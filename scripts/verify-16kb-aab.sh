#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
set -euo pipefail

readonly MIN_PAGE_ALIGNMENT=$((16 * 1024))
readonly MIN_PAGE_ALIGNMENT_HEX="0x4000"

problem_count=0
native_library_count=0
load_segment_count=0
bundletool_status="skipped (bundletool not available)"
zipalign_status="not checked"
temporary_directory=""

declare -a apk_paths=()
aab_path=""

usage() {
    cat <<'EOF'
Usage:
  scripts/verify-16kb-aab.sh --aab PATH --apk PATH [--apk PATH ...]

Checks a release Android App Bundle and one or more APKs for 16 KB page-size
compatibility:
  * every ELF LOAD segment in every bundled .so has p_align >= 0x4000;
  * bundletool reports PAGE_ALIGNMENT_16K, when bundletool is available;
  * every APK passes zipalign -c -P 16 -v 4.

Tool overrides:
  LLVM_READELF  Path or command name for llvm-readelf/readelf.
  LLVM_OBJDUMP  Path or command name for llvm-objdump/objdump.
  BUNDLETOOL    Path or command name for bundletool (a .jar is also accepted).
  BUNDLETOOL_JAR
                Path to the executable bundletool-all JAR.
  ZIPALIGN      Path or command name for zipalign.

bundletool is optional. ELF inspection and zipalign are mandatory.
EOF
}

log() {
    printf '%s\n' "$*"
}

warn() {
    printf 'WARNING: %s\n' "$*" >&2
}

add_problem() {
    problem_count=$((problem_count + 1))
    printf 'ERROR: %s\n' "$*" >&2
}

resolve_command() {
    local candidate="$1"

    if [[ "${candidate}" == */* ]]; then
        [[ -x "${candidate}" ]] || return 1
        printf '%s\n' "${candidate}"
        return 0
    fi

    command -v "${candidate}" 2>/dev/null
}

absolute_file_path() {
    local input_path="$1"
    local input_directory
    local input_name

    input_directory="$(cd -- "$(dirname -- "${input_path}")" && pwd -P)" || return 1
    input_name="$(basename -- "${input_path}")"
    printf '%s/%s\n' "${input_directory}" "${input_name}"
}

cleanup() {
    if [[ -n "${temporary_directory}" && -d "${temporary_directory}" ]]; then
        case "${temporary_directory}" in
            */verify-16kb-aab.*)
                rm -rf -- "${temporary_directory}"
                ;;
            *)
                warn "Refusing to remove unexpected temporary path: ${temporary_directory}"
                ;;
        esac
    fi
}

trap cleanup EXIT

while [[ $# -gt 0 ]]; do
    case "$1" in
        --aab)
            [[ $# -ge 2 ]] || { printf 'Missing value for --aab\n' >&2; usage >&2; exit 2; }
            aab_path="$2"
            shift 2
            ;;
        --apk)
            [[ $# -ge 2 ]] || { printf 'Missing value for --apk\n' >&2; usage >&2; exit 2; }
            apk_paths+=("$2")
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            printf 'Unknown argument: %s\n' "$1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ -z "${aab_path}" ]]; then
    printf 'An AAB is required. Pass it with --aab PATH.\n' >&2
    usage >&2
    exit 2
fi

if [[ ${#apk_paths[@]} -eq 0 ]]; then
    printf 'At least one APK is required. Pass it with --apk PATH.\n' >&2
    usage >&2
    exit 2
fi

if [[ ! -f "${aab_path}" ]]; then
    printf 'AAB does not exist: %s\n' "${aab_path}" >&2
    exit 2
fi
aab_path="$(absolute_file_path "${aab_path}")"

for apk_index in "${!apk_paths[@]}"; do
    if [[ ! -f "${apk_paths[$apk_index]}" ]]; then
        printf 'APK does not exist: %s\n' "${apk_paths[$apk_index]}" >&2
        exit 2
    fi
    apk_paths[$apk_index]="$(absolute_file_path "${apk_paths[$apk_index]}")"
done

if ! command -v unzip >/dev/null 2>&1; then
    printf 'unzip is required to inspect the AAB.\n' >&2
    exit 2
fi

find_elf_tool() {
    local candidate=""
    local ndk_root=""
    local sdk_root=""

    if [[ -n "${LLVM_READELF:-}" ]]; then
        if candidate="$(resolve_command "${LLVM_READELF}")"; then
            printf 'readelf\t%s\n' "${candidate}"
            return 0
        fi
        printf 'Configured LLVM_READELF is not executable: %s\n' "${LLVM_READELF}" >&2
        return 1
    fi

    for candidate in llvm-readelf readelf; do
        if candidate="$(resolve_command "${candidate}")"; then
            printf 'readelf\t%s\n' "${candidate}"
            return 0
        fi
    done

    for ndk_root in "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}"; do
        [[ -d "${ndk_root}/toolchains/llvm/prebuilt" ]] || continue
        candidate="$(find "${ndk_root}/toolchains/llvm/prebuilt" -type f -path '*/bin/llvm-readelf' 2>/dev/null | sort | tail -n 1)"
        if [[ -n "${candidate}" && -x "${candidate}" ]]; then
            printf 'readelf\t%s\n' "${candidate}"
            return 0
        fi
    done

    for sdk_root in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}"; do
        [[ -d "${sdk_root}/ndk" ]] || continue
        candidate="$(find "${sdk_root}/ndk" -type f -path '*/toolchains/llvm/prebuilt/*/bin/llvm-readelf' 2>/dev/null | sort | tail -n 1)"
        if [[ -n "${candidate}" && -x "${candidate}" ]]; then
            printf 'readelf\t%s\n' "${candidate}"
            return 0
        fi
    done

    if [[ -n "${LLVM_OBJDUMP:-}" ]]; then
        if candidate="$(resolve_command "${LLVM_OBJDUMP}")"; then
            printf 'objdump\t%s\n' "${candidate}"
            return 0
        fi
        printf 'Configured LLVM_OBJDUMP is not executable: %s\n' "${LLVM_OBJDUMP}" >&2
        return 1
    fi

    for candidate in llvm-objdump objdump; do
        if candidate="$(resolve_command "${candidate}")"; then
            printf 'objdump\t%s\n' "${candidate}"
            return 0
        fi
    done

    return 1
}

parse_alignment() {
    local raw_alignment="$1"
    local exponent

    if [[ "${raw_alignment}" =~ ^0[xX][0-9a-fA-F]+$ ]]; then
        printf '%d\n' "$((raw_alignment))"
        return 0
    fi

    if [[ "${raw_alignment}" =~ ^[0-9]+$ ]]; then
        printf '%d\n' "${raw_alignment}"
        return 0
    fi

    if [[ "${raw_alignment}" =~ ^2\*\*([0-9]+)$ ]]; then
        exponent="${BASH_REMATCH[1]}"
        (( exponent < 63 )) || return 1
        printf '%d\n' "$((1 << exponent))"
        return 0
    fi

    return 1
}

is_safe_zip_entry() {
    local entry="$1"

    [[ -n "${entry}" ]] || return 1
    [[ "${entry}" != /* ]] || return 1
    [[ "${entry}" != ../* ]] || return 1
    [[ "${entry}" != */../* ]] || return 1
    [[ "${entry}" != */.. ]] || return 1
}

check_elf_library() {
    local archive_entry="$1"
    local extracted_file="$2"
    local tool_kind="$3"
    local tool_path="$4"
    local headers=""
    local alignments=""
    local raw_alignment=""
    local alignment_value=""
    local library_load_count=0

    if [[ "${tool_kind}" == "readelf" ]]; then
        if ! headers="$("${tool_path}" -lW "${extracted_file}" 2>&1)"; then
            add_problem "${archive_entry}: unable to read ELF program headers with ${tool_path}"
            printf '%s\n' "${headers}" | sed 's/^/       /' >&2
            return
        fi
        alignments="$(printf '%s\n' "${headers}" | awk '$1 == "LOAD" { print $NF }')"
    else
        if ! headers="$("${tool_path}" -p "${extracted_file}" 2>&1)"; then
            add_problem "${archive_entry}: unable to read ELF program headers with ${tool_path}"
            printf '%s\n' "${headers}" | sed 's/^/       /' >&2
            return
        fi
        alignments="$(printf '%s\n' "${headers}" | awk '$1 == "LOAD" { for (i = 1; i <= NF; i++) if ($i == "align") print $(i + 1) }')"
    fi

    while IFS= read -r raw_alignment; do
        [[ -n "${raw_alignment}" ]] || continue
        library_load_count=$((library_load_count + 1))
        load_segment_count=$((load_segment_count + 1))

        if ! alignment_value="$(parse_alignment "${raw_alignment}")"; then
            add_problem "${archive_entry}: LOAD[${library_load_count}] has an unrecognized alignment '${raw_alignment}'"
            continue
        fi

        if (( alignment_value < MIN_PAGE_ALIGNMENT )); then
            add_problem "${archive_entry}: LOAD[${library_load_count}] alignment ${raw_alignment} is below ${MIN_PAGE_ALIGNMENT_HEX}"
        fi
    done <<< "${alignments}"

    if (( library_load_count == 0 )); then
        add_problem "${archive_entry}: no ELF LOAD segments were found"
    fi
}

log "Inspecting AAB: ${aab_path}"

elf_tool="$(find_elf_tool || true)"
if [[ -z "${elf_tool}" ]]; then
    printf 'No ELF inspection tool found. Install an Android NDK (llvm-readelf), binutils readelf, or objdump.\n' >&2
    exit 2
fi
elf_tool_kind="${elf_tool%%$'\t'*}"
elf_tool_path="${elf_tool#*$'\t'}"
log "ELF tool: ${elf_tool_path}"

temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/verify-16kb-aab.XXXXXX")"
readonly entries_file="${temporary_directory}/entries.txt"
readonly libraries_file="${temporary_directory}/libraries.txt"

if ! unzip -Z1 "${aab_path}" > "${entries_file}"; then
    printf 'Unable to list AAB contents: %s\n' "${aab_path}" >&2
    exit 2
fi
awk '/\.so$/ { print }' "${entries_file}" > "${libraries_file}"

while IFS= read -r archive_entry; do
    [[ -n "${archive_entry}" ]] || continue
    native_library_count=$((native_library_count + 1))

    if ! is_safe_zip_entry "${archive_entry}"; then
        add_problem "Unsafe native-library path in AAB: ${archive_entry}"
        continue
    fi

    extracted_file="${temporary_directory}/aab/${archive_entry}"
    mkdir -p -- "$(dirname -- "${extracted_file}")"
    if ! unzip -p "${aab_path}" "${archive_entry}" > "${extracted_file}"; then
        add_problem "Unable to extract native library: ${archive_entry}"
        continue
    fi

    check_elf_library "${archive_entry}" "${extracted_file}" "${elf_tool_kind}" "${elf_tool_path}"
done < "${libraries_file}"

if (( native_library_count == 0 )); then
    log "No native libraries found in the AAB."
else
    log "Checked ${load_segment_count} LOAD segments in ${native_library_count} native libraries."
fi

bundletool_kind=""
bundletool_path=""

if [[ -n "${BUNDLETOOL_JAR:-}" ]]; then
    if [[ -f "${BUNDLETOOL_JAR}" ]] && command -v java >/dev/null 2>&1; then
        bundletool_kind="jar"
        bundletool_path="${BUNDLETOOL_JAR}"
    else
        add_problem "BUNDLETOOL_JAR is not a readable JAR or java is unavailable: ${BUNDLETOOL_JAR}"
        bundletool_status="failed"
    fi
elif [[ -n "${BUNDLETOOL:-}" ]]; then
    if [[ "${BUNDLETOOL}" == *.jar ]]; then
        if [[ -f "${BUNDLETOOL}" ]] && command -v java >/dev/null 2>&1; then
            bundletool_kind="jar"
            bundletool_path="${BUNDLETOOL}"
        else
            add_problem "BUNDLETOOL points to an unreadable JAR or java is unavailable: ${BUNDLETOOL}"
            bundletool_status="failed"
        fi
    elif bundletool_path="$(resolve_command "${BUNDLETOOL}" || true)" && [[ -n "${bundletool_path}" ]]; then
        bundletool_kind="command"
    else
        add_problem "Configured BUNDLETOOL is not executable: ${BUNDLETOOL}"
        bundletool_status="failed"
    fi
elif bundletool_path="$(command -v bundletool 2>/dev/null || true)" && [[ -n "${bundletool_path}" ]]; then
    bundletool_kind="command"
fi

if [[ -n "${bundletool_kind}" ]]; then
    bundle_config=""
    if [[ "${bundletool_kind}" == "jar" ]]; then
        bundletool_label="java -jar ${bundletool_path}"
        if ! bundle_config="$(java -jar "${bundletool_path}" dump config --bundle="${aab_path}" 2>&1)"; then
            add_problem "bundletool could not dump the AAB configuration (${bundletool_label})"
            printf '%s\n' "${bundle_config}" | sed 's/^/       /' >&2
            bundletool_status="failed"
        fi
    else
        bundletool_label="${bundletool_path}"
        if ! bundle_config="$("${bundletool_path}" dump config --bundle="${aab_path}" 2>&1)"; then
            add_problem "bundletool could not dump the AAB configuration (${bundletool_label})"
            printf '%s\n' "${bundle_config}" | sed 's/^/       /' >&2
            bundletool_status="failed"
        fi
    fi

    if [[ "${bundletool_status}" != "failed" ]]; then
        if printf '%s\n' "${bundle_config}" | grep -Eq '"?alignment"?[[:space:]]*:[[:space:]]*"?PAGE_ALIGNMENT_16K"?'; then
            bundletool_status="PAGE_ALIGNMENT_16K"
        else
            detected_alignment="$(printf '%s\n' "${bundle_config}" | grep -Eo 'PAGE_ALIGNMENT_[A-Z0-9_]+' | sort -u | paste -sd, - || true)"
            if [[ -z "${detected_alignment}" ]]; then
                detected_alignment="no PAGE_ALIGNMENT value"
            fi
            add_problem "AAB bundle config is not PAGE_ALIGNMENT_16K (${detected_alignment})"
            bundletool_status="failed"
        fi
    fi
elif [[ "${bundletool_status}" != "failed" ]]; then
    warn "bundletool is unavailable; skipping BundleConfig PAGE_ALIGNMENT_16K check."
fi

find_zipalign() {
    local candidate=""
    local sdk_root=""

    if [[ -n "${ZIPALIGN:-}" ]]; then
        resolve_command "${ZIPALIGN}"
        return
    fi

    if candidate="$(command -v zipalign 2>/dev/null || true)" && [[ -n "${candidate}" ]]; then
        printf '%s\n' "${candidate}"
        return 0
    fi

    for sdk_root in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}"; do
        [[ -d "${sdk_root}/build-tools" ]] || continue
        candidate="$(find "${sdk_root}/build-tools" -mindepth 2 -maxdepth 2 -type f -name zipalign 2>/dev/null | sort | tail -n 1)"
        if [[ -n "${candidate}" && -x "${candidate}" ]]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    done

    return 1
}

zipalign_path="$(find_zipalign || true)"
if [[ -z "${zipalign_path}" ]]; then
    add_problem "zipalign was not found; install Android SDK Build Tools or set ZIPALIGN"
    zipalign_status="failed"
else
    zipalign_status="passed"
    log "zipalign tool: ${zipalign_path}"
    for apk_path in "${apk_paths[@]}"; do
        zipalign_output=""
        if zipalign_output="$("${zipalign_path}" -c -P 16 -v 4 "${apk_path}" 2>&1)"; then
            log "APK passes zipalign -P 16: ${apk_path}"
        else
            add_problem "APK fails zipalign -P 16: ${apk_path}"
            printf '%s\n' "${zipalign_output}" | sed 's/^/       /' >&2
            zipalign_status="failed"
        fi
    done
fi

log "BundleConfig check: ${bundletool_status}"
log "APK zip alignment: ${zipalign_status}"

if (( problem_count > 0 )); then
    printf '16 KB verification FAILED with %d problem(s).\n' "${problem_count}" >&2
    exit 1
fi

log "16 KB verification PASSED."

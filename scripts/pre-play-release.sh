#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"

skip_device=false
self_check_only=false
requested_device_4k=""
requested_device_16k=""
report_root="${PROJECT_ROOT}/app/build/reports/pre-play"
run_started_at="$(date '+%Y-%m-%dT%H:%M:%S%z')"
run_id="$(date '+%Y%m%d-%H%M%S')-$$"
report_dir=""
summary_file=""
current_step="preflight"
completed_steps=0
device_4k_serial=""
device_4k_abi=""
device_4k_abi_list=""
device_4k_page_size=""
device_4k_model=""
device_4k_api=""
device_16k_serial=""
device_16k_abi=""
device_16k_abi_list=""
device_16k_page_size=""
device_16k_model=""
device_16k_api=""
candidate_commit=""
candidate_branch=""
candidate_version_name=""
candidate_version_code=""
candidate_tree_status=""
apk_sha256=""
aab_sha256=""
apk_size=""
aab_size=""
apk_signer_sha256=""
aab_signer_sha256=""
apk_path="${PROJECT_ROOT}/app/build/outputs/apk/release/app-release.apk"
aab_path="${PROJECT_ROOT}/app/build/outputs/bundle/release/app-release.aab"
apksigner_path=""
sha256_command=""
bundletool_jar=""
readonly BUNDLETOOL_VERSION="1.18.3"
readonly BUNDLETOOL_SHA256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
readonly UPLOAD_CERT_SHA256="228275ccb1841cd921ab45a514deda11323f81040d7abc2af7b9f9288d3fc0bb"
readonly APPLICATION_ID="tech.g24.feresaslicer"
readonly TEST_APPLICATION_ID="tech.g24.feresaslicer.test"
readonly SMOKE_SETTLE_SECONDS=10

usage() {
    cat <<'EOF'
Usage:
  scripts/pre-play-release.sh [--device SERIAL] [--device-16k SERIAL]
  scripts/pre-play-release.sh --skip-device
  scripts/pre-play-release.sh --self-check \
    [--device SERIAL] [--device-16k SERIAL]
  scripts/pre-play-release.sh --self-check --skip-device

Runs the mandatory local gate before uploading Feresa Slicer to Google Play:
  1. installs locked JavaScript dependencies, tests and rebuilds the viewer;
  2. runs JVM tests and release lint;
  3. assembles the signed, minified release APK and AAB;
  4. verifies the APK signature and APK/AAB 16 KB page compatibility;
  5. runs release instrumentation tests on distinct 4 KiB and 16 KiB ARM64
     Android targets;
  6. installs and cold-starts the exact signed release APK on both targets,
     requires a live PID, and scans logcat for fatal/ANR/OOM/native failures.

Options:
  --device SERIAL   Use this online ARM64 adb target for the 4 KiB role. If
                    omitted, select an unambiguous target by PAGE_SIZE=4096.
  --device-16k SERIAL
                    Use this online ARM64 adb target for the 16 KiB role. If
                    omitted, select an unambiguous target by PAGE_SIZE=16384.
  --skip-device     Explicitly skip both instrumentation and signed-APK smoke.
                    This always produces an INCOMPLETE result.
  --self-check      Check tools, signing configuration and device availability,
                    then exit without building.
  --report-dir DIR  Write logs below DIR instead of app/build/reports/pre-play.
  -h, --help        Show this help.

Release signing must be configured through an untracked keystore.properties or
the FERESA_UPLOAD_* environment variables. This script never prints their values.

Examples:
  scripts/pre-play-release.sh --device emulator-5554 --device-16k emulator-5556
  scripts/pre-play-release.sh
  scripts/pre-play-release.sh --self-check --skip-device
EOF
}

log() {
    printf '[pre-play] %s\n' "$*"
}

die() {
    printf '[pre-play] ERROR: %s\n' "$*" >&2
    exit 1
}

absolute_directory_path() {
    local input_path="$1"
    mkdir -p -- "${input_path}"
    (cd -- "${input_path}" && pwd -P)
}

require_command() {
    local command_name="$1"
    command -v "${command_name}" >/dev/null 2>&1 || die "Required command is missing: ${command_name}"
}

find_sdk_tool() {
    local tool_name="$1"
    local candidate=""

    if command -v "${tool_name}" >/dev/null 2>&1; then
        command -v "${tool_name}"
        return 0
    fi

    if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}/build-tools" ]]; then
        candidate="$(find "${ANDROID_SDK_ROOT}/build-tools" -mindepth 2 -maxdepth 2 \
            -type f -name "${tool_name}" 2>/dev/null | sort | tail -n 1)"
        if [[ -n "${candidate}" && -x "${candidate}" ]]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    fi

    return 1
}

sha256_file() {
    local input_path="$1"

    if [[ "${sha256_command}" == "shasum" ]]; then
        shasum -a 256 "${input_path}" | awk '{ print $1 }'
    else
        sha256sum "${input_path}" | awk '{ print $1 }'
    fi
}

file_size() {
    local input_path="$1"

    if stat -f '%z' "${input_path}" >/dev/null 2>&1; then
        stat -f '%z' "${input_path}"
    else
        stat -c '%s' "${input_path}"
    fi
}

ensure_bundletool() {
    local configured_jar="${BUNDLETOOL_JAR:-}"
    local downloaded_jar="${report_dir}/bundletool-all-${BUNDLETOOL_VERSION}.jar"

    if [[ -n "${configured_jar}" ]]; then
        [[ -r "${configured_jar}" ]] || die "BUNDLETOOL_JAR is not readable: ${configured_jar}"
        [[ "$(sha256_file "${configured_jar}")" == "${BUNDLETOOL_SHA256}" ]] || \
            die "External BUNDLETOOL_JAR checksum does not match pinned bundletool ${BUNDLETOOL_VERSION}."
        bundletool_jar="${configured_jar}"
        return
    fi

    log "Downloading checksum-pinned bundletool ${BUNDLETOOL_VERSION}"
    curl --fail --location --retry 3 --proto '=https' --tlsv1.2 \
        --output "${downloaded_jar}" \
        "https://github.com/google/bundletool/releases/download/${BUNDLETOOL_VERSION}/bundletool-all-${BUNDLETOOL_VERSION}.jar"
    [[ "$(sha256_file "${downloaded_jar}")" == "${BUNDLETOOL_SHA256}" ]] || \
        die "Downloaded bundletool checksum does not match the pinned value."
    bundletool_jar="${downloaded_jar}"
}

gradle_literal_value() {
    local property_name="$1"
    local build_file="${PROJECT_ROOT}/app/build.gradle.kts"

    case "${property_name}" in
        versionName)
            sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' \
                "${build_file}" | head -n 1
            ;;
        versionCode)
            sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' \
                "${build_file}" | head -n 1
            ;;
        *)
            return 2
            ;;
    esac
}

capture_candidate_identity() {
    local status_path="${report_dir}/00-git-status.txt"

    git rev-parse --is-inside-work-tree >/dev/null 2>&1 || \
        die "Project root is not a Git worktree: ${PROJECT_ROOT}"
    candidate_commit="$(git rev-parse HEAD)"
    candidate_branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null || printf 'DETACHED')"
    candidate_version_name="$(gradle_literal_value versionName)"
    candidate_version_code="$(gradle_literal_value versionCode)"
    [[ -n "${candidate_version_name}" ]] || die "Unable to read literal versionName from app/build.gradle.kts."
    [[ -n "${candidate_version_code}" ]] || die "Unable to read literal versionCode from app/build.gradle.kts."

    candidate_tree_status="$(git status --porcelain=v1 --untracked-files=all)"
    printf '%s\n' "${candidate_tree_status}" > "${status_path}"

    append_summary "INFO | Git commit | ${candidate_commit}"
    append_summary "INFO | Git branch | ${candidate_branch}"
    append_summary "INFO | Version | ${candidate_version_name} (${candidate_version_code})"
    if [[ -z "${candidate_tree_status}" ]]; then
        append_summary "PASS | Clean Git tree | 00-git-status.txt"
    elif [[ "${self_check_only}" == true ]]; then
        append_summary "WARN | Git tree is not clean | self-check remains INCOMPLETE | 00-git-status.txt"
    else
        die "Git tree is not clean. A complete artifact gate requires an exact clean commit; see ${status_path}."
    fi
}

verify_candidate_identity_unchanged() {
    [[ "$(git rev-parse HEAD)" == "${candidate_commit}" ]] || \
        die "Git HEAD changed during the release gate."
    [[ -z "$(git status --porcelain=v1 --untracked-files=all)" ]] || \
        die "Git tree became dirty during the release gate."
}

run_host_native_tests() {
    local native_build_dir="${report_dir}/native-build"
    cmake -S "${PROJECT_ROOT}/app/src/main/cpp" -B "${native_build_dir}" \
        -DFERESA_SLICER_BUILD_TESTS=ON
    cmake --build "${native_build_dir}" --parallel
    ctest --test-dir "${native_build_dir}" --output-on-failure
}

verify_viewer_bundle_is_committed() {
    git diff --exit-code -- app/src/main/assets/viewer/viewer.bundle.js
}

verify_aab_signature_and_print_cert() {
    local cert_details_path="${report_dir}/08-aab-signer.txt"
    local cert_pem_path="${report_dir}/08-aab-signer.pem"
    local truststore_path="${report_dir}/08-upload-cert-truststore.p12"
    local truststore_password="feresa-public-cert"
    local signer_digest=""

    keytool -printcert -jarfile "${aab_path}" | tee "${cert_details_path}"
    signer_digest="$(sed -nE 's/^[[:space:]]*SHA256:[[:space:]]*(.*)$/\1/p' \
        "${cert_details_path}" | head -n 1)"
    [[ -n "${signer_digest}" ]] || die "AAB signer SHA-256 digest is missing."
    [[ "$(normalized_certificate_digest "${signer_digest}")" == "${UPLOAD_CERT_SHA256}" ]] || \
        die "AAB signer does not match the pinned permanent upload certificate."

    # Trust only the already-pinned public upload certificate. This lets strict
    # verification reject unsigned entries and all other severe JAR warnings
    # without rejecting the expected self-signed Android upload certificate.
    keytool -printcert -rfc -jarfile "${aab_path}" > "${cert_pem_path}"
    keytool -importcert -noprompt -alias feresa-upload \
        -file "${cert_pem_path}" \
        -keystore "${truststore_path}" -storetype PKCS12 \
        -storepass "${truststore_password}"
    jarsigner -verify -strict \
        -keystore "${truststore_path}" -storetype PKCS12 \
        -storepass "${truststore_password}" "${aab_path}"
}

normalized_certificate_digest() {
    printf '%s' "$1" | tr -d ':' | tr '[:upper:]' '[:lower:]'
}

property_is_configured() {
    local property_name="$1"
    [[ -n "$(property_value "${property_name}")" ]]
}

property_value() {
    local property_name="$1"
    local properties_file="${PROJECT_ROOT}/keystore.properties"

    [[ -f "${properties_file}" ]] || return 1
    awk -F= -v expected="${property_name}" '
        /^[[:space:]]*#/ { next }
        {
            separator = index($0, "=")
            if (separator == 0) next
            key = $1
            value = substr($0, separator + 1)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", key)
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
            if (key == expected && length(value) > 0) {
                print value
                found = 1
                exit
            }
        }
        END { exit(found ? 0 : 1) }
    ' "${properties_file}"
}

release_signing_is_configured() {
    if [[ -n "${FERESA_UPLOAD_STORE_FILE:-}" && \
          -n "${FERESA_UPLOAD_STORE_PASSWORD:-}" && \
          -n "${FERESA_UPLOAD_KEY_ALIAS:-}" && \
          -n "${FERESA_UPLOAD_KEY_PASSWORD:-}" ]]; then
        return 0
    fi

    property_is_configured storeFile && \
        property_is_configured storePassword && \
        property_is_configured keyAlias && \
        property_is_configured keyPassword
}

release_keystore_is_readable() {
    local configured_path=""

    if [[ -n "${FERESA_UPLOAD_STORE_FILE:-}" ]]; then
        configured_path="${FERESA_UPLOAD_STORE_FILE}"
    else
        configured_path="$(property_value storeFile || true)"
    fi

    [[ -n "${configured_path}" ]] || return 1
    if [[ "${configured_path}" == /* ]]; then
        [[ -f "${configured_path}" && -r "${configured_path}" ]]
    else
        [[ -f "${PROJECT_ROOT}/${configured_path}" && -r "${PROJECT_ROOT}/${configured_path}" ]]
    fi
}

resolve_adb() {
    local candidate=""

    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return 0
    fi

    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        candidate="${ANDROID_SDK_ROOT}/platform-tools/adb"
        if [[ -x "${candidate}" ]]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    fi

    return 1
}

device_is_online() {
    local adb_path="$1"
    local serial="$2"
    [[ "$("${adb_path}" -s "${serial}" get-state 2>/dev/null || true)" == "device" ]]
}

device_primary_abi() {
    local adb_path="$1"
    local serial="$2"

    "${adb_path}" -s "${serial}" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r\n'
}

device_abi_list() {
    local adb_path="$1"
    local serial="$2"
    local abi_list=""

    abi_list="$("${adb_path}" -s "${serial}" shell getprop ro.product.cpu.abilist 2>/dev/null \
        | tr -d '\r\n' || true)"
    if [[ -z "${abi_list}" ]]; then
        abi_list="$(device_primary_abi "${adb_path}" "${serial}")"
    fi
    printf '%s\n' "${abi_list}"
}

device_page_size() {
    local adb_path="$1"
    local serial="$2"

    "${adb_path}" -s "${serial}" shell getconf PAGE_SIZE 2>/dev/null \
        | tr -cd '0-9'
}

device_is_arm64_primary() {
    local primary_abi=""

    primary_abi="$(device_primary_abi "$1" "$2")"
    [[ "${primary_abi}" == "arm64-v8a" ]]
}

select_device_for_page_size() {
    local adb_path="$1"
    local requested_serial="$2"
    local expected_page_size="$3"
    local role_label="$4"
    local serial=""
    local actual_page_size=""
    local selected=""
    local selected_count=0

    if [[ -n "${requested_serial}" ]]; then
        device_is_online "${adb_path}" "${requested_serial}" || \
            die "Requested ${role_label} adb target is not online: ${requested_serial}"
        device_is_arm64_primary "${adb_path}" "${requested_serial}" || \
            die "Requested ${role_label} target does not have a primary ARM64 ABI: ${requested_serial}"
        actual_page_size="$(device_page_size "${adb_path}" "${requested_serial}")"
        [[ "${actual_page_size}" == "${expected_page_size}" ]] || \
            die "Requested ${role_label} target ${requested_serial} reports PAGE_SIZE=${actual_page_size:-unknown}; expected ${expected_page_size}."
        printf '%s\n' "${requested_serial}"
        return 0
    fi

    while IFS= read -r serial; do
        [[ -n "${serial}" ]] || continue
        if device_is_arm64_primary "${adb_path}" "${serial}" && \
           [[ "$(device_page_size "${adb_path}" "${serial}")" == "${expected_page_size}" ]]; then
            selected="${serial}"
            selected_count=$((selected_count + 1))
        fi
    done <<EOF
$("${adb_path}" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
EOF

    if (( selected_count == 0 )); then
        die "No online primary-ARM64 ${role_label} target with PAGE_SIZE=${expected_page_size} found. Connect one, pass its serial explicitly, or use --skip-device for an INCOMPLETE run."
    fi
    if (( selected_count > 1 )); then
        die "More than one ${role_label} ARM64 target reports PAGE_SIZE=${expected_page_size}. Select one explicitly."
    fi

    printf '%s\n' "${selected}"
}

capture_device_facts() {
    local adb_path="$1"
    local serial="$2"
    local expected_page_size="$3"
    local role="$4"
    local role_label="$5"
    local primary_abi=""
    local abi_list=""
    local page_size=""
    local model=""
    local api=""
    local evidence_path="${report_dir}/00-device-${role}.txt"

    device_is_online "${adb_path}" "${serial}" || die "${role_label} target went offline: ${serial}"
    primary_abi="$(device_primary_abi "${adb_path}" "${serial}")"
    abi_list="$(device_abi_list "${adb_path}" "${serial}")"
    page_size="$(device_page_size "${adb_path}" "${serial}")"
    model="$("${adb_path}" -s "${serial}" shell getprop ro.product.model 2>/dev/null | tr -d '\r\n')"
    api="$("${adb_path}" -s "${serial}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r\n')"

    [[ "${primary_abi}" == "arm64-v8a" ]] || \
        die "${role_label} target is not primary ARM64 (${primary_abi:-unknown}): ${serial}"
    [[ "${page_size}" == "${expected_page_size}" ]] || \
        die "${role_label} target ${serial} reports PAGE_SIZE=${page_size:-unknown}; expected ${expected_page_size}."

    {
        printf 'Role: %s\n' "${role_label}"
        printf 'Serial: %s\n' "${serial}"
        printf 'Model: %s\n' "${model:-unknown}"
        printf 'API: %s\n' "${api:-unknown}"
        printf 'Primary ABI: %s\n' "${primary_abi}"
        printf 'ABI list: %s\n' "${abi_list}"
        printf 'PAGE_SIZE: %s\n' "${page_size}"
    } > "${evidence_path}"

    case "${role}" in
        4k)
            device_4k_serial="${serial}"
            device_4k_abi="${primary_abi}"
            device_4k_abi_list="${abi_list}"
            device_4k_page_size="${page_size}"
            device_4k_model="${model}"
            device_4k_api="${api}"
            ;;
        16k)
            device_16k_serial="${serial}"
            device_16k_abi="${primary_abi}"
            device_16k_abi_list="${abi_list}"
            device_16k_page_size="${page_size}"
            device_16k_model="${model}"
            device_16k_api="${api}"
            ;;
        *)
            die "Internal error: unknown device role ${role}."
            ;;
    esac

    append_summary "PASS | ${role_label} target | serial=${serial} | ABI=${primary_abi} | ABI list=${abi_list} | PAGE_SIZE=${page_size} | API=${api:-unknown} | 00-device-${role}.txt"
}

select_and_record_devices() {
    local adb_path="$1"
    local selected_4k=""
    local selected_16k=""

    selected_4k="$(select_device_for_page_size "${adb_path}" "${requested_device_4k}" 4096 '4 KiB')"
    selected_16k="$(select_device_for_page_size "${adb_path}" "${requested_device_16k}" 16384 '16 KiB')"
    [[ "${selected_4k}" != "${selected_16k}" ]] || \
        die "The 4 KiB and 16 KiB roles must use two distinct adb serials."

    capture_device_facts "${adb_path}" "${selected_4k}" 4096 4k 'ARM64 4 KiB'
    capture_device_facts "${adb_path}" "${selected_16k}" 16384 16k 'ARM64 16 KiB'
}

assert_device_role_now() {
    local adb_path="$1"
    local serial="$2"
    local expected_page_size="$3"
    local role_label="$4"
    local primary_abi=""
    local page_size=""

    device_is_online "${adb_path}" "${serial}" || \
        die "${role_label} target is not online: ${serial}"
    primary_abi="$(device_primary_abi "${adb_path}" "${serial}")"
    page_size="$(device_page_size "${adb_path}" "${serial}")"
    [[ "${primary_abi}" == "arm64-v8a" ]] || \
        die "${role_label} target ${serial} now reports primary ABI=${primary_abi:-unknown}; expected arm64-v8a."
    [[ "${page_size}" == "${expected_page_size}" ]] || \
        die "${role_label} target ${serial} now reports PAGE_SIZE=${page_size:-unknown}; expected ${expected_page_size}."
    printf 'Validated device role: %s serial=%s ABI=%s PAGE_SIZE=%s\n' \
        "${role_label}" "${serial}" "${primary_abi}" "${page_size}"
}

run_release_instrumentation() {
    local adb_path="$1"
    local serial="$2"
    local expected_page_size="$3"
    local role="$4"
    local role_label="$5"
    local marker_path="${report_dir}/${role}-instrumentation-started.marker"
    local results_root="${PROJECT_ROOT}/app/build/outputs/androidTest-results"
    local snapshot_dir="${report_dir}/${role}-instrumentation-results"
    local result_path=""
    local result_name=""
    local result_count=0

    assert_device_role_now "${adb_path}" "${serial}" "${expected_page_size}" "${role_label}"
    mkdir -p -- "${snapshot_dir}"
    touch "${marker_path}"

    env ANDROID_SERIAL="${serial}" \
        ./gradlew --no-watch-fs -Pferesa.testBuildType=releaseTest \
            :app:connectedReleaseTestAndroidTest

    [[ -d "${results_root}" ]] || \
        die "Instrumentation passed without an Android test-results directory: ${results_root}"
    while IFS= read -r -d '' result_path; do
        result_count=$((result_count + 1))
        result_name="$(printf '%03d-%s' "${result_count}" "$(basename -- "${result_path}")")"
        cp "${result_path}" "${snapshot_dir}/${result_name}"
    done < <(find "${results_root}" -type f -name '*.xml' -newer "${marker_path}" -print0)

    (( result_count > 0 )) || \
        die "${role_label} instrumentation produced no fresh XML results; refusing stale or skipped evidence."
    grep -REq 'tests="[1-9][0-9]*"' "${snapshot_dir}" || \
        die "${role_label} instrumentation XML reports no executed tests."
    if grep -REq '(failures|errors|skipped)="[1-9][0-9]*"' "${snapshot_dir}"; then
        die "${role_label} instrumentation XML contains failed, errored, or skipped tests."
    fi
    grep -RqF 'OrcaNativeParityInstrumentedTest' "${snapshot_dir}" || \
        die "${role_label} instrumentation evidence is missing OrcaNativeParityInstrumentedTest."
    grep -RqF 'PrePlaySlicingInstrumentedTest' "${snapshot_dir}" || \
        die "${role_label} instrumentation evidence is missing PrePlaySlicingInstrumentedTest."
    grep -RqF 'PrePlayModelScreenE2ETest' "${snapshot_dir}" || \
        die "${role_label} instrumentation evidence is missing PrePlayModelScreenE2ETest."
    assert_device_role_now "${adb_path}" "${serial}" "${expected_page_size}" "${role_label}"

    append_summary "PASS | ${role_label} instrumentation XML | ${result_count} fresh result file(s) | required suites present | ${role}-instrumentation-results/"
}

record_aab_manifest_identity() {
    local aab_version_name=""
    local aab_version_code=""

    aab_version_name="$(java -jar "${bundletool_jar}" dump manifest \
        --bundle="${aab_path}" --xpath='/manifest/@android:versionName' | tr -d '\r\n')"
    aab_version_code="$(java -jar "${bundletool_jar}" dump manifest \
        --bundle="${aab_path}" --xpath='/manifest/@android:versionCode' | tr -d '\r\n')"
    [[ "${aab_version_name}" == "${candidate_version_name}" ]] || \
        die "AAB versionName ${aab_version_name:-unknown} does not match source ${candidate_version_name}."
    [[ "${aab_version_code}" == "${candidate_version_code}" ]] || \
        die "AAB versionCode ${aab_version_code:-unknown} does not match source ${candidate_version_code}."
    append_summary "PASS | AAB manifest version | ${aab_version_name} (${aab_version_code})"
}

verify_artifact_hashes_unchanged() {
    local final_apk_sha256=""
    local final_aab_sha256=""

    [[ -s "${apk_path}" && -s "${aab_path}" ]] || \
        die "Release artifact disappeared before final identity check."
    final_apk_sha256="$(sha256_file "${apk_path}")"
    final_aab_sha256="$(sha256_file "${aab_path}")"
    [[ "${final_apk_sha256}" == "${apk_sha256}" ]] || \
        die "Exact signed APK changed after validation (${apk_sha256} -> ${final_apk_sha256})."
    [[ "${final_aab_sha256}" == "${aab_sha256}" ]] || \
        die "Exact signed AAB changed after validation (${aab_sha256} -> ${final_aab_sha256})."
    printf 'Final artifact hashes unchanged: APK=%s AAB=%s\n' \
        "${final_apk_sha256}" "${final_aab_sha256}"
}

wait_for_application_pid() {
    local adb_path="$1"
    local serial="$2"
    local attempt=0
    local pid=""

    while (( attempt < 20 )); do
        pid="$("${adb_path}" -s "${serial}" shell pidof "${APPLICATION_ID}" 2>/dev/null \
            | tr -d '\r' | awk '{ print $1 }' || true)"
        if [[ "${pid}" =~ ^[0-9]+$ ]]; then
            printf '%s\n' "${pid}"
            return 0
        fi
        sleep 1
        attempt=$((attempt + 1))
    done
    return 1
}

run_signed_apk_launch_smoke() {
    local adb_path="$1"
    local serial="$2"
    local role="$3"
    local role_label="$4"
    local expected_page_size="$5"
    local full_log="${report_dir}/${role}-signed-apk-logcat.txt"
    local pid_log="${report_dir}/${role}-signed-apk-pid-logcat.txt"
    local scan_log="${report_dir}/${role}-signed-apk-log-scan.txt"
    local package_log="${report_dir}/${role}-signed-apk-package.txt"
    local launch_log="${report_dir}/${role}-signed-apk-launch.txt"
    local launch_component=""
    local pid_before=""
    local pid_after=""
    local installed_version_name=""
    local installed_version_code=""
    local fatal_log_source=""

    assert_device_role_now "${adb_path}" "${serial}" "${expected_page_size}" "${role_label}"
    [[ "$(sha256_file "${apk_path}")" == "${apk_sha256}" ]] || \
        die "Release APK changed after artifact hashing; refusing install."

    # Instrumentation can leave a differently signed target APK installed.
    # Uninstall first so the upload-signed candidate is guaranteed to be the
    # package that the following cold start exercises.
    "${adb_path}" -s "${serial}" uninstall "${TEST_APPLICATION_ID}" >/dev/null 2>&1 || true
    "${adb_path}" -s "${serial}" uninstall "${APPLICATION_ID}" >/dev/null 2>&1 || true
    "${adb_path}" -s "${serial}" install "${apk_path}"

    "${adb_path}" -s "${serial}" shell dumpsys package "${APPLICATION_ID}" > "${package_log}"
    installed_version_name="$(sed -nE 's/^[[:space:]]*versionName=(.*)$/\1/p' "${package_log}" \
        | head -n 1 | tr -d '\r')"
    installed_version_code="$(sed -nE 's/^[[:space:]]*versionCode=([0-9]+).*/\1/p' "${package_log}" \
        | head -n 1 | tr -d '\r')"
    [[ "${installed_version_name}" == "${candidate_version_name}" ]] || \
        die "Installed ${role_label} APK versionName ${installed_version_name:-unknown} does not match ${candidate_version_name}."
    [[ "${installed_version_code}" == "${candidate_version_code}" ]] || \
        die "Installed ${role_label} APK versionCode ${installed_version_code:-unknown} does not match ${candidate_version_code}."

    "${adb_path}" -s "${serial}" logcat -c
    "${adb_path}" -s "${serial}" shell am force-stop "${APPLICATION_ID}"
    launch_component="$(
        "${adb_path}" -s "${serial}" shell cmd package resolve-activity --brief \
            -a android.intent.action.MAIN \
            -c android.intent.category.LAUNCHER \
            "${APPLICATION_ID}" | tr -d '\r' | tail -n 1
    )" || die "Unable to resolve the launcher activity for ${role_label}."
    [[ "${launch_component}" == "${APPLICATION_ID}/"* ]] || \
        die "Resolved launcher activity is invalid for ${role_label}: ${launch_component:-missing}."
    "${adb_path}" -s "${serial}" shell am start -W -n "${launch_component}" > "${launch_log}" || \
        die "Unable to launch the exact signed APK on ${role_label}."
    grep -Eq '^Status:[[:space:]]+ok$' "${launch_log}" || \
        die "Android did not report a successful exact-APK launch on ${role_label}."

    pid_before="$(wait_for_application_pid "${adb_path}" "${serial}" || true)"
    if [[ "${pid_before}" =~ ^[0-9]+$ ]]; then
        sleep "${SMOKE_SETTLE_SECONDS}"
    fi

    "${adb_path}" -s "${serial}" logcat -d -v threadtime > "${full_log}" || \
        die "Unable to capture logcat for ${role_label} exact-APK smoke."
    if [[ "${pid_before}" =~ ^[0-9]+$ ]]; then
        "${adb_path}" -s "${serial}" logcat --pid="${pid_before}" -d -v threadtime > "${pid_log}" || \
            die "Unable to capture PID-filtered logcat for ${role_label} PID ${pid_before}."
    else
        printf 'No application PID was observed after cold start.\n' > "${pid_log}"
    fi

    fatal_log_source="${full_log}"
    if [[ "${pid_before}" =~ ^[0-9]+$ ]]; then
        fatal_log_source="${pid_log}"
    fi
    : > "${scan_log}"
    grep -Ei \
        'FATAL EXCEPTION|Fatal signal [0-9]+|SIG(SEGV|ABRT|BUS|ILL)|Abort message:|OutOfMemoryError|native (crash|fatal)|tombstone' \
        "${fatal_log_source}" >> "${scan_log}" || true
    grep -Ei \
        'ANR in tech\.g24\.feresaslicer|am_anr.*tech\.g24\.feresaslicer|lowmemorykiller.*tech\.g24\.feresaslicer|lmkd.*tech\.g24\.feresaslicer|OutOfMemory.*tech\.g24\.feresaslicer|tombstone.*tech\.g24\.feresaslicer' \
        "${full_log}" >> "${scan_log}" || true
    if [[ -s "${scan_log}" ]]; then
        printf 'Release-blocking logcat evidence on %s (%s):\n' "${role_label}" "${serial}" >&2
        sed 's/^/  /' "${scan_log}" >&2
        return 1
    fi
    [[ "${pid_before}" =~ ^[0-9]+$ ]] || \
        die "Exact signed release APK did not produce a live PID on ${role_label} target ${serial}."
    pid_after="$(wait_for_application_pid "${adb_path}" "${serial}" || true)"
    [[ "${pid_after}" == "${pid_before}" ]] || \
        die "Exact signed release APK PID changed or disappeared during ${role_label} smoke (${pid_before} -> ${pid_after:-missing})."
    assert_device_role_now "${adb_path}" "${serial}" "${expected_page_size}" "${role_label}"
    printf 'No FATAL/ANR/OOM/native fatal evidence found for PID %s.\n' \
        "${pid_before}" > "${scan_log}"

    append_summary "PASS | Exact signed APK ${role_label} launch smoke | serial=${serial} | PID=${pid_before} | version=${installed_version_name} (${installed_version_code}) | ${role}-signed-apk-launch.txt | ${role}-signed-apk-logcat.txt | ${role}-signed-apk-pid-logcat.txt | ${role}-signed-apk-log-scan.txt"
    printf 'Exact signed APK launch smoke passed: role=%s serial=%s pid=%s SHA-256=%s\n' \
        "${role_label}" "${serial}" "${pid_before}" "${apk_sha256}"
    printf 'Smoke scope: install, cold start, PID survival, and log scan only; no automatic slicing claim.\n'
}

append_summary() {
    printf '%s\n' "$*" >> "${summary_file}"
}

run_step() {
    local label="$1"
    local log_name="$2"
    local started_at=""
    local finished_at=""
    local duration_seconds=0
    local status=0
    shift 2

    current_step="${label}"
    started_at="$(date +%s)"
    log "START ${label}"

    set +e
    (set -e; "$@") 2>&1 | tee "${report_dir}/${log_name}.log"
    status=$?
    set -e

    finished_at="$(date +%s)"
    duration_seconds=$((finished_at - started_at))
    if (( status != 0 )); then
        append_summary "FAIL | ${label} | ${duration_seconds}s | ${log_name}.log"
        die "${label} failed (exit ${status}). See ${report_dir}/${log_name}.log"
    fi

    completed_steps=$((completed_steps + 1))
    append_summary "PASS | ${label} | ${duration_seconds}s | ${log_name}.log"
    log "PASS ${label} (${duration_seconds}s)"
}

write_initial_summary() {
    cat > "${summary_file}" <<EOF
Feresa Slicer pre-Play release gate
Run: ${run_id}
Started: ${run_started_at}
Project: ${PROJECT_ROOT}
Device requirement: $(if [[ "${skip_device}" == true ]]; then printf 'SKIPPED by explicit --skip-device'; else printf 'two distinct primary-ARM64 targets: PAGE_SIZE=4096 and PAGE_SIZE=16384'; fi)

Results:
EOF
}

finish_report() {
    local exit_status=$?
    local result="FAILED"

    trap - EXIT
    if [[ -n "${summary_file}" && -f "${summary_file}" ]]; then
        if (( exit_status == 0 )); then
            if [[ "${self_check_only}" == true || "${skip_device}" == true ]]; then
                result="INCOMPLETE"
            else
                result="AUTOMATED PASSED / MANUAL + PLAY-DELIVERED REQUIRED"
            fi
        fi
        {
            printf '\nResult: %s\n' "${result}"
            printf 'Completed steps: %s\n' "${completed_steps}"
            printf 'Finished: %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')"
            if (( exit_status != 0 )); then
                printf 'Upload authorization: BLOCKED\n'
            elif [[ "${self_check_only}" == true ]]; then
                printf 'Upload authorization: INCOMPLETE (self-check only; no tests or build ran)\n'
            elif [[ "${skip_device}" == true ]]; then
                printf 'Upload authorization: INCOMPLETE (both device instrumentation and exact-APK smoke were skipped)\n'
            elif [[ -n "${device_4k_serial}" && -n "${device_16k_serial}" ]]; then
                printf 'ARM64 4 KiB target: %s (%s, PAGE_SIZE=%s)\n' \
                    "${device_4k_serial}" "${device_4k_abi}" "${device_4k_page_size}"
                printf 'ARM64 16 KiB target: %s (%s, PAGE_SIZE=%s)\n' \
                    "${device_16k_serial}" "${device_16k_abi}" "${device_16k_page_size}"
                printf 'Manual matrix: REQUIRED\n'
                printf 'Play-delivered internal test: REQUIRED\n'
                printf 'Upload authorization: INCOMPLETE (automation alone cannot approve upload)\n'
            else
                printf 'Upload authorization: BLOCKED (two-device evidence missing)\n'
            fi
            if (( exit_status != 0 )); then
                printf 'Failed step: %s\n' "${current_step}"
            fi
        } >> "${summary_file}"

        cp "${summary_file}" "${report_root}/latest-summary.txt"
        log "Report: ${summary_file}"
    fi

    exit "${exit_status}"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device)
            [[ $# -ge 2 ]] || { printf 'Missing value for --device\n' >&2; usage >&2; exit 2; }
            requested_device_4k="$2"
            shift 2
            ;;
        --device-16k)
            [[ $# -ge 2 ]] || { printf 'Missing value for --device-16k\n' >&2; usage >&2; exit 2; }
            requested_device_16k="$2"
            shift 2
            ;;
        --skip-device)
            skip_device=true
            shift
            ;;
        --self-check)
            self_check_only=true
            shift
            ;;
        --report-dir)
            [[ $# -ge 2 ]] || { printf 'Missing value for --report-dir\n' >&2; usage >&2; exit 2; }
            report_root="$2"
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

if [[ "${skip_device}" == true && \
      ( -n "${requested_device_4k}" || -n "${requested_device_16k}" ) ]]; then
    die "--device/--device-16k and --skip-device cannot be used together."
fi

if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        export ANDROID_SDK_ROOT="${ANDROID_HOME}"
    elif [[ -d "${HOME}/Library/Android/sdk" ]]; then
        export ANDROID_SDK_ROOT="${HOME}/Library/Android/sdk"
    fi
fi

report_root="$(absolute_directory_path "${report_root}")"
report_dir="${report_root}/${run_id}"
mkdir -p -- "${report_dir}"
summary_file="${report_dir}/summary.txt"
write_initial_summary
trap finish_report EXIT

cd -- "${PROJECT_ROOT}"

for required_file in \
    package-lock.json \
    package.json \
    gradlew \
    scripts/verify-16kb-aab.sh; do
    [[ -f "${required_file}" ]] || die "Required project file is missing: ${required_file}"
done
[[ -x ./gradlew ]] || die "Gradle wrapper is not executable: ${PROJECT_ROOT}/gradlew"
[[ -x scripts/verify-16kb-aab.sh ]] || die "16 KB verifier is not executable."

for command_name in awk basename cmake cp ctest curl find git grep head java jarsigner keytool node npm sed sleep sort stat tee touch tr unzip; do
    require_command "${command_name}"
done

capture_candidate_identity

release_signing_is_configured || die \
    "Release signing is not configured (keystore.properties or all FERESA_UPLOAD_* variables)."
release_keystore_is_readable || die "The configured release keystore file is not readable."

apksigner_path="$(find_sdk_tool apksigner || true)"
[[ -n "${apksigner_path}" ]] || die \
    "apksigner was not found. Install Android SDK Build Tools or add it to PATH."
if command -v shasum >/dev/null 2>&1; then
    sha256_command="shasum"
elif command -v sha256sum >/dev/null 2>&1; then
    sha256_command="sha256sum"
else
    die "shasum or sha256sum is required to record release artifact hashes."
fi
ensure_bundletool
export BUNDLETOOL_JAR="${bundletool_jar}"
append_summary "PASS | Bundletool pin | version=${BUNDLETOOL_VERSION} | SHA-256=${BUNDLETOOL_SHA256}"

adb_path=""
if [[ "${skip_device}" != true ]]; then
    adb_path="$(resolve_adb || true)"
    [[ -n "${adb_path}" ]] || die "adb was not found. Set ANDROID_SDK_ROOT or add adb to PATH."
    select_and_record_devices "${adb_path}"
else
    append_summary "SKIP | Both release instrumentation runs and exact signed APK smoke runs | explicit --skip-device | result is INCOMPLETE"
fi

append_summary "PASS | Preflight | signing and required tools are configured"
completed_steps=$((completed_steps + 1))

if [[ "${self_check_only}" == true ]]; then
    current_step="self-check complete"
    log "Self-check passed. No build was run."
    exit 0
fi

run_step "Install locked viewer dependencies" "01-npm-ci" npm ci
run_step "Viewer unit tests" "02-viewer-tests" npm run test:viewer
run_step "Viewer production bundle" "03-viewer-build" npm run build:viewer
run_step "Committed viewer bundle matches web source" "04-viewer-bundle-freshness" \
    verify_viewer_bundle_is_committed
run_step "Host native regression tests" "05-host-native" run_host_native_tests

run_step "JVM tests, release lint and signed release artifacts" "06-gradle-release" \
    ./gradlew --no-watch-fs \
        :app:testDebugUnitTest \
        :app:testReleaseUnitTest \
        :app:lintRelease \
        :app:assembleRelease \
        :app:bundleRelease

current_step="Release artifact validation"
[[ -s "${apk_path}" ]] || die "Release APK was not produced: ${apk_path}"
[[ -s "${aab_path}" ]] || die "Release AAB was not produced: ${aab_path}"
apk_sha256="$(sha256_file "${apk_path}")"
aab_sha256="$(sha256_file "${aab_path}")"
apk_size="$(file_size "${apk_path}")"
aab_size="$(file_size "${aab_path}")"
append_summary "PASS | Release artifacts exist | APK and AAB"
append_summary "INFO | Exact signed APK | size=${apk_size} | SHA-256=${apk_sha256}"
append_summary "INFO | Exact signed AAB | size=${aab_size} | SHA-256=${aab_sha256}"
completed_steps=$((completed_steps + 1))

run_step "Release APK signature verification" "07-apk-signature" \
    "${apksigner_path}" verify --verbose --print-certs "${apk_path}"
apk_signer_sha256="$(sed -nE 's/^Signer #1 certificate SHA-256 digest:[[:space:]]*(.*)$/\1/p' \
    "${report_dir}/07-apk-signature.log" | head -n 1)"
[[ -n "${apk_signer_sha256}" ]] || die "Verified APK signer SHA-256 digest was not found in apksigner evidence."
[[ "$(normalized_certificate_digest "${apk_signer_sha256}")" == "${UPLOAD_CERT_SHA256}" ]] || \
    die "APK signer does not match the pinned permanent upload certificate."
append_summary "INFO | APK signer certificate SHA-256 | ${apk_signer_sha256} | 07-apk-signature.log"

run_step "Release AAB signature verification" "08-aab-signature" \
    verify_aab_signature_and_print_cert
aab_signer_sha256="$(sed -nE 's/^[[:space:]]*SHA256:[[:space:]]*(.*)$/\1/p' \
    "${report_dir}/08-aab-signer.txt" | head -n 1)"
[[ -n "${aab_signer_sha256}" ]] || die "Verified AAB signer SHA-256 digest was not found in keytool evidence."
[[ "$(normalized_certificate_digest "${aab_signer_sha256}")" == \
   "$(normalized_certificate_digest "${apk_signer_sha256}")" ]] || \
    die "The exact APK and AAB are not signed by the same upload certificate."
append_summary "PASS | Pinned upload certificate | SHA-256=${UPLOAD_CERT_SHA256} | APK and AAB match | 07-apk-signature.log | 08-aab-signer.txt"

run_step "Android App Bundle validation" "09-bundletool-validate" \
    java -jar "${bundletool_jar}" validate --bundle="${aab_path}"
record_aab_manifest_identity

run_step "APK/AAB 16 KB page-size verification" "10-16kb" \
    scripts/verify-16kb-aab.sh --aab "${aab_path}" --apk "${apk_path}"

if [[ "${skip_device}" != true ]]; then
    run_step "Minified release instrumentation on ARM64 4 KiB" "11-release-instrumentation-4k" \
        run_release_instrumentation "${adb_path}" "${device_4k_serial}" 4096 4k 'ARM64 4 KiB'
    run_step "Minified release instrumentation on ARM64 16 KiB" "12-release-instrumentation-16k" \
        run_release_instrumentation "${adb_path}" "${device_16k_serial}" 16384 16k 'ARM64 16 KiB'

    run_step "Exact signed APK launch smoke on ARM64 4 KiB" "13-signed-apk-smoke-4k" \
        run_signed_apk_launch_smoke "${adb_path}" "${device_4k_serial}" 4k 'ARM64 4 KiB' 4096
    run_step "Exact signed APK launch smoke on ARM64 16 KiB" "14-signed-apk-smoke-16k" \
        run_signed_apk_launch_smoke "${adb_path}" "${device_16k_serial}" 16k 'ARM64 16 KiB' 16384
fi

run_step "Candidate commit and clean tree unchanged" "15-final-git-identity" \
    verify_candidate_identity_unchanged
run_step "Exact release artifact hashes unchanged" "16-final-artifact-hashes" \
    verify_artifact_hashes_unchanged

current_step="complete"
log "Automated checks passed. Manual matrix and Play-delivered internal-test evidence are still required."
if [[ "${skip_device}" == true ]]; then
    log "Device checks were skipped explicitly; Result: INCOMPLETE."
fi

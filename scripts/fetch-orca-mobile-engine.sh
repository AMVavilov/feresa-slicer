#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"
MARKER="$OUTPUT_DIR/.orca-mobile-engine.sha256"

ENGINE_COMMIT="6fc2e14b9a222301f4432cee26d7ab37d3be86d0"
ENGINE_RELEASE_TAG="native-engine-6fc2e14-android16k-r1"
ENGINE_ARCHIVE_NAME="orca-mobile-engine-6fc2e14-arm64-v8a-16k-r1.zip"
ENGINE_ARCHIVE_SHA256="fdcf1b82e91a3897e6ec860cedc6e0cb79ed49cfd037a9f5274126edd2560388"
ENGINE_URL_DEFAULT="https://github.com/AMVavilov/feresa-slicer/releases/download/$ENGINE_RELEASE_TAG/$ENGINE_ARCHIVE_NAME"
ENGINE_URL="${FERESA_ORCA_ENGINE_URL:-$ENGINE_URL_DEFAULT}"

ENGINE_LIBSLIC3R_SHA256="24d26ecbc0f5f622d2ddfad90f6f5ec95af91bcfcb05b572817c9f938df36f30"
ENGINE_GMP_SHA256="478989ff7e0933a9644e49a8c509ddaf05c0ba068eb70d903228b0b24159335b"
ENGINE_GMPXX_SHA256="6f052894438b078f653e4af0e448ab5ac99a0c15628801006b4e68f0a0da012a"
ENGINE_MPFR_SHA256="56e238f24b7cac9781f37aa9908bae1fda1244ccd22f2e58422ecf9723ad369d"
ENGINE_LIBCXX_SHA256="ab4e6c71b96b851de45a8a9bd86369e7dbc2130a44b3b4520564be94847910f2"

INSTALLED_LIBRARIES=(
    "libslic3r.so"
    "libgmp.so"
    "libgmpxx.so"
    "libmpfr.so"
)

ARCHIVE_LIBRARIES=(
    "libslic3r.so"
    "libgmp.so"
    "libgmpxx.so"
    "libmpfr.so"
    "libc++_shared.so"
)

die() {
    echo "$*" >&2
    exit 1
}

sha256_file() {
    shasum -a 256 "$1" | awk '{print $1}'
}

expected_library_sha256() {
    case "$1" in
        libslic3r.so) printf '%s\n' "$ENGINE_LIBSLIC3R_SHA256" ;;
        libgmp.so) printf '%s\n' "$ENGINE_GMP_SHA256" ;;
        libgmpxx.so) printf '%s\n' "$ENGINE_GMPXX_SHA256" ;;
        libmpfr.so) printf '%s\n' "$ENGINE_MPFR_SHA256" ;;
        libc++_shared.so) printf '%s\n' "$ENGINE_LIBCXX_SHA256" ;;
        *) return 1 ;;
    esac
}

installed_engine_is_valid() {
    [[ -f "$MARKER" && ! -L "$MARKER" ]] || return 1
    [[ "$(tr -d '\r\n' < "$MARKER")" == "$ENGINE_ARCHIVE_SHA256" ]] || return 1

    local count=0
    local path
    local library
    local expected_sha
    for path in "$OUTPUT_DIR"/*.so; do
        [[ -e "$path" || -L "$path" ]] || continue
        [[ -f "$path" && ! -L "$path" ]] || return 1
        library="$(basename "$path")"
        case "$library" in
            libslic3r.so|libgmp.so|libgmpxx.so|libmpfr.so) ;;
            *) return 1 ;;
        esac
        expected_sha="$(expected_library_sha256 "$library")"
        [[ "$(sha256_file "$path")" == "$expected_sha" ]] || return 1
        count=$((count + 1))
    done

    [[ "$count" -eq "${#INSTALLED_LIBRARIES[@]}" ]]
}

if [[ "${FERESA_ORCA_ENGINE_FORCE:-0}" != "1" ]] && installed_engine_is_valid; then
    exit 0
fi

temp_root="${TMPDIR:-/tmp}"
temp_root="${temp_root%/}"
work_dir="$(mktemp -d "$temp_root/feresa-orca-engine.XXXXXX")"
install_temps=()
cleanup() {
    local temp_file
    for temp_file in "${install_temps[@]}"; do
        rm -f "$temp_file" 2>/dev/null || true
    done
    if [[ -n "$work_dir" && "$work_dir" == "$temp_root"/feresa-orca-engine.* ]]; then
        rm -rf "$work_dir"
    fi
}
trap cleanup EXIT

archive="$work_dir/$ENGINE_ARCHIVE_NAME"
extracted="$work_dir/extracted"
mkdir -p "$extracted" "$OUTPUT_DIR"

curl --fail --location --retry 3 --silent --show-error "$ENGINE_URL" --output "$archive"
actual_archive_sha="$(sha256_file "$archive")"
[[ "$actual_archive_sha" == "$ENGINE_ARCHIVE_SHA256" ]] || \
    die "Orca native engine checksum mismatch: expected $ENGINE_ARCHIVE_SHA256, got $actual_archive_sha"

expected_archive_contents="$(printf 'arm64-v8a/%s\n' "${ARCHIVE_LIBRARIES[@]}")"
actual_archive_contents="$(unzip -Z1 "$archive")"
[[ "$actual_archive_contents" == "$expected_archive_contents" ]] || {
    echo "Unexpected files in the verified Orca native-engine archive:" >&2
    printf '%s\n' "$actual_archive_contents" >&2
    exit 1
}

unzip -q "$archive" -d "$extracted"
for library in "${ARCHIVE_LIBRARIES[@]}"; do
    library_path="$extracted/arm64-v8a/$library"
    [[ -f "$library_path" && ! -L "$library_path" ]] || \
        die "The verified Orca native-engine archive has an invalid $library entry"
    expected_sha="$(expected_library_sha256 "$library")"
    actual_sha="$(sha256_file "$library_path")"
    [[ "$actual_sha" == "$expected_sha" ]] || \
        die "Orca $library checksum mismatch: expected $expected_sha, got $actual_sha"
done

# Refuse to overwrite unrelated native files. The old release integration put
# only the four engine libraries and OCCT's libTK*.so family in this directory.
for path in "$OUTPUT_DIR"/*.so; do
    [[ -e "$path" || -L "$path" ]] || continue
    library="$(basename "$path")"
    case "$library" in
        libslic3r.so|libgmp.so|libgmpxx.so|libmpfr.so|libTK*.so) ;;
        *) die "Refusing to replace unknown native library: $path" ;;
    esac
done

# Stage each replacement in the destination directory, then rename it. The
# marker is written last, so an interrupted install is never accepted as a
# valid cache on the next build.
for library in "${INSTALLED_LIBRARIES[@]}"; do
    temp_output="$OUTPUT_DIR/.feresa-orca-engine.$$.${library}.tmp"
    install_temps+=("$temp_output")
    cp "$extracted/arm64-v8a/$library" "$temp_output"
    mv -f "$temp_output" "$OUTPUT_DIR/$library"
done

# The no-OCCT engine must not leave the former 50-library Open CASCADE runtime
# in jniLibs: Play validates every packaged ELF, even an unreferenced one.
for path in "$OUTPUT_DIR"/libTK*.so; do
    [[ -e "$path" || -L "$path" ]] || continue
    rm -f "$path"
done

marker_temp="$OUTPUT_DIR/.orca-mobile-engine.sha256.tmp.$$"
install_temps+=("$marker_temp")
printf '%s\n' "$ENGINE_ARCHIVE_SHA256" > "$marker_temp"
mv -f "$marker_temp" "$MARKER"

installed_engine_is_valid || die "Installed Orca native-engine set failed final verification"

echo "Installed source-built OrcaSlicer Mobile engine $ENGINE_COMMIT for arm64-v8a (16 KiB, no OCCT)"

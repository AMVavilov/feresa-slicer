#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/app/src/main/jniLibs/arm64-v8a"
MARKER="$OUTPUT_DIR/.orca-mobile-engine.sha256"

ENGINE_COMMIT="6fc2e14b9a222301f4432cee26d7ab37d3be86d0"
ENGINE_APK_SHA256="25bd3b72ff698b43991005f0df65ac57f67766ed4b240c48b8f3ec943eafbbdd"
ENGINE_LIBSLIC3R_SHA256="d3462d2f6ba7612b4d3bd85a4608b1dba5b3b2a52c35f49905c2c4e25defcbcf"
ENGINE_URL="https://github.com/CodeMasterCody3D/OrcaSlicer-Mobile/releases/download/0.4.6/OrcaSlicerMobile_6fc2e14b9a.apk"

if [[ "${FERESA_ORCA_ENGINE_FORCE:-0}" != "1" ]] && \
   [[ -f "$MARKER" ]] && \
   [[ "$(tr -d '\r\n' < "$MARKER")" == "$ENGINE_APK_SHA256" ]] && \
   [[ "$(find "$OUTPUT_DIR" -maxdepth 1 -type f -name '*.so' | wc -l | tr -d ' ')" == "54" ]] && \
   [[ "$(shasum -a 256 "$OUTPUT_DIR/libslic3r.so" | awk '{print $1}')" == "$ENGINE_LIBSLIC3R_SHA256" ]]; then
    exit 0
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/feresa-orca-engine.XXXXXX")"
cleanup() {
    find "$work_dir" -type f -delete 2>/dev/null || true
    find "$work_dir" -depth -type d -empty -delete 2>/dev/null || true
}
trap cleanup EXIT

archive="$work_dir/orca-mobile.apk"
extracted="$work_dir/extracted"
mkdir -p "$extracted" "$OUTPUT_DIR"

curl --fail --location --retry 3 --silent --show-error "$ENGINE_URL" --output "$archive"
actual_sha="$(shasum -a 256 "$archive" | awk '{print $1}')"
if [[ "$actual_sha" != "$ENGINE_APK_SHA256" ]]; then
    echo "Orca native engine checksum mismatch: expected $ENGINE_APK_SHA256, got $actual_sha" >&2
    exit 1
fi

unzip -q -j "$archive" 'lib/arm64-v8a/*.so' -d "$extracted"
if [[ ! -f "$extracted/libslic3r.so" ]]; then
    echo "The verified Orca APK does not contain lib/arm64-v8a/libslic3r.so" >&2
    exit 1
fi
extracted_libslic3r_sha="$(shasum -a 256 "$extracted/libslic3r.so" | awk '{print $1}')"
if [[ "$extracted_libslic3r_sha" != "$ENGINE_LIBSLIC3R_SHA256" ]]; then
    echo "Orca libslic3r checksum mismatch: $extracted_libslic3r_sha" >&2
    exit 1
fi

# Gradle/CMake supplies the newer NDK 27 libc++_shared for both native engines.
# All other shared libraries are the unmodified artifacts from the verified release.
find "$OUTPUT_DIR" -maxdepth 1 -type f -name '*.so' -delete
find "$extracted" -maxdepth 1 -type f -name '*.so' ! -name 'libc++_shared.so' -exec cp {} "$OUTPUT_DIR/" \;
printf '%s\n' "$ENGINE_APK_SHA256" > "$MARKER"

echo "Installed OrcaSlicer Mobile native engine $ENGINE_COMMIT for arm64-v8a"

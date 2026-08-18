#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)
ORCA_ROOT="${REPO_ROOT}/external/orcaslicer"
EXPECTED_ORCA_COMMIT="d5dbd96dd64b830076c81053ed5fda26d5a1771b"
NDK_VERSION="27.1.12297006"
ABI="${ANDROID_ABI:-arm64-v8a}"
PLATFORM="${ANDROID_PLATFORM:-android-28}"
BUILD_DIR="${ORCA_ANDROID_SPIKE_BUILD_DIR:-${REPO_ROOT}/build/orca-android-spike/${ABI}}"

if [ ! -d "${ORCA_ROOT}/.git" ]; then
    echo "Pinned OrcaSlicer checkout is missing; run scripts/fetch-orcaslicer.sh first." >&2
    exit 1
fi

ACTUAL_ORCA_COMMIT=$(git -C "${ORCA_ROOT}" rev-parse HEAD)
if [ "${ACTUAL_ORCA_COMMIT}" != "${EXPECTED_ORCA_COMMIT}" ]; then
    echo "Unexpected OrcaSlicer commit: ${ACTUAL_ORCA_COMMIT}" >&2
    echo "Expected: ${EXPECTED_ORCA_COMMIT}" >&2
    exit 1
fi

NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "${NDK_ROOT}" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    NDK_ROOT="${ANDROID_SDK_ROOT}/ndk/${NDK_VERSION}"
fi
if [ -z "${NDK_ROOT}" ] && [ -n "${ANDROID_HOME:-}" ]; then
    NDK_ROOT="${ANDROID_HOME}/ndk/${NDK_VERSION}"
fi
if [ -z "${NDK_ROOT}" ]; then
    echo "Set ANDROID_NDK_HOME, ANDROID_NDK_ROOT, ANDROID_SDK_ROOT, or ANDROID_HOME." >&2
    exit 1
fi

TOOLCHAIN_FILE="${NDK_ROOT}/build/cmake/android.toolchain.cmake"
if [ ! -f "${TOOLCHAIN_FILE}" ]; then
    echo "Android NDK toolchain not found: ${TOOLCHAIN_FILE}" >&2
    exit 1
fi

NINJA_BIN="${CMAKE_MAKE_PROGRAM:-}"
if [ -z "${NINJA_BIN}" ] && command -v ninja >/dev/null 2>&1; then
    NINJA_BIN=$(command -v ninja)
fi
if [ -z "${NINJA_BIN}" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    NINJA_BIN="${ANDROID_SDK_ROOT}/cmake/3.22.1/bin/ninja"
fi
if [ -z "${NINJA_BIN}" ] || [ ! -x "${NINJA_BIN}" ]; then
    echo "Ninja not found. Set CMAKE_MAKE_PROGRAM to its absolute path." >&2
    exit 1
fi

cmake \
    -S "${REPO_ROOT}/tools/orca-headless-spike" \
    -B "${BUILD_DIR}" \
    -G Ninja \
    -DCMAKE_MAKE_PROGRAM="${NINJA_BIN}" \
    -DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN_FILE}" \
    -DANDROID_ABI="${ABI}" \
    -DANDROID_PLATFORM="${PLATFORM}" \
    -DCMAKE_BUILD_TYPE=Release

cmake --build "${BUILD_DIR}" --target \
    feresa_orca_geometry_spike \
    feresa_orca_geometry_spike_runner

echo "Built ${BUILD_DIR}/libferesa_orca_geometry_spike.so"
echo "Built ${BUILD_DIR}/feresa_orca_geometry_spike_runner"

#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only

set -euo pipefail

# Rebuild the ARM64 OrcaSlicer Mobile engine shipped by Feresa. This is the
# repeatable, fail-closed corresponding-source recipe for
# native-engine-6fc2e14-android16k-r1. It is
# intentionally macOS/arm64-v8a-only because that is the currently published
# Android ABI. Nothing is installed into the application tree by this script.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PATCH_FILE="$SCRIPT_DIR/patches/orca-mobile-6fc2e14-no-occt-ndk28.patch"
WORK_ROOT="${FERESA_NATIVE_BUILD_ROOT:-$PROJECT_ROOT/.native-build-16k}"
DOWNLOAD_DIR="$WORK_ROOT/downloads"
SOURCE_DIR="$WORK_ROOT/source"
BUILD_DIR="$WORK_ROOT/build"
INSTALL_DIR="$WORK_ROOT/install"
STAGE_DIR="$WORK_ROOT/stage"
DIST_DIR="$WORK_ROOT/dist"

ENGINE_REPOSITORY="https://github.com/CodeMasterCody3D/OrcaSlicer-Mobile.git"
ENGINE_COMMIT="6fc2e14b9a222301f4432cee26d7ab37d3be86d0"
NDK_REVISION="28.2.13676358"
ANDROID_ABI="arm64-v8a"
ANDROID_API="21"

GMP_VERSION="6.2.1"
GMP_URL="https://ftp.gnu.org/gnu/gmp/gmp-${GMP_VERSION}.tar.xz"
GMP_SHA256="fd4829912cddd12f84181c3451cc752be224643e87fac497b69edddadc49b4f2"

MPFR_VERSION="4.2.1"
MPFR_URL="https://www.mpfr.org/mpfr-${MPFR_VERSION}/mpfr-${MPFR_VERSION}.tar.xz"
MPFR_SHA256="277807353a6726978996945af13e52829e3abd7a9a5b7fb2793894e18f1fcbb2"

BOOST_VERSION="1.85.0"
BOOST_VERSION_U="1_85_0"
BOOST_URL="https://archives.boost.io/release/${BOOST_VERSION}/source/boost_${BOOST_VERSION_U}.tar.bz2"
BOOST_SHA256="7009fe1faa1697476bdc7027703a2badb84e849b7b0baad5086b087b971f8617"

# This is the TBB2019 Android port selected by syoyo/openvdb-android commit
# 4d4a057. It provides the classic tbb:: ABI used by the pinned mobile engine.
TBB_COMMIT="c0bf89c041df6b794ddf5970854a6b730cb480b1"
TBB_URL="https://codeload.github.com/syoyo/tbb-aarch64/tar.gz/${TBB_COMMIT}"
TBB_SHA256="408f98cb49e92562f231011414f86e6d67c9ae3122cfc7a76fc18f675481f04f"

ARCHIVE_NAME="orca-mobile-engine-6fc2e14-arm64-v8a-16k-r1.zip"
PUBLISHED_ARCHIVE_SHA256="fdcf1b82e91a3897e6ec860cedc6e0cb79ed49cfd037a9f5274126edd2560388"

JOBS="${FERESA_NATIVE_JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)}"
if [[ ! "$JOBS" =~ ^[1-9][0-9]*$ ]]; then
    echo "FERESA_NATIVE_JOBS must be a positive integer" >&2
    exit 1
fi

for command_name in \
    awk basename cat cmp cmake cp curl dirname file git grep make mkdir mv \
    patch sed shasum sort tar touch unzip zip; do
    command -v "$command_name" >/dev/null || {
        echo "Missing required command: $command_name" >&2
        exit 1
    }
done

resolve_ndk() {
    local candidate
    for candidate in \
        "${ANDROID_NDK_HOME:-}" \
        "${ANDROID_NDK_ROOT:-}" \
        "${ANDROID_SDK_ROOT:-}/ndk/$NDK_REVISION" \
        "${ANDROID_HOME:-}/ndk/$NDK_REVISION" \
        "$HOME/Library/Android/sdk/ndk/$NDK_REVISION"; do
        if [[ -n "$candidate" && -f "$candidate/source.properties" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    return 1
}

NDK_ROOT="$(resolve_ndk)" || {
    echo "Android NDK r28c ($NDK_REVISION) was not found" >&2
    exit 1
}
actual_ndk_revision="$(awk -F= '/Pkg.Revision/ {gsub(/[[:space:]]/, "", $2); print $2}' "$NDK_ROOT/source.properties")"
if [[ "$actual_ndk_revision" != "$NDK_REVISION" ]]; then
    echo "Expected NDK $NDK_REVISION, found $actual_ndk_revision at $NDK_ROOT" >&2
    exit 1
fi

HOST_TAG="darwin-x86_64"
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG"
TOOLCHAIN_BIN="$TOOLCHAIN/bin"
ANDROID_TOOLCHAIN="$NDK_ROOT/build/cmake/android.toolchain.cmake"
CC="$TOOLCHAIN_BIN/aarch64-linux-android${ANDROID_API}-clang"
CXX="$TOOLCHAIN_BIN/aarch64-linux-android${ANDROID_API}-clang++"
AR="$TOOLCHAIN_BIN/llvm-ar"
NM="$TOOLCHAIN_BIN/llvm-nm"
RANLIB="$TOOLCHAIN_BIN/llvm-ranlib"
READELF="$TOOLCHAIN_BIN/llvm-readelf"
STRIP="$TOOLCHAIN_BIN/llvm-strip"
for tool in "$CC" "$CXX" "$AR" "$NM" "$RANLIB" "$READELF" "$STRIP"; do
    [[ -x "$tool" ]] || { echo "Missing NDK tool: $tool" >&2; exit 1; }
done

mkdir -p "$DOWNLOAD_DIR" "$SOURCE_DIR" "$BUILD_DIR" "$INSTALL_DIR" "$STAGE_DIR" "$DIST_DIR"

verify_sha256() {
    local path="$1" expected="$2" actual
    actual="$(shasum -a 256 "$path" | awk '{print $1}')"
    if [[ "$actual" != "$expected" ]]; then
        echo "Checksum mismatch for $path: expected $expected, got $actual" >&2
        exit 1
    fi
}

download() {
    local url="$1" path="$2" sha="$3"
    if [[ ! -f "$path" ]]; then
        local temporary="${path}.download"
        curl --fail --location --retry 3 --silent --show-error "$url" --output "$temporary"
        verify_sha256 "$temporary" "$sha"
        mv "$temporary" "$path"
    fi
    verify_sha256 "$path" "$sha"
}

download "$GMP_URL" "$DOWNLOAD_DIR/gmp-${GMP_VERSION}.tar.xz" "$GMP_SHA256"
download "$MPFR_URL" "$DOWNLOAD_DIR/mpfr-${MPFR_VERSION}.tar.xz" "$MPFR_SHA256"
download "$BOOST_URL" "$DOWNLOAD_DIR/boost_${BOOST_VERSION_U}.tar.bz2" "$BOOST_SHA256"
download "$TBB_URL" "$DOWNLOAD_DIR/tbb-aarch64-${TBB_COMMIT}.tar.gz" "$TBB_SHA256"

extract_once() {
    local archive="$1" expected_dir="$2"
    if [[ ! -d "$expected_dir" ]]; then
        tar -xf "$archive" -C "$SOURCE_DIR"
    fi
    [[ -d "$expected_dir" ]] || { echo "Archive did not create $expected_dir" >&2; exit 1; }
}

GMP_SOURCE="$SOURCE_DIR/gmp-${GMP_VERSION}"
MPFR_SOURCE="$SOURCE_DIR/mpfr-${MPFR_VERSION}"
BOOST_SOURCE="$SOURCE_DIR/boost_${BOOST_VERSION_U}"
TBB_SOURCE="$SOURCE_DIR/tbb-aarch64-${TBB_COMMIT}"
extract_once "$DOWNLOAD_DIR/gmp-${GMP_VERSION}.tar.xz" "$GMP_SOURCE"
extract_once "$DOWNLOAD_DIR/mpfr-${MPFR_VERSION}.tar.xz" "$MPFR_SOURCE"
extract_once "$DOWNLOAD_DIR/boost_${BOOST_VERSION_U}.tar.bz2" "$BOOST_SOURCE"
extract_once "$DOWNLOAD_DIR/tbb-aarch64-${TBB_COMMIT}.tar.gz" "$TBB_SOURCE"

ENGINE_SOURCE="$SOURCE_DIR/orcaslicer-mobile-port"
engine_was_cloned=0
if [[ ! -d "$ENGINE_SOURCE/.git" ]]; then
    git clone --filter=blob:none --no-checkout "$ENGINE_REPOSITORY" "$ENGINE_SOURCE"
    engine_was_cloned=1
fi
git -C "$ENGINE_SOURCE" fetch --depth 1 origin "$ENGINE_COMMIT"
if [[ "$engine_was_cloned" == "1" ]]; then
    # A --no-checkout clone has no populated index/worktree and therefore can
    # look dirty. Check out the pinned commit before applying any dirty-tree
    # policy.
    git -C "$ENGINE_SOURCE" checkout --detach "$ENGINE_COMMIT"
elif [[ "$(git -C "$ENGINE_SOURCE" rev-parse HEAD 2>/dev/null || true)" != "$ENGINE_COMMIT" ]]; then
    if [[ -n "$(git -C "$ENGINE_SOURCE" status --short 2>/dev/null || true)" ]]; then
        echo "Refusing to replace a modified engine checkout: $ENGINE_SOURCE" >&2
        exit 1
    fi
    git -C "$ENGINE_SOURCE" checkout --detach "$ENGINE_COMMIT"
fi
if patch -d "$ENGINE_SOURCE" -p1 --dry-run --silent < "$PATCH_FILE"; then
    patch -d "$ENGINE_SOURCE" -p1 < "$PATCH_FILE"
elif patch -d "$ENGINE_SOURCE" -p1 -R --dry-run --silent < "$PATCH_FILE"; then
    echo "Feresa no-OCCT/NDK r28 patch is already applied"
else
    echo "Engine checkout does not match the pinned patch baseline" >&2
    exit 1
fi

if grep -n 'oneapi/tbb' \
    "$ENGINE_SOURCE/app/src/main/jni/clipper/clipper.hpp" \
    "$ENGINE_SOURCE/app/src/main/jni/libslic3r/JumpPointSearch.cpp" \
    "$ENGINE_SOURCE/app/src/main/jni/libslic3r/PrintObject.cpp" \
    "$ENGINE_SOURCE/app/src/main/jni/libslic3r/Support/SupportLayer.hpp" \
    "$ENGINE_SOURCE/app/src/main/jni/libslic3r/Point.hpp"; then
    echo "Active oneapi/tbb include remains after the TBB2019 compatibility patch" >&2
    exit 1
fi

COMMON_CFLAGS="-O2 -fPIC"
PAGE_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
GMP_BUILD="$BUILD_DIR/gmp-${GMP_VERSION}-arm64-api${ANDROID_API}"
GMP_PREFIX="$INSTALL_DIR/gmp-${GMP_VERSION}-arm64-api${ANDROID_API}"
mkdir -p "$GMP_BUILD" "$GMP_PREFIX"
(
    cd "$GMP_BUILD"
    env \
        ABI=64 \
        CC="$CC" CXX="$CXX" AR="$AR" NM="$NM" RANLIB="$RANLIB" STRIP="$STRIP" \
        CFLAGS="$COMMON_CFLAGS" CXXFLAGS="$COMMON_CFLAGS" LDFLAGS="$PAGE_LDFLAGS" \
        "$GMP_SOURCE/configure" \
            --host=aarch64-linux-android \
            --prefix="$GMP_PREFIX" \
            --enable-cxx \
            --enable-shared \
            --disable-static
    make -j"$JOBS"
    make install
)

MPFR_BUILD="$BUILD_DIR/mpfr-${MPFR_VERSION}-arm64-api${ANDROID_API}"
MPFR_PREFIX="$INSTALL_DIR/mpfr-${MPFR_VERSION}-arm64-api${ANDROID_API}"
mkdir -p "$MPFR_BUILD" "$MPFR_PREFIX"
(
    cd "$MPFR_BUILD"
    env \
        CC="$CC" CXX="$CXX" AR="$AR" NM="$NM" RANLIB="$RANLIB" STRIP="$STRIP" \
        CFLAGS="$COMMON_CFLAGS" CXXFLAGS="$COMMON_CFLAGS" \
        CPPFLAGS="-I$GMP_PREFIX/include" \
        LDFLAGS="-L$GMP_PREFIX/lib -Wl,-rpath-link,$GMP_PREFIX/lib $PAGE_LDFLAGS" \
        "$MPFR_SOURCE/configure" \
            --host=aarch64-linux-android \
            --prefix="$MPFR_PREFIX" \
            --with-gmp="$GMP_PREFIX" \
            --enable-shared \
            --disable-static
    make -j"$JOBS"
    make install
)

TBB_BUILD="$BUILD_DIR/tbb-2019-arm64-api${ANDROID_API}"
TBB_PREFIX="$INSTALL_DIR/tbb-2019-arm64-api${ANDROID_API}"
cmake \
    -S "$TBB_SOURCE" \
    -B "$TBB_BUILD" \
    -G "Unix Makefiles" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_TOOLCHAIN" \
    -DANDROID_ABI="$ANDROID_ABI" \
    -DANDROID_PLATFORM="android-$ANDROID_API" \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$TBB_PREFIX" \
    -DTBB_BUILD_TESTS=OFF \
    -DTBB_BUILD_SHARED=OFF \
    -DTBB_BUILD_STATIC=ON \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5
cmake --build "$TBB_BUILD" --parallel "$JOBS"
cmake --install "$TBB_BUILD"

BOOST_BUILD="$BUILD_DIR/boost-${BOOST_VERSION}-arm64-api${ANDROID_API}"
BOOST_PREFIX="$INSTALL_DIR/boost-${BOOST_VERSION}-arm64-api${ANDROID_API}"
BOOST_USER_CONFIG="$WORK_ROOT/boost-android-arm64-r28c.jam"
mkdir -p "$BOOST_BUILD" "$BOOST_PREFIX"
cat > "$BOOST_USER_CONFIG" <<EOF
using clang : arm64v8a
    : $CXX
    :
      <archiver>$AR
      <ranlib>$RANLIB
      <compileflags>-fPIC
      <compileflags>-DANDROID
      <compileflags>-D__ANDROID__
      <compileflags>-D__ANDROID_API__=$ANDROID_API
      <compileflags>-fexceptions
      <compileflags>-frtti
      <compileflags>-ffunction-sections
      <compileflags>-fdata-sections
      <compileflags>-march=armv8-a
      <linkflags>-Wl,-z,max-page-size=16384
      <linkflags>-Wl,-z,common-page-size=16384
    ;
EOF
(
    cd "$BOOST_SOURCE"
    [[ -x ./b2 ]] || ./bootstrap.sh
    boost_common=(
        -q
        -j"$JOBS"
        --user-config="$BOOST_USER_CONFIG"
        --build-dir="$BOOST_BUILD"
        --prefix="$BOOST_PREFIX"
        toolset=clang-arm64v8a
        target-os=android
        architecture=arm
        address-model=64
        abi=aapcs
        binary-format=elf
        link=static
        runtime-link=shared
        threading=multi
        variant=release
        visibility=hidden
        --layout=versioned
    )
    initial_libraries=(
        atomic charconv chrono container context contract coroutine date_time
        exception fiber filesystem graph iostreams json log nowide random regex
        serialization stacktrace system test thread timer type_erasure url wave
    )
    initial_with=()
    for library in "${initial_libraries[@]}"; do initial_with+=("--with-$library"); done
    ./b2 "${boost_common[@]}" cxxstd=11 "${initial_with[@]}" install
    ./b2 "${boost_common[@]}" cxxstd=17 --with-math install
    ./b2 "${boost_common[@]}" cxxstd=17 --with-program_options install
)

JNI_IMPORTS="$ENGINE_SOURCE/app/src/main/jniImports"
JNI_LIBS="$ENGINE_SOURCE/app/src/main/jniLibs/$ANDROID_ABI"
mkdir -p \
    "$JNI_IMPORTS/oneTBB/include" \
    "$JNI_IMPORTS/oneTBB/lib/$ANDROID_ABI" \
    "$JNI_IMPORTS/boost/include" \
    "$JNI_IMPORTS/boost/lib/$ANDROID_ABI/lib" \
    "$JNI_IMPORTS/gmp/include/$ANDROID_ABI" \
    "$JNI_LIBS"
cp -R "$TBB_PREFIX/include/." "$JNI_IMPORTS/oneTBB/include/"
cp "$TBB_PREFIX/lib/libtbb_static.a" "$JNI_IMPORTS/oneTBB/lib/$ANDROID_ABI/libtbb.a"
cp "$TBB_PREFIX/lib/libtbbmalloc_static.a" "$JNI_IMPORTS/oneTBB/lib/$ANDROID_ABI/libtbbmalloc.a"
cp -R "$BOOST_PREFIX/include/boost-1_85/." "$JNI_IMPORTS/boost/include/"
for archive in "$BOOST_PREFIX"/lib/libboost_*-clang-darwin-mt-a64-1_85.a; do
    base="$(basename "$archive")"
    expected="${base/-clang-darwin-/-clang-}"
    cp "$archive" "$JNI_IMPORTS/boost/lib/$ANDROID_ABI/lib/$expected"
done
cp \
    "$JNI_IMPORTS/boost/lib/$ANDROID_ABI/lib/libboost_test_exec_monitor-clang-mt-a64-1_85.a" \
    "$JNI_IMPORTS/boost/lib/$ANDROID_ABI/lib/libboost_test_exec_moinotr-clang-mt-a64-1_85.a"
cp "$GMP_PREFIX/include/gmp.h" "$JNI_IMPORTS/gmp/include/$ANDROID_ABI/gmp.h"
cp "$GMP_PREFIX/lib/libgmp.so" "$GMP_PREFIX/lib/libgmpxx.so" "$MPFR_PREFIX/lib/libmpfr.so" "$JNI_LIBS/"

ENGINE_BUILD="$BUILD_DIR/libslic3r-${ENGINE_COMMIT:0:7}-arm64-api${ANDROID_API}-no-occt"
cmake \
    -S "$ENGINE_SOURCE/app" \
    -B "$ENGINE_BUILD" \
    -G "Unix Makefiles" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_TOOLCHAIN" \
    -DANDROID_ABI="$ANDROID_ABI" \
    -DANDROID_PLATFORM="android-$ANDROID_API" \
    -DANDROID_STL=c++_shared \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
    -DFERESA_DISABLE_OCCT=ON \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    '-DSLIC3R_VERSION="0.4.5"' \
    '-DSLIC3R_BUILD_ID="3"' \
    '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384'
cmake --build "$ENGINE_BUILD" --parallel "$JOBS"

ENGINE_SO="$ENGINE_BUILD/libslic3r.so"
[[ -f "$ENGINE_SO" ]] || { echo "libslic3r.so was not produced" >&2; exit 1; }
before_strip="$WORK_ROOT/libslic3r.dynsym.before-strip"
after_strip="$WORK_ROOT/libslic3r.dynsym.after-strip"
"$NM" -D --defined-only "$ENGINE_SO" | sort > "$before_strip"
"$STRIP" --strip-unneeded "$ENGINE_SO"
"$NM" -D --defined-only "$ENGINE_SO" | sort > "$after_strip"
cmp "$before_strip" "$after_strip"

LIBCXX="$TOOLCHAIN/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
FINAL_LIBRARIES=(
    "$ENGINE_SO"
    "$GMP_PREFIX/lib/libgmp.so"
    "$GMP_PREFIX/lib/libgmpxx.so"
    "$MPFR_PREFIX/lib/libmpfr.so"
    "$LIBCXX"
)

audit_dynamic_section() {
    local library="$1" expected_soname="$2"
    shift 2
    local actual_soname actual_needed expected_needed
    actual_soname="$("$READELF" -d "$library" | awk '/\(SONAME\)/ {sub(/^.*\[/, ""); sub(/\].*$/, ""); print}')"
    if [[ "$actual_soname" != "$expected_soname" ]]; then
        echo "Unexpected SONAME in $library: expected $expected_soname, got ${actual_soname:-<none>}" >&2
        exit 1
    fi
    actual_needed="$("$READELF" -d "$library" | awk '/\(NEEDED\)/ {sub(/^.*\[/, ""); sub(/\].*$/, ""); print}' | LC_ALL=C sort)"
    expected_needed="$(printf '%s\n' "$@" | LC_ALL=C sort)"
    if [[ "$actual_needed" != "$expected_needed" ]]; then
        echo "Unexpected DT_NEEDED set in $library" >&2
        echo "Expected:" >&2
        echo "$expected_needed" >&2
        echo "Actual:" >&2
        echo "$actual_needed" >&2
        exit 1
    fi
}

for library in "${FINAL_LIBRARIES[@]}"; do
    [[ -f "$library" ]] || { echo "Missing final library: $library" >&2; exit 1; }
    bad_loads="$("$READELF" -lW "$library" | awk '$1 == "LOAD" && $NF != "0x4000" {print}')"
    if [[ -n "$bad_loads" ]]; then
        echo "Non-16-KiB LOAD alignment in $library:" >&2
        echo "$bad_loads" >&2
        exit 1
    fi
done
audit_dynamic_section "$ENGINE_SO" "libslic3r.so" \
    libm.so libdl.so libmpfr.so libgmp.so libgmpxx.so liblog.so \
    libEGL.so libGLESv3.so libc++_shared.so libc.so
audit_dynamic_section "$GMP_PREFIX/lib/libgmp.so" "libgmp.so" \
    libdl.so libc.so
audit_dynamic_section "$GMP_PREFIX/lib/libgmpxx.so" "libgmpxx.so" \
    libgmp.so libc++_shared.so libm.so libc.so libdl.so
audit_dynamic_section "$MPFR_PREFIX/lib/libmpfr.so" "libmpfr.so" \
    libgmp.so libdl.so libc.so
audit_dynamic_section "$LIBCXX" "libc++_shared.so" \
    libc.so libm.so libdl.so
if [[ "$("$NM" -D --defined-only "$ENGINE_SO" | awk '$3 ~ /^Java_/ {count++} END {print count+0}')" != "117" ]]; then
    echo "Unexpected JNI export count in libslic3r.so" >&2
    exit 1
fi
exported_symbol_names="$WORK_ROOT/libslic3r.exported-symbols"
"$NM" -D --defined-only "$ENGINE_SO" | awk '{print $3}' | LC_ALL=C sort > "$exported_symbol_names"
required_jni_symbols=(
    JNI_OnLoad
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_get_1print_1config_1def
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_set_1svg_1path_1prefix
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1read_1from_1file
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1objects_1count
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1get_1bounding_1box_1exact_1global
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1translate
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1translate_1global
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1ensure_1on_1bed
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1scale
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1rotate
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1slice
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_model_1release
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1recommended_1name
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1used_1filament_1mm
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1get_1used_1filament_1g
    Java_ru_ytkab0bp_slicebeam_slic3r_Native_gcoderesult_1release
)
for required_symbol in "${required_jni_symbols[@]}"; do
    if ! grep -Fqx "$required_symbol" "$exported_symbol_names"; then
        echo "Required Feresa JNI symbol is missing: $required_symbol" >&2
        exit 1
    fi
done

PACKAGE_ROOT="$STAGE_DIR/package"
PACKAGE_ABI="$PACKAGE_ROOT/$ANDROID_ABI"
mkdir -p "$PACKAGE_ABI"
cp "$ENGINE_SO" "$PACKAGE_ABI/libslic3r.so"
cp "$GMP_PREFIX/lib/libgmp.so" "$PACKAGE_ABI/libgmp.so"
cp "$GMP_PREFIX/lib/libgmpxx.so" "$PACKAGE_ABI/libgmpxx.so"
cp "$MPFR_PREFIX/lib/libmpfr.so" "$PACKAGE_ABI/libmpfr.so"
cp "$LIBCXX" "$PACKAGE_ABI/libc++_shared.so"
TZ=UTC touch -t 202608240000 "$PACKAGE_ABI"/*.so

ARCHIVE="$DIST_DIR/$ARCHIVE_NAME"
(
    cd "$PACKAGE_ROOT"
    zip -FS -X -9 "$ARCHIVE" \
        "$ANDROID_ABI/libslic3r.so" \
        "$ANDROID_ABI/libgmp.so" \
        "$ANDROID_ABI/libgmpxx.so" \
        "$ANDROID_ABI/libmpfr.so" \
        "$ANDROID_ABI/libc++_shared.so"
)
unzip -t "$ARCHIVE"
actual_entries="$(unzip -Z1 "$ARCHIVE")"
expected_entries="$(cat <<EOF
$ANDROID_ABI/libslic3r.so
$ANDROID_ABI/libgmp.so
$ANDROID_ABI/libgmpxx.so
$ANDROID_ABI/libmpfr.so
$ANDROID_ABI/libc++_shared.so
EOF
)"
if [[ "$actual_entries" != "$expected_entries" ]]; then
    echo "Unexpected archive contents:" >&2
    echo "$actual_entries" >&2
    exit 1
fi
(
    cd "$PACKAGE_ROOT"
    shasum -a 256 \
        "$ANDROID_ABI/libslic3r.so" \
        "$ANDROID_ABI/libgmp.so" \
        "$ANDROID_ABI/libgmpxx.so" \
        "$ANDROID_ABI/libmpfr.so" \
        "$ANDROID_ABI/libc++_shared.so"
) > "$DIST_DIR/$ARCHIVE_NAME.SHA256SUMS"
archive_sha="$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')"
printf '%s  %s\n' "$archive_sha" "$ARCHIVE_NAME" > "$DIST_DIR/$ARCHIVE_NAME.sha256"

cat <<EOF
Built and audited: $ARCHIVE
SHA-256: $archive_sha
Reference published r1 SHA-256: $PUBLISHED_ARCHIVE_SHA256
Per-library hashes: $DIST_DIR/$ARCHIVE_NAME.SHA256SUMS
EOF

if [[ "$archive_sha" != "$PUBLISHED_ARCHIVE_SHA256" ]]; then
    cat <<EOF
Note: the rebuilt archive is ELF/API-audited but is not byte-for-byte identical
to the reference r1 asset. Absolute build paths and host tool versions can alter
stripped native bytes; publish a new revision instead of replacing r1.
EOF
fi

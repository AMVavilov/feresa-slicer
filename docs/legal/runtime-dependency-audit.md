# Runtime dependency and license audit

Audit target date: 2026-08-24
Release candidate: 0.16.0-alpha.1 (version code 31)

This is an engineering audit, not legal advice. The release owner must verify
the source offer and license obligations before each Google Play submission.

## Evidence used

- `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
- `unzip -l app/build/outputs/bundle/release/app-release.aab`
- ELF `NEEDED` entries from the pinned `libslic3r.so`
- OrcaSlicer Mobile source revision
  `6fc2e14b9a222301f4432cee26d7ab37d3be86d0`
- the source-build dependency manifest and CMake input list for that revision
- `scripts/build-orca-mobile-engine-16kb.sh` and its pinned no-OCCT/NDK r28c
  source patch
- Android NDK 28.2.13676358 (r28c) used by Feresa
- `scripts/verify-16kb-aab.sh` against the final AAB and APK

The human-readable result is `THIRD_PARTY_NOTICES.md`. Full license texts are
in `third_party_licenses/`; Gradle copies them into
`assets/legal/licenses/` in every Android build.

## Managed Android runtime

The resolved release graph contains Kotlin stdlib 2.1.20, coroutines 1.9.0,
AndroidX Activity 1.10.0, Compose 1.7.6, Material3 1.3.1, Lifecycle 2.8.7,
WebKit 1.17.0, Core 1.13.1, Collection 1.4.4 and their AndroidX support
artifacts. It also contains JetBrains annotations 23.0.0, JSpecify 1.0.0 and
Guava's listenablefuture compatibility artifact 1.0. These are covered by the
bundled Apache License 2.0 text and attribution in the notice file.

Debug-only, unit-test and instrumentation-test configurations are excluded
from this runtime list.

## Native runtime

The ARM64 bundle contains `libslic3r.so`, `libgmp.so`, `libgmpxx.so`,
`libmpfr.so`, r28c's `libc++_shared.so`, Feresa's JNI library, and AndroidX
Graphics Path's native library. The source-built `libslic3r.so` declares no
Open CASCADE / `libTK*.so` dependency. Its non-system dynamic dependencies are
limited to GMP, GMP C++, MPFR, and libc++.

The no-OCCT patch excludes the OCCT-backed STEP import and SVG/TextShape
object-construction paths. Feresa imports STL, OBJ, and 3MF, and normalizes
OBJ/3MF geometry to STL before calling the native engine.

The upstream CMake input list also incorporates ADMesh, Boost, CGAL, Clipper,
cereal, Eigen, Expat, fast_float, GLU libtess, heatshrink, Dear ImGui,
libigl, libjpeg-turbo, libnest2d, libpng, MCUT/CDT, miniz, nlohmann/json,
NLopt, OpenVDB, Qhull, QOI, oneTBB and zlib into `libslic3r.so`.
The corresponding license families and component notices are bundled.

## Items requiring release-owner action

1. The exact corresponding source for the shipped AAB, including native
   dependency build material and installation/relink instructions, must be
   available at the source URL shown in the app and notices.
2. Keep every source archive, revision and SHA-256 in the native build manifest
   pinned. Regenerate the engine rather than importing an untracked APK binary
   whenever the toolchain or dependency graph changes.
3. LGPL components that are incorporated into `libslic3r.so` (including
   NLopt, MCUT and libnest2d) require source/object-code and relinking
   compliance. Dynamically linked GMP/MPFR also require users to be able to
   run a modified compatible library. Google Play delivery by itself does not
   solve those obligations.
4. Qhull's license asks distributors to make its source available. Preserve
   its notice and source link.
5. Re-run this audit whenever the Gradle lock graph, native archive checksum,
   source revision, NDK version, or viewer bundle changes.

## Verification commands

```sh
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
./gradlew :app:bundleRelease
scripts/verify-16kb-aab.sh \
  --aab app/build/outputs/bundle/release/app-release.aab \
  --apk app/build/outputs/apk/release/app-release.apk
unzip -l app/build/outputs/bundle/release/app-release.aab |
  grep 'base/assets/legal/'
```

A release is not ready if the last command does not list
`THIRD_PARTY_NOTICES.md`, `AGPL-3.0.txt`, and every file in
`third_party_licenses/`.

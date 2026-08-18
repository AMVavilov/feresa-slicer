# OrcaSlicer headless Android build spike

Feresa pins OrcaSlicer at commit
`d5dbd96dd64b830076c81053ed5fda26d5a1771b`. The production target is the
FFF slicing pipeline in `src/libslic3r`, without Orca's wxWidgets GUI, OpenGL
viewer, printer discovery, cloud integrations, Python tooling, or Bambu device
components.

## Verified first NDK milestone

`tools/orca-headless-spike` directly builds Orca's vendored Clipper2 target and
links it into an Android shared object. Clipper2 is used by `libslic3r` for
polygon clipping and offsets, so this is a real dependency integration rather
than a replacement geometry implementation.

The same NDK graph also compiles the first self-contained dependency tranche
from the pinned source tree: miniz, qhull, qoi, semver, glu-libtess, and mcut.
They are linked into the spike artifact so the build cannot succeed by merely
configuring unused targets.

The same offset check is also linked as the Android executable
`feresa_orca_geometry_spike_runner`. It exits with status zero only when the
pinned Clipper2 code expands the test polygon as expected. It may be pushed to
an emulator or device for a runtime check:

```sh
adb push build/orca-android-spike/arm64-v8a/feresa_orca_geometry_spike_runner \
  /data/local/tmp/
adb shell chmod 755 /data/local/tmp/feresa_orca_geometry_spike_runner
adb shell /data/local/tmp/feresa_orca_geometry_spike_runner
```

On macOS with the SDK used by the application:

```sh
ANDROID_SDK_ROOT=/Users/amvavilov/Library/Android/sdk \
  scripts/build-orca-android-spike.sh
```

The script validates the pinned Orca commit and builds for `arm64-v8a`, API 28
by default. Override those with `ANDROID_ABI` and `ANDROID_PLATFORM`.

## Why upstream `libslic3r` cannot be added as-is

At the pinned commit, `SLIC3R_GUI=OFF` only prevents `src/slic3r` and wxWidgets
from being added. The top-level configure still unconditionally resolves Boost,
Eigen, TBB, OpenSSL, CURL, Freetype, ZLIB, EXPAT, PNG, OpenGL, GLFW, cereal,
NLopt, Python, and OpenVDB. `src/libslic3r/CMakeLists.txt` additionally requires
CGAL, OpenCV, OpenCASCADE, JPEG, Draco, fontconfig, and the in-tree geometry
libraries.

The upstream dependency superbuild is also desktop-oriented: it always adds
GLEW/GLFW/OpenCSG, wxWidgets, Python, OpenVDB, OpenCV, OCCT, and wxInspector.
An Android configure currently stops in `deps/GLEW/GLEW.cmake` because the NDK
does not provide desktop OpenGL. Configuring the application tree directly gets
past the NDK compiler checks and then stops at the first absent target package,
Boost 1.83.

Full current target graph:

```text
libslic3r
├── public: Eigen3, admesh, libigl, libnest2d, miniz, opencv_world
└── private
    ├── expat, boost, cereal, clipper, Clipper2
    ├── draco, glu-libtess, JPEG, mcut, libnoise, PNG, qhull, qoi
    ├── semver, TBB + tbbmalloc, ZLIB, OpenSSL::Crypto
    ├── libslic3r_cgal → CGAL, admesh, libigl, mcut, boost
    ├── OpenCASCADE (STEP/CAD import and mesh operations)
    ├── freetype + fontconfig on non-Apple Unix
    └── OpenVDB when available
```

## Smallest compilable production milestones

1. Continue the isolated `ORCA_HEADLESS_FFF` CMake path outside the desktop
   top-level. Clipper2, miniz, qhull, qoi, semver, mcut, and glu-libtess are now
   proven on the NDK. Add admesh, libigl, and libnest2d after Eigen, Boost, TBB,
   and NLopt are available for Android.
2. Cross-build the required third-party FFF packages for every APK ABI: Boost,
   Eigen, TBB, OpenSSL, zlib, expat, PNG/JPEG, cereal, Draco, libnoise, and CGAL.
   Preserve Orca's exact versions and compile definitions.
3. Split `libslic3r` sources behind explicit feature flags. For the first
   production slice, exclude GUI, SLA, STEP/OCCT import, OpenVDB, Python,
   networking, OpenCV-only helpers, and device code while retaining Model,
   TriangleMesh/STL, Print/PrintConfig, layers, perimeters/Arachne, fills,
   supports, G-code generation, and profile inheritance.
4. Produce `liborca_fff_android.a` and a tiny native runner that loads a cube
   STL, applies an Orca `DynamicPrintConfig`, calls `Print::process()`, exports
   G-code, and checks walls, top/bottom shells, sparse infill, and support roles.
5. Only after the runner matches pinned desktop Orca fixtures should the app's
   JNI bridge switch engines. UI settings must remain disabled until each one
   is represented in the resolved Orca configuration and comparison tests.

The current spike is intentionally isolated from Gradle and the production JNI
target. It can therefore be advanced one dependency group at a time without
silently changing APK slicing behavior.

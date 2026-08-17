# Verification record

## 2026-08-17 Feresa Slicer repository split

- Renamed the Android namespace and application ID to
  `tech.g24.feresaslicer`.
- Renamed the JNI package bridge and native library to `feresa_slicer`.
- Viewer parser tests passed: 4 tests, 0 failures.
- Native host CMake build and CTest passed: 1 test, 0 failures.
- Android `assembleDebug` and `lintDebug` passed after the rename.
- Repository secret scan found no private keys, service tokens, passwords,
  signing material, personal profile cache, or machine-specific SDK paths.

## 2026-08-15 Android technical preview

Environment:

- macOS host, JDK 17
- Gradle 8.9, Android Gradle Plugin 8.7.3
- Android SDK 35, NDK 27.1.12297006, CMake 3.22.1
- Pixel 8 emulator, Android 35, ARM64

Checks completed:

- Native host build and CTest passed.
- Android `assembleDebug` passed.
- Android `lintDebug` passed with no errors.
- APK contains `lib/arm64-v8a/libferesa_slicer.so`.
- APK installed and launched without a JNI or runtime crash.
- `test-data/cube-10mm.stl` imported through Android's document picker.
- Three.js WebGL scene rendered the cube and print-bed grid in Android WebView.
- Orbit interaction and reset/fit camera behavior rendered without a crash.
- Centered cube reported bounds X/Y 105–115 mm on the 220 × 220 mm bed.
- Moving X to 1.4 mm reported bounds X -3.6–6.4 mm, displayed the out-of-bed
  warning, and disabled slicing.
- On-device slice produced 50 layers, 400 extrusion segments, and a 567-line
  G-code file.
- Generated G-code retained the prepared placement (X/Y 105–115 mm), and the
  toolpath view rendered all 400 extrusion segments.

The native contour engine is a technical baseline, not production slicing
logic. Its perimeter-only G-code must not be used for unattended printing.

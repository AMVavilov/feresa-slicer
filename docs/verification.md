# Verification record

## 2026-08-18 Feresa Slicer 0.13.0-alpha.1 printer phase

The current working tree adds the following automated coverage for the new
Printer workflow:

- `PrinterConnectionServiceTest` uses loopback HTTP servers to exercise typed
  Moonraker and OctoPrint identity/status parsing, printer/job/progress and
  temperature mapping, offline/startup behavior, authentication headers,
  redirect refusal, fixed-length multipart uploads, returned-path validation,
  URL encoding, and the separation between upload and start.
- `ManualPrinterConnectionStoreTest` covers host normalization, port and
  credential validation, and saved-connection serialization.
- `SliceArtifactGenerationTest` covers rejection of an artifact produced for
  an older model/settings generation.
- `PrinterStorageInstrumentedTest` checks that manually saved credentials and
  the OrcaCloud profile cache can be restored but their secrets do not appear
  in the persisted encrypted envelopes; it also checks that the legacy
  plaintext cache is absent after an encrypted write.

Completed verification on the final release source:

- `npm run test:viewer`: 28 tests, 0 failures.
- `./gradlew -I /tmp/feresa-parity-builddir.gradle --no-watch-fs
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: 117 JVM tests,
  0 failures/errors/skips; lint and the ARM64 debug APK build passed.
- `./gradlew -I /tmp/feresa-parity-builddir.gradle --no-watch-fs
  :app:connectedDebugAndroidTest`: 8 tests, 0 failures on an ARM64 Android
  16/API 36 emulator. This includes native Orca parity/plate fixtures and the
  isolated encrypted-storage checks; the tests do not erase the emulator's
  real saved connection or Orca profile cache.
- The final `0.13.0-alpha.1` APK (`versionCode 26`) installed and launched on
  that emulator. The Model empty state, Printer empty state, and the complete
  manual Moonraker/OctoPrint editor were visually checked at 1080 x 2400.
- APK SHA-256:
  `46f489d75642fe21a50294f08200e3df0cb4eb2f13e9c5fc91fd364975c26863`.
- Android build-tools verified the APK's v2 signature and 16 KiB page
  zip-alignment. It is a debug-signed alpha APK, not a Play Store release.

No physical Moonraker or OctoPrint upload/start test was performed. Upload and
especially automatic start therefore remain an alpha feature that requires a
user confirmation and a fresh ready-state probe.

## 2026-08-18 pinned OrcaSlicer Mobile engine integration

Historical record: this section describes the former upstream APK artifact
set. It is superseded for version code 31 by the source-built NDK r28c,
no-OCCT engine documented in `orca-mobile-native-abi.md`.

Engine identity and packaging audit:

- OrcaSlicer Mobile source is pinned to commit
  `6fc2e14b9a222301f4432cee26d7ab37d3be86d0` and release 0.4.6.
- The fetched release APK is rejected unless its SHA-256 is
  `25bd3b72ff698b43991005f0df65ac57f67766ed4b240c48b8f3ec943eafbbdd`.
- The extracted ARM64 `libslic3r.so` SHA-256 is
  `d3462d2f6ba7612b4d3bd85a4608b1dba5b3b2a52c35f49905c2c4e25defcbcf`.
- The APK provides 55 ARM64 libraries. Feresa extracts the 54 release files
  other than `libc++_shared.so` and supplies NDK 27's C++ runtime.
- ELF symbol comparison found all 419 C++ runtime symbols referenced by the
  release native set in NDK 27's runtime (zero missing). This is an audit, not
  a substitute for device loading.

Configuration and JVM checks completed:

- `./gradlew -I /tmp/feresa-parity-builddir.gradle --no-watch-fs
  :app:testDebugUnitTest` passed after the model-workspace integration: 95
  tests, 0 failures, 0 errors, 0 skipped.
- `PrintSettingsState` currently contains 73 UI state fields, and its mapping
  test asserts 72 distinct Orca wire keys. It verifies that the explicit
  outer-wall speed becomes `outer_wall_speed` and that no unsupported
  `print_speed` key is emitted.
- Native option-definition tests confirm that runtime defaults are restricted
  to FFF/ANY options and that unsupported overlays fail instead of disappearing.
- Dynamic-config tests cover deterministic overlay precedence, full vector
  serialization, UTF-8/escape preservation, recursive profile inheritance,
  cycle detection, and live machine/filament overrides.
- JNI reflection tests verify the exact Java names and descriptors required by
  the pinned release ABI, including the headless shader-manager shim.
- Viewer parser tests cover real Orca feature comments, layer metadata,
  extrusion width/height/speed values, travel visibility, and progress-based
  toolpath filtering.
- `npm run test:viewer` passed with the multi-object viewer, full XYZ transform,
  object-selection lifecycle, G-code metadata, and renderer-recovery tests: 28
  tests, 0 failures.
- `./gradlew ... :app:lintDebug` completed with zero lint issues.

Device release gates completed on an ARM64 Android 16/API 36 emulator:

- `OrcaNativeParityInstrumentedTest` passed against the packaged ARM64 native
  engine: 6 tests, 0 failures. Its fixtures prove that wall count, shell count, sparse-infill density,
  support enablement, and outer-wall speed change generated G-code rather than
  only changing UI state.
- A two-object plate test proved printable extrusion in both separated model
  regions after plate composition. A second transform fixture proved that XYZ
  rotation and non-uniform scale reach the packaged Orca engine.
- The `0.12.0-alpha.1` APK installed and launched successfully. Android's
  document picker imported `parity_box.stl`; the WebGL view rendered it, and the
  floating View/Position/Slice menu exposed the real XYZ placement workspace.
- Empty-state and imported-model screenshots were visually checked at a
  1080 × 2400 mobile viewport.
- The release APK passed Android v2 signature verification and 16 KiB
  zip-alignment verification. Its SHA-256 is
  `a57eca5affaa1ee16ad25e5e4a92e2d9e516620439cd843707a8b1af416abea0`.
- Do not promote the APK to unattended or production printing based only on
  these checks or the ELF audit.

## 2026-08-17 Feresa Slicer repository split

- Renamed the Android namespace and application ID to
  `tech.g24.feresaslicer`.
- Renamed the JNI package bridge and native library to `feresa_slicer`.
- Viewer parser tests passed: 4 tests, 0 failures.
- Native host CMake build and CTest passed: 1 test, 0 failures.
- Android `assembleDebug` and `lintDebug` passed after the rename.
- Repository secret scan found no private keys, service tokens, passwords,
  signing material, personal profile cache, or machine-specific SDK paths.

## 2026-08-15 Android technical preview (historical contour-engine baseline)

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

This record predates the pinned OrcaSlicer Mobile `libslic3r` integration. The
small contour engine remains only as historical scaffolding and is no longer
the slicing path invoked by the Compose workflow. Its results must not be used
as evidence for the behavior of the current Orca-backed engine.

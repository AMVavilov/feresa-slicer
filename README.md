# Feresa Slicer for Android

Android-first, offline FDM slicing application. Feresa now sends its complete
print configuration to a pinned, headless Android build of OrcaSlicer
`libslic3r`; local slicing and the current read-only OrcaCloud profile import
are free, and the project does not include a proprietary subscription service.

Feresa Slicer is named after *Feresa attenuata*, the pygmy killer whale. It is
an independent project and is not affiliated with or endorsed by OrcaSlicer or
Bambu Lab.

## Download

[Download the latest tested Android APK](https://github.com/AMVavilov/feresa-slicer/releases).
This is an alpha development build intended for testing on ARM64 Android devices. APK files are
published as GitHub Release assets instead of being committed to the source repository.

## Current technical preview

- Kotlin and Jetpack Compose Android application.
- Real OrcaSlicer / `libslic3r` slicing through a source-built ARM64 JNI
  engine based on OrcaSlicer Mobile commit
  `6fc2e14b9a222301f4432cee26d7ab37d3be86d0`.
- Local STL, OBJ, and 3MF geometry import plus G-code export. OBJ and 3MF are converted offline to
  the validated binary-STL interchange used by the current mobile Orca engine.
- Offline Three.js preview for the model and generated extrusion paths.
- Multi-object plate with tap selection, XYZ move/rotation, linked or per-axis scale, duplicate,
  rename, centre, move-to-bed, lay-flat, collision warnings, and deterministic auto-arrange.
- Live 220 × 220 mm print-bed bounds check; slicing is blocked outside the bed.
- Searchable offline catalog generated from OrcaSlicer's bundled system
  profiles: 63 manufacturers and 908 printer/nozzle configurations. Selecting
  one applies its print volume, nozzle, G-code flavor and default process name.
- Optional OrcaCloud sign-in with Google or GitHub through the system browser.
- Moonraker/Klipper and OctoPrint connections can be entered manually or
  imported from OrcaCloud printer profiles (`host_type`, `print_host`, port and
  authentication settings). The Printer screen reports normalized server,
  printer, job, progress and temperature status when the host provides it.
- Network sending separates upload-only from upload-and-start. Starting is
  enabled only after a fresh status probe reports that the printer can accept a
  new job; a failed start never hides a successful upload.
- OAuth 2.0 PKCE with loopback callback. The refresh token, downloaded
  OrcaCloud profile cache, and saved manual printer credentials are encrypted
  at rest with separate AES-256-GCM keys held by Android Keystore.
- Five-part bottom navigation matching the slicer workflow: Model, Printer,
  Filament, Print, and App. After a model is selected, the Model workspace adds
  a floating View, Position, and Slice switcher above it.
- Orca-compatible print-settings editor with Quality, Strength, Support,
  Multimaterial and Others tabs, three visibility levels, collapsible groups,
  typed controls and read-only OrcaCloud profile import. Its 73 state fields
  produce 72 distinct Orca process wire keys: the legacy summary speed field
  and explicit outer-wall speed intentionally share one real Orca setting, and
  no unsupported `print_speed` key is invented.
- The Model/File workspace owns import and file information; View owns the
  print-bed scene, Position owns model transforms, and Slice owns slicing and
  the G-code preview. App contains theme, account, and synchronization controls.
- Local slicing does not require an account or cloud connection.
- No telemetry or proprietary Bambu plugin.

OrcaCloud authentication uses OrcaSlicer's public cloud client configuration.
The app never asks for or receives the user's Google/GitHub password. Profiles
are downloaded read-only from OrcaCloud after sign-in, cached for
offline viewing, and can be applied to the current project. The app does not
push, edit, or delete OrcaCloud data.

System printer presets and personal cloud profiles are intentionally separate:
the system catalog describes printer geometry and slicing defaults, while a
synced personal profile may additionally provide the physical printer's IP,
protocol and authentication required for network printing.

Before slicing, Feresa constructs one complete Orca INI in this order (later
values win): native FFF defaults, selected printer profile, selected process
profile, selected filament profile, current machine/filament values, and the
live Print-screen controls. OrcaCloud `inherits` chains are resolved before a
profile is applied. Every live key is checked against the option definition
reported by the same native engine, so an exposed setting cannot be silently
sent under an unknown name.

This is not full desktop OrcaSlicer parity. The current project flow accepts multiple
STL/OBJ/3MF geometry files on one plate and has one active filament; there is no
mobile multi-material object assignment, filament palette/AMS workflow, preservation of 3MF
project settings, desktop GUI, or slice cancellation. Multimaterial-related process
values can be stored in a profile, but they do not create a multi-filament
project by themselves. Network printer support is limited to Moonraker and
OctoPrint; there is no LAN discovery, camera, queue/file manager, or remote
pause/cancel control. Generated G-code must be reviewed before printing, and
the technical preview is not intended for unattended printing. The network
path has automated local-server coverage but has not been certified against a
physical printer.

## Build

Requirements:

- JDK 17
- Android SDK 36
- Android NDK 28.2.13676358 (r28c)
- CMake 3.22.1
- `curl`, `shasum`, and `unzip` for the pinned native-engine fetch
- Node.js 20+ only when rebuilding the bundled viewer

```bash
npm ci
npm run build:viewer
./gradlew :app:assembleDebug
```

Android `preBuild` runs `scripts/fetch-orca-mobile-engine.sh`. The script
downloads the immutable Feresa native-engine archive only when the local
checksum marker is missing, verifies the archive and shared-library SHA-256
values recorded in the script, and installs `libslic3r.so`, `libgmp.so`,
`libgmpxx.so`, and `libmpfr.so` under the ignored
`app/src/main/jniLibs/arm64-v8a` directory. Gradle/CMake supplies r28c's
`libc++_shared.so` for both native modules.

The engine is rebuilt from pinned OrcaSlicer Mobile commit
`6fc2e14b9a222301f4432cee26d7ab37d3be86d0` with NDK r28c and 16 KiB ELF page
alignment. The Android build excludes the OCCT-backed STEP import and
SVG/TextShape object-construction paths; Feresa's supported model imports are
STL, OBJ, and 3MF. OBJ and 3MF geometry is normalized to the validated STL
interchange before native slicing. A clean build needs network access to the
checksummed native-engine release archive.

To rebuild that archive from corresponding source on macOS, install NDK r28c
and run:

```bash
scripts/build-orca-mobile-engine-16kb.sh
```

The recipe pins every downloaded source and checksum, applies
`scripts/patches/orca-mobile-6fc2e14-no-occt-ndk28.patch`, and writes the
audited five-library archive plus checksum files below
`.native-build-16k/dist/`. Set `FERESA_NATIVE_BUILD_ROOT` to use a different
scratch directory; the application tree is not modified.

To refresh the compact printer catalog from an OrcaSlicer checkout:

```bash
python3 scripts/build_orca_printer_catalog.py \
  /path/to/OrcaSlicer/resources/profiles \
  app/src/main/assets/orca-printer-catalog.json \
  --commit "$(git -C /path/to/OrcaSlicer rev-parse HEAD)"
```

The generated viewer bundle is committed, so Android-only builds do not need
Node.js unless `web-src/viewer.js` changes.

## License

GNU Affero General Public License version 3. See `LICENSE`, `NOTICE.md`, and
`THIRD_PARTY_NOTICES.md`.

## Contributing

Development is organized through short-lived branches and pull requests. See
`CONTRIBUTING.md` before committing changes, especially the rules for keeping
printer credentials, local profiles, signing keys, and Android SDK paths out of
Git.

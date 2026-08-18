# Feresa Slicer for Android

Android-first, offline FDM slicing application. Feresa now sends its complete
print configuration to a pinned, headless Android build of OrcaSlicer
`libslic3r`; local slicing and the current read-only OrcaCloud profile import
are free, and the project does not include a proprietary subscription service.

Feresa Slicer is named after *Feresa attenuata*, the pygmy killer whale. It is
an independent project and is not affiliated with or endorsed by OrcaSlicer or
Bambu Lab.

## Download

[Download the latest tested Android APK](https://github.com/AMVavilov/feresa-slicer/releases/latest).
This is an alpha development build intended for testing on ARM64 Android devices. APK files are
published as GitHub Release assets instead of being committed to the source repository.

## Current technical preview

- Kotlin and Jetpack Compose Android application.
- Real OrcaSlicer / `libslic3r` slicing on ARM64 through the JNI ABI from
  OrcaSlicer Mobile 0.4.6, pinned to source commit
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
- OrcaCloud printer profiles import `host_type`, `print_host`, port and
  authentication settings. Moonraker/Klipper and OctoPrint profiles can receive
  the generated G-code and start printing after an explicit confirmation.
- OAuth 2.0 PKCE with loopback callback and Android Keystore-protected refresh token.
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
project by themselves. Generated G-code must be reviewed before printing, and
the technical preview is not intended for unattended printing.

## Build

Requirements:

- JDK 17
- Android SDK 35
- Android NDK 27.1.12297006 or compatible
- CMake 3.22.1
- `curl`, `shasum`, and `unzip` for the pinned native-engine fetch
- Node.js 20+ only when rebuilding the bundled viewer

```bash
npm ci
npm run build:viewer
./gradlew :app:assembleDebug
```

Android `preBuild` runs `scripts/fetch-orca-mobile-engine.sh`. The script
downloads OrcaSlicer Mobile 0.4.6 only when the local checksum marker is
missing, verifies the release APK SHA-256
`25bd3b72ff698b43991005f0df65ac57f67766ed4b240c48b8f3ec943eafbbdd`,
and installs its ARM64 native dependency set under the ignored
`app/src/main/jniLibs/arm64-v8a` directory. The extracted `libslic3r.so` has
SHA-256
`d3462d2f6ba7612b4d3bd85a4608b1dba5b3b2a52c35f49905c2c4e25defcbcf`.
Feresa supplies the compatible NDK 27 `libc++_shared.so`; the remaining native
libraries are the unmodified files from the checksummed release APK. A clean
build therefore needs network access to that pinned GitHub release.

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

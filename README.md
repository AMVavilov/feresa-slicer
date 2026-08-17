# Feresa Slicer for Android

Android-first, offline FDM slicing application. Local slicing and the current
read-only OrcaCloud profile import are free; the project does not include a
proprietary subscription service.

Feresa Slicer is named after *Feresa attenuata*, the pygmy killer whale. It is
an independent project and is not affiliated with or endorsed by OrcaSlicer or
Bambu Lab.

## Download

[Download the latest tested Android APK](releases/feresa-slicer-latest.apk).
This is a development build intended for testing on ARM64 Android devices.

## Current technical preview

- Kotlin and Jetpack Compose Android application.
- ARM64 C++17 slicing core connected through JNI.
- Local STL import and G-code export.
- Offline Three.js preview for the model and generated extrusion paths.
- Model placement, Z rotation, uniform scaling, centering, and camera controls.
- Live 220 × 220 mm print-bed bounds check; slicing is blocked outside the bed.
- Searchable offline catalog generated from OrcaSlicer's bundled system
  profiles: 63 manufacturers and 908 printer/nozzle configurations. Selecting
  one applies its print volume, nozzle, G-code flavor and default process name.
- Optional OrcaCloud sign-in with Google or GitHub through the system browser.
- OrcaCloud printer profiles import `host_type`, `print_host`, port and
  authentication settings. Moonraker/Klipper and OctoPrint profiles can receive
  the generated G-code and start printing after an explicit confirmation.
- OAuth 2.0 PKCE with loopback callback and Android Keystore-protected refresh token.
- Five-part top navigation matching the slicer workflow: File, Printer,
  Filament, Print, and App.
- Orca-compatible print-settings editor with Quality, Strength, Support,
  Multimaterial and Others tabs, three visibility levels, collapsible groups,
  typed controls and read-only OrcaCloud profile import.
- File contains model import, print-bed preview, arrangement and slicing; App contains settings, account and profile synchronization.
- Local slicing does not require an account or cloud connection.
- No telemetry or proprietary Bambu plugin.

OrcaCloud authentication uses OrcaSlicer's public cloud client configuration.
The app never asks for or receives the user's Google/GitHub password. Profile
profiles are downloaded read-only from OrcaCloud after sign-in, cached for
offline viewing, and can be applied to the current project. The app does not
push, edit, or delete OrcaCloud data.

System printer presets and personal cloud profiles are intentionally separate:
the system catalog describes printer geometry and slicing defaults, while a
synced personal profile may additionally provide the physical printer's IP,
protocol and authentication required for network printing.

The native core remains deliberately small while OrcaSlicer `libslic3r`
features are ported incrementally. It currently produces perimeters and
Orca-derived gyroid, rectilinear, line, and grid sparse infill. Unsupported
patterns fail explicitly instead of silently falling back. The technical
preview is not yet intended for unattended printing.

## Build

Requirements:

- JDK 17
- Android SDK 35
- Android NDK 27.1.12297006 or compatible
- CMake 3.22.1
- Node.js 20+ only when rebuilding the bundled viewer

```bash
npm ci
npm run build:viewer
./gradlew :app:assembleDebug
```

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

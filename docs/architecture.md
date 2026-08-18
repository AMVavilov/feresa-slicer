# Android architecture

## Product boundary

```text
┌─────────────────────────────────────────────────┐
│ Feresa Android application                       │
│                                                    │
│ Compose UI ──► complete Orca INI ──► JNI ABI     │
│      │                                  │         │
│      └─► offline Three.js preview      │         │
│                                         ▼         │
│ Local/system/OrcaCloud profiles ──► libslic3r.so  │
│                                                    │
│ Optional HTTPS: OrcaCloud pull and printer upload │
└─────────────────────────────────────────────────┘
```

Local slicing and local profiles do not depend on an account. OrcaCloud is an
optional read-only profile source; Feresa does not include a separate paid sync
backend, entitlement service, or remote slicer.

## OrcaCloud authentication

The app mirrors OrcaSlicer's public OAuth 2.0 PKCE flow. It opens the provider
page in the system browser, listens on `localhost` ports 41172–41174 for one
callback, verifies the random state, and exchanges the authorization code at
OrcaCloud's token endpoint. Access tokens stay in memory. Only the rotating
refresh token is persisted, encrypted with an app-specific AES-GCM key held by
Android Keystore. Application backup is disabled so the encrypted token is not
restored without its hardware-backed key.

After authentication, the app performs a read-only full pull from
`https://api.orcaslicer.com/api/v1/sync/pull`. The response is grouped into
machine, filament, and process profiles. Full profile JSON is cached in private
app storage for offline viewing; common slicer values can be applied to the
current project. This client does not call the push, force-push, or delete
routes, so it cannot modify the user's OrcaCloud data.

Machine profiles may also contain OrcaSlicer's print-host fields (`host_type`,
`print_host`, `printhost_port`, and authentication values). The Android client
uses these values only after an explicit send confirmation. Moonraker uploads
use `/server/files/upload` followed by `/printer/print/start`; OctoPrint uses
`/api/files/local` with the print action enabled. API keys are never shown in
the interface or logs.

## Configuration contract

The Print screen owns 73 state fields. They serialize to 72 distinct
OrcaSlicer process keys because the legacy `printSpeed` summary field and the
explicit outer-wall-speed control refer to `outer_wall_speed`; Feresa never
emits the nonexistent generic process key `print_speed`.

`OrcaDynamicPrintConfigBuilder` constructs the complete flat INI consumed by
native `model_slice`. Its overlay order is deterministic, and every later
source overrides earlier sources:

1. FFF defaults read at runtime from the pinned engine's `PrintConfigDef`;
2. selected printer preset, including recursively resolved printer ancestors;
3. selected process preset, including recursively resolved process ancestors;
4. selected filament preset, including recursively resolved filament ancestors;
5. live machine and single-filament values (printable area and height, nozzle,
   filament diameter, temperatures, and G-code flavor);
6. all 72 live process wire keys from the Print screen.

Profile arrays are serialized as complete comma-separated vectors, profile
metadata is excluded, inheritance cycles fail explicitly, and an exposed live
key missing from the native engine definition aborts configuration generation
instead of being silently ignored. The generated INI is UTF-8 and preserves
the native engine's already-serialized escape sequences.

## Preview and transform contract

The Compose screen owns the model transform and bed dimensions. It sends those
values to the bundled Three.js viewer and to the JNI slicing request. The
viewer parses the selected STL for interactive rendering and reports its
transformed bounds back to Compose. `OrcaNativeEngine` applies the same uniform
scale, Z rotation, on-bed placement, and XY translation to the native Orca
model before slicing.

The viewer is loaded from Android `WebViewAssetLoader`; STL and G-code are
served from app-local paths. It requires no network permission. Generated
G-code is parsed by layer and extrusion feature; the layer/toolpath sliders,
print/travel visibility, and line-type/width/layer-height/speed coloring operate
on parsed moves rather than placeholder geometry.

## Pinned native engine

Feresa uses the unmodified ARM64 slicing artifacts from OrcaSlicer Mobile 0.4.6:

- source commit:
  `6fc2e14b9a222301f4432cee26d7ab37d3be86d0`;
- release APK SHA-256:
  `25bd3b72ff698b43991005f0df65ac57f67766ed4b240c48b8f3ec943eafbbdd`;
- extracted `libslic3r.so` SHA-256:
  `d3462d2f6ba7612b4d3bd85a4608b1dba5b3b2a52c35f49905c2c4e25defcbcf`.

Gradle `preBuild` invokes `scripts/fetch-orca-mobile-engine.sh`, verifies the
release APK, and extracts 54 ARM64 shared libraries other than the release's
old `libc++_shared.so`. Feresa packages NDK 27's compatible C++ runtime. The
Java compatibility layer deliberately retains the upstream
`ru.ytkab0bp.slicebeam.slic3r` package and JNI descriptors, including a
headless zero-pointer `GLShadersManager` shim required by `JNI_OnLoad`.

Native `model_slice` loads the complete INI with forward-compatibility
substitution disabled, normalizes FFF vector settings, validates the config and
print, and writes G-code to the supplied app-private path. Slices are serialized
because the diagnostic SVG path is process-global. The release ABI reports
progress but does not expose cancellation; portable layer/time/statistic
summaries are derived from the generated G-code.

## Initial constraints

- ABI: `arm64-v8a`
- Minimum Android: API 28
- STL, OBJ, and 3MF geometry import; imported geometry is normalized to binary STL offline
- Multiple objects on one plate; XYZ/non-uniform transformed meshes are composed into one binary
  STL before native slicing because the pinned mobile JNI accepts one model file per slice
- One active filament; no object-to-filament assignment or AMS/palette workflow
- 3MF project settings are not restored; there is no project editor or native slice cancellation
- No desktop GUI, wxWidgets UI, device discovery, or proprietary Bambu plugin
- Not all desktop OrcaSlicer settings and workflows are exposed on mobile
- Output must be inspected before printing

## Upstream baseline

OrcaSlicer main commit used for the earlier desktop/headless port audit:

`d5dbd96dd64b830076c81053ed5fda26d5a1771b`

The production Android slicing ABI described above is instead pinned to the
OrcaSlicer Mobile commit and checksums in the preceding section. See
`docs/orca-mobile-native-abi.md` and `THIRD_PARTY_NOTICES.md` for the complete
ABI and licensing record.

# Android architecture

## Product boundary

```text
AGPL Android application              Independent sync service
┌──────────────────────────────┐      ┌──────────────────────────┐
│ Compose UI                   │      │ Accounts and entitlement │
│ Offline Three.js WebView     │      │ Encrypted profile store  │
│ Local profile repository     │ HTTPS│ Version history          │
│ JNI C API                    ├─────►│ Collaboration             │
│ Native slicing engine        │      │ Billing webhooks         │
└──────────────────────────────┘      └──────────────────────────┘
```

Local slicing and local profiles do not depend on an account. OrcaCloud login
is optional and currently establishes an authenticated session only; profile
download is the next integration boundary. The future independent paid sync
service exchanges versioned profile documents and never performs slicing.

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

## Preview and transform contract

The Compose screen owns the model transform and bed dimensions. It sends those
values to the bundled Three.js viewer and to the JNI slicing request. The
viewer parses the selected STL for interactive rendering and reports its
transformed bounds back to Compose. The C++ core independently applies the same
scale, Z rotation, and XY placement before generating G-code, then repeats the
bed check as a safety boundary.

The viewer is loaded from Android `WebViewAssetLoader`; STL and G-code are
served from app-local paths. It requires no network permission. Generated
extrusion moves are parsed into line segments for an on-device toolpath view.

## Native migration path

1. Prove STL import, layer generation, JNI, and ARM64 packaging with the small
   dependency-free contour engine in this repository.
2. Define a stable C boundary for model loading, profiles, progress, cancel,
   slicing, statistics, and G-code preview.
3. Port the minimum headless OrcaSlicer `libslic3r` dependency graph to Android
   NDK. Desktop wxWidgets, OpenGL GUI, Python runtime, device discovery, and the
   proprietary Bambu networking plugin stay excluded.
4. Compare reference models against the pinned desktop OrcaSlicer build before
   enabling production printing.

## Initial constraints

- ABI: `arm64-v8a`
- Minimum Android: API 28
- STL only in the technical preview
- One object and one plate
- Perimeters only; no infill, support, or retraction tuning yet
- Output must be inspected before printing

## Upstream baseline

OrcaSlicer main commit used for the initial port audit:

`d5dbd96dd64b830076c81053ed5fda26d5a1771b`

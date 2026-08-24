# Feresa / Orca feature-parity audit

Audit date: 2026-08-18. This document describes the current Android working tree, not a marketing
roadmap. The desktop comparison target is OrcaSlicer commit
`d5dbd96dd64b830076c81053ed5fda26d5a1771b`; the production Android slicing binary is the
source-built, checksum-verified NDK r28c engine based on OrcaSlicer Mobile commit
`6fc2e14b9a222301f4432cee26d7ab37d3be86d0`
(`architecture.md#pinned-native-engine`). Those are different baselines: a control can be
valid for the mobile native ABI while the surrounding desktop workflow is still absent.

## Status definitions

- **WORKING**: connected to production state and an actual implementation; not a visual stub.
- **PARTIAL**: useful implementation exists, but materially differs from or covers less than the
  corresponding desktop Orca workflow.
- **MISSING**: present in desktop Orca and not implemented in the Android product.
- **HIDDEN**: intentionally not shown because the current product/native contract cannot make it
  work correctly.

## Device evidence for the real Orca engine

`OrcaNativeParityInstrumentedTest` loads the exact packaged ARM64 `libslic3r.so`; it does not use a
fake slicer (`../app/src/androidTest/java/tech/g24/feresaslicer/slicer/OrcaNativeParityInstrumentedTest.kt:17-34`).
The latest emulator run completed 5/5 tests on API 36:

```text
./gradlew -I /tmp/feresa-parity-builddir.gradle --no-watch-fs \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=tech.g24.feresaslicer.slicer.OrcaNativeParityInstrumentedTest

Finished 5 tests on Medium_Phone_API_36.0(AVD) - 16
BUILD SUCCESSFUL
```

Observed G-code differences prove that these controls affect native slicing:

| Contract | Device result |
| --- | --- |
| Process wire contract | all 72 serialized state keys exist in native FFF `PrintConfigDef` |
| Wall loops 2 -> 5 | inner-wall path 2933.136 -> 11022.960 mm |
| Top/bottom shells 1 -> 6 | solid-feature path 2669.620 -> 9969.983 mm |
| Sparse infill 20% -> 40% | sparse-infill path 7447.246 -> 13239.463 mm |
| Supports off -> on | support path 0 -> 1326.981 mm plus 694.540 mm interface |
| Outer wall speed 10 -> 20 mm/s | dominant outer-wall feed `F600` -> `F1200` |
| Two separated STL objects | composed STL retained 24 triangles; G-code had printable extrusion in both X=39..61 and X=159..181 zones |

The comparative assertions are at
`../app/src/androidTest/java/tech/g24/feresaslicer/slicer/OrcaNativeParityInstrumentedTest.kt:37-164`.
The last case is important: it proves that the multi-object UI is not only visual; both meshes reach
the native Orca toolpath.

## Model screen

| Status | User function | Evidence and boundary |
| --- | --- | --- |
| **WORKING** | Import STL, OBJ, and 3MF geometry | The offline importer validates the extension, parses binary/ASCII STL, OBJ polygons (including negative indices), and 3MF units/components/build transforms, then atomically converts the result to normalized binary STL (`../app/src/main/java/tech/g24/feresaslicer/modelimport/ModelFileImporter.kt`). File metadata and real mesh bounds are retained for the Model/File UI. |
| **WORKING** | Multiple objects on one plate | Each object has a stable ID; add, remove, select and transform are immutable domain operations (`../app/src/main/java/tech/g24/feresaslicer/plate/PlateWorkspace.kt:186-288`). The File section lists every object and supports selection/removal (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:701-779`). |
| **WORKING** | Actual multi-object 3D view and tap selection | Compose sends every local STL and transform to the WebView (`../app/src/main/java/tech/g24/feresaslicer/ui/ModelViewer.kt:331-427`); Three.js loads every mesh, reports aggregate/per-object bounds, and ray-picks the selected object (`../web-src/viewer.js:274-318`, `:357-427`, `:585-598`). |
| **WORKING** | XYZ position/rotation, linked or non-uniform scale, centre, move to bed, rotate 90 degrees | The visible controls mutate the selected object's real transform and invalidate stale G-code. Viewer and STL composer both use the same scale -> Rx -> Ry -> Rz -> translation convention, with contract tests covering the matrix and transformed bounds. |
| **WORKING** | Duplicate, rename, collision warnings, auto-arrange, and lay flat | Operations are immutable domain mutations with stable object IDs. Auto-arrange is deterministic shelf packing inside the active build volume; collisions use conservative transformed AABBs; lay-flat uses the largest valid STL triangle and then corrects the exact lowest Z to the bed (`../app/src/main/java/tech/g24/feresaslicer/plate/PlateWorkspace.kt`, `../app/src/main/java/tech/g24/feresaslicer/slicer/StlPlateComposer.kt`). |
| **WORKING** | Build-volume validation before slicing | Validation covers left/right/front/back/below-bed/maximum-height for every object (`../app/src/main/java/tech/g24/feresaslicer/plate/PlateWorkspace.kt:290-312`) and blocks a slice outside the volume (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:461-470`). |
| **WORKING** | Slice all visible objects | Because the pinned JNI accepts one model file per slice, Feresa composes transformed meshes into one binary STL (`../app/src/main/java/tech/g24/feresaslicer/slicer/StlPlateComposer.kt:56-63`, `:110-165`) and passes that file to native Orca (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:533-560`). The two-zone device test above proves both objects print. |
| **PARTIAL** | Advanced object manipulation | Core mobile transforms, clone, arrange, collision reporting and lay-flat work. Split, cut, repair, mesh boolean, per-object settings, support/seam/fuzzy/MMU painting, emboss/SVG, measure, assembly, simplify and object-to-filament assignment remain absent. Desktop Orca exposes those workflows (`../external/orcaslicer/src/slic3r/GUI/Gizmos/GLGizmosManager.cpp:202-221`). |
| **PARTIAL** | Plate management | The Android domain supports multiple objects but only one plate. Desktop has per-plate select/delete/arrange and multiple-plate workflows (`../external/orcaslicer/src/slic3r/GUI/GUI_Factories.cpp:1702-1745`). |
| **PARTIAL** | Orca project/file workflows | OBJ and 3MF geometry import works, including 3MF build transforms, but 3MF project-embedded printer/process/filament settings are not restored. STEP, AMF, SVG and desktop project lifecycle remain absent; desktop includes the broader STL/STEP/AMF/3MF workflow (`../external/orcaslicer/src/slic3r/GUI/Plater.cpp:63-70`). |

Composing meshes is a correct one-plate slicing workaround, but native Orca then sees one mesh.
Original mobile object IDs therefore cannot drive desktop-style by-object sequence, per-object
settings, object labels, or object exclusion in the generated G-code.

## Print settings screen

| Status | User function | Evidence and boundary |
| --- | --- | --- |
| **WORKING** | Orca-backed configuration, not a placeholder form | State hydrates from resolved Orca keys (`../app/src/main/java/tech/g24/feresaslicer/ui/PrintSettingsPanel.kt:131-223`), serializes to 72 native wire keys (`../app/src/main/java/tech/g24/feresaslicer/ui/PrintSettingsOrcaPayload.kt:6-83`), validates types/ranges/enums against the packaged engine (`../app/src/main/java/tech/g24/feresaslicer/slicer/OrcaProcessSettingsValidator.kt:7-93`), and overlays native defaults plus printer/process/filament/live values into the INI (`../app/src/main/java/tech/g24/feresaslicer/slicer/OrcaDynamicPrintConfigBuilder.kt:107-186`). |
| **WORKING** | Profile dropdown, Basic/Advanced/Expert detail, Orca-like categories and accordions | The profile/detail row is wired at `../app/src/main/java/tech/g24/feresaslicer/ui/PrintSettingsPanel.kt:242-339`; visible categories and real setting panels are selected at `:341-433` and rendered through interactive accordions at `:522-612`. |
| **WORKING** | Core print effects verified on device | Walls, top/bottom shells, infill, support toggle and outer-wall speed all change real native G-code; see the device evidence and assertions at `../app/src/androidTest/java/tech/g24/feresaslicer/slicer/OrcaNativeParityInstrumentedTest.kt:37-120`. |
| **WORKING** | Stale-output safety for edits and profile changes | Any visible process, nozzle, filament or temperature edit clears generated G-code; applying cloud/system profiles also invalidates it. A slice-generation token additionally discards a native result if the plate or settings changed while slicing was still running (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt`). |
| **PARTIAL** | Desktop setting breadth | The 72-key Android state is a useful subset, not desktop Orca's complete setting schema. Only five high-risk groups have device-level effect assertions; all 72 are schema-supported, but the remaining controls are not yet individually regression-tested. |
| **PARTIAL** | Orca dependency logic | Feresa now gates support/tree controls by their active modes and hides thin-wall detection under Arachne (`../app/src/main/java/tech/g24/feresaslicer/ui/PrintSettingsPanel.kt:413-460`). It still does not reproduce every desktop coupled-value transaction, so spiral vase remains hidden instead of sending an unsafe partial configuration. Desktop dependency logic is at `../external/orcaslicer/src/slic3r/GUI/ConfigManipulation.cpp:418-450`, `:873-907`, and `:1044-1048`. |
| **PARTIAL** | Preset lifecycle | Cloud presets can be selected and edited values affect the current slice, but there is no Save As, overwrite/reset, local user-preset library, compatibility filter or project-embedded preset. Desktop preset save/overwrite/project logic is materially broader (`../external/orcaslicer/src/slic3r/GUI/SavePresetDialog.cpp:179-265`). |
| **HIDDEN** | Multi-material/prime-tower UI | The category is intentionally filtered because JNI is currently called with one active filament and there is no palette/object assignment workflow (`../app/src/main/java/tech/g24/feresaslicer/ui/PrintSettingsPanel.kt:341-352`, `orca-mobile-native-abi.md:42-45`). It must stay hidden until a complete multi-filament contract exists. |
| **HIDDEN** | By-object controls | `print_sequence`, `gcode_label_objects` and `exclude_object` exist in the serialized state, but are not rendered as Android controls. They must remain hidden while multiple imported objects are flattened into one composed STL (`../app/src/main/java/tech/g24/feresaslicer/ui/PrintSettingsOrcaPayload.kt:75-82`, `../app/src/main/java/tech/g24/feresaslicer/slicer/StlPlateComposer.kt:56-63`). |

The 72-key device assertion proves ABI compatibility, not full desktop parity and not that 72 controls
are simultaneously visible. Detail level and the intentional hidden categories determine what the
user can edit.

## Slice and preview screen

| Status | User function | Evidence and boundary |
| --- | --- | --- |
| **WORKING** | Real Orca slicing | The screen builds a complete INI and calls `OrcaNativeEngine`, which opens the model with pinned JNI, applies transforms, calls native `model_slice`, and requires a non-empty output file (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:461-580`, `../app/src/main/java/tech/g24/feresaslicer/slicer/OrcaNativeEngine.kt:16-94`). |
| **WORKING** | Actual toolpath view | G-code is parsed into extrusion/travel segments and rendered as Three.js line segments, not synthetic model outlines (`../web-src/toolpath-parser.mjs:250-325`, `../web-src/viewer.js:502-569`). G2/G3 arcs are interpolated before rendering (`../web-src/toolpath-parser.mjs:250-313`). |
| **WORKING** | Layer range, sequential progress and selected move facts | Vertical layer range and horizontal segment progress feed the viewer (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:2448-2557`); the viewer returns actual X/Y/Z, speed, line type, line width and layer height (`../app/src/main/java/tech/g24/feresaslicer/ui/ModelViewer.kt:75-113`, `:448-468`). |
| **WORKING** | Four color modes and extrusion/travel visibility | Line width, line type, speed and layer height are real modes (`../app/src/main/java/tech/g24/feresaslicer/ui/ModelViewer.kt:121-126`); the screen exposes extrusion/travel switches (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:2405-2446`). |
| **WORKING** | G-code window, save and send actions | The preview shows source commands around the selected move and exposes Save and Print (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:2559-2614`). |
| **PARTIAL** | Preview classification breadth | Desktop Orca exposes summary, filament, actual speed, acceleration, jerk, flow, actual flow, layer time, fan, temperature and pressure advance in addition to the four Android modes (`../external/orcaslicer/src/slic3r/GUI/GCodeViewer.cpp:66-103`, `:1074-1117`). Desktop also filters retract, unretract, wipe and seam; Android currently has extrusion and travel only. |
| **PARTIAL** | Statistics | The pinned native ABI does not expose layer count or total time, so Feresa derives portable summaries from generated G-code (`orca-mobile-native-abi.md:68-77`). These are useful estimates, not the complete desktop statistics model. |
| **MISSING** | Slice cancellation | The release JNI has progress callbacks but no cancellation entry point (`orca-mobile-native-abi.md:68-77`). |

## Printer screen

| Status | User function | Evidence and boundary |
| --- | --- | --- |
| **WORKING** | Load connection details from an Orca printer profile | `print_host`, host type, port, API key and username/password are parsed from the selected profile (`../app/src/main/java/tech/g24/feresaslicer/auth/OrcaPrinterConnection.kt:29-51`). A profile connection and a manually saved connection are intentionally separate, and the UI identifies which source is active (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:375-405`, `:2207-2244`). |
| **WORKING** | Manual Moonraker/OctoPrint connection | The editor accepts printer name, HTTP/HTTPS host, optional port, protocol, API key and complete HTTP Basic credentials. It rejects invalid ports, URL user-info/path/query/fragment and partial Basic credentials (`../app/src/main/java/tech/g24/feresaslicer/printer/ManualPrinterConnectionStore.kt:11-52`, `:127-145`). The single saved manual connection can be edited, activated or deleted and is encrypted with an app-specific Android Keystore AES-GCM key (`:56-95`, `../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:2201-2411`). |
| **WORKING** | Typed, side-effect-free status probe | Moonraker identity and object queries and OctoPrint version/printer/job queries are schema-checked and normalized into printer state, job/file, progress, elapsed/remaining time, tool/bed temperatures, server versions and warnings (`../app/src/main/java/tech/g24/feresaslicer/printer/PrinterConnectionService.kt:17-113`, `:151-212`, `:364-521`). Authentication, network, HTTP, malformed-response and wrong-server failures are distinct; redirects are not followed. The UI renders the typed result rather than treating any JSON response as success (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:2224-2235`, `:2521-2590`). |
| **WORKING** | Upload-only and guarded start are separate actions | Both protocols first upload without starting; a second request starts the exact path returned by the host (`../app/src/main/java/tech/g24/feresaslicer/printer/NetworkPrinterClient.kt:42-134`). The confirmation dialog performs a fresh status probe, keeps upload-only available for a reachable host, and enables immediate start only when `canStart` is true. If upload succeeds but start fails, the UI reports the partial success (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:1741-1879`). |
| **WORKING** | Stale G-code cannot be sent | Slice output carries the generation that produced it; model, transform, profile or print-setting changes invalidate the active artifact. Save, preview and printer-send paths only receive an artifact whose generation still matches the current project (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:162-166`, `:249-320`, `:518-683`, `:1761-1842`). Generation-specific output filenames also prevent an older in-flight slice from overwriting the current file. |
| **PARTIAL** | Verification level | Local fake-HTTP tests cover both status schemas, offline/startup states, authentication headers, redirect refusal, fixed-length multipart upload, returned-path validation, and upload/start separation (`../app/src/test/java/tech/g24/feresaslicer/printer/PrinterConnectionServiceTest.kt`). Android instrumentation covers encrypted manual credentials and encrypted cloud-profile cache (`../app/src/androidTest/java/tech/g24/feresaslicer/auth/PrinterStorageInstrumentedTest.kt`). This audit did not upload to or start a physical Moonraker/OctoPrint printer; live-printer integration remains required before release certification. |
| **PARTIAL** | Remote printer management | Connection editing and read-only live status work, but there is no LAN discovery, camera, remote file/queue manager, or pause/resume/cancel and temperature-control commands. Only one manual connection is stored. |
| **MISSING** | Other desktop hosts/device plugins | PrusaLink and unknown protocols are explicitly rejected by the status/send contracts (`../app/src/main/java/tech/g24/feresaslicer/printer/PrinterConnectionService.kt:124-149`, `../app/src/main/java/tech/g24/feresaslicer/printer/NetworkPrinterClient.kt:51-90`). Proprietary Bambu device integration is not included. |

## Profiles and OrcaCloud

| Status | User function | Evidence and boundary |
| --- | --- | --- |
| **WORKING** | Google/GitHub sign-in with OAuth PKCE | The app opens the browser, verifies random state and exchanges the code (`../app/src/main/java/tech/g24/feresaslicer/auth/OrcaAuthViewModel.kt:35-70`, `../app/src/main/java/tech/g24/feresaslicer/auth/OrcaCloudAuthClient.kt:26-64`). |
| **WORKING** | Secure persisted session | The refresh token is persisted using AES-256-GCM with Android Keystore (`../app/src/main/java/tech/g24/feresaslicer/auth/AndroidKeystoreAesGcm.kt`, `../app/src/main/java/tech/g24/feresaslicer/auth/EncryptedRefreshTokenStore.kt`); session restore/refresh and sign-out are implemented (`../app/src/main/java/tech/g24/feresaslicer/auth/OrcaAuthViewModel.kt:86-159`). |
| **WORKING** | Read-only cloud pull and offline cache | The only profile sync endpoint is `/api/v1/sync/pull` (`../app/src/main/java/tech/g24/feresaslicer/auth/OrcaCloudAuthClient.kt:66-105`); results are cached and restored offline (`../app/src/main/java/tech/g24/feresaslicer/auth/OrcaAuthViewModel.kt:99-129`, `:162-214`). The UI accurately says it does not mutate OrcaCloud (`../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt:1253-1289`). |
| **WORKING** | Apply complete inherited profiles | Missing, ambiguous and cyclic profile parents are explicit errors (`../app/src/main/java/tech/g24/feresaslicer/slicer/OrcaProfileSettingsResolver.kt:6-60`). Cloud diffs can inherit bundled system presets, which are flattened before native config construction (`../app/src/main/java/tech/g24/feresaslicer/slicer/OrcaSystemPresetCatalog.kt:24-94`, `:104-168`). |
| **WORKING** | Offline printer catalog | The app verifies 64 bundled Orca INI bundles and provides searchable compatible printer selection. Loading, parsing and compatibility filtering run off the UI thread; indexed `(type, name)` lookup avoids rescanning every preset for each printer (`../app/src/main/java/tech/g24/feresaslicer/slicer/OrcaSystemPresetCatalog.kt`, `../app/src/main/java/tech/g24/feresaslicer/ui/FeresaSlicerApp.kt`). Cloud lists have an explicit Show all/Shrink action. |
| **PARTIAL** | Local profile product | System printers are browsable offline, but there is no equivalent offline filament/process browser or user-created local preset lifecycle. Edits live in current app state only. |
| **WORKING** | Encrypted cloud-profile cache | The full pulled profile JSON is stored in an AES-256-GCM envelope under a separate Android Keystore alias. A legacy app-private plaintext cache is migrated on read and deleted; normal writes and clear also remove it (`../app/src/main/java/tech/g24/feresaslicer/auth/OrcaProfileCache.kt:15-93`). This protects data at rest inside the app sandbox; it does not make HTTP printer credentials safe in transit. |
| **MISSING** | Two-way OrcaCloud sync | Feresa has no push, force-push, delete or conflict resolution. Desktop Orca implements conflict choices and periodic print/filament/printer push/delete synchronization (`../external/orcaslicer/src/slic3r/GUI/GUI_App.cpp:5471-5554`, `:7392-7466`). |
| **HIDDEN** | Cloud write actions | Save/push/delete controls are correctly absent because the Android client is explicitly read-only. They must not be presented as working until write APIs, conflict handling and data-loss tests exist. |

## Dependency rules enforced after the audit

The audit found three controls that could mislead users even though the native keys existed. They
are now handled conservatively in the production UI:

1. **Spiral vase is hidden** until Feresa can apply Orca's complete atomic dependency transaction
   (one wall, zero top shells/infill/support, no thin walls and the remaining coupled values).
2. **Detect thin walls is hidden under Arachne**, matching desktop Orca's enablement rule.
3. **Support-dependent controls appear only when support is on**, and tree branch controls appear
   only for a tree support type at Expert detail.

No currently visible action found in this audit is a pure click-only stub. Items marked **PARTIAL**
above must still not be described as full desktop Orca parity. The next parity milestone is to add
device-effect fixtures for the remaining visible process groups before expanding the settings
surface.

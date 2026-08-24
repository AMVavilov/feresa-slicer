# OrcaSlicer Mobile native ABI audit

Feresa's headless JNI bridge is pinned to CodeMasterCody3D/OrcaSlicer-Mobile
commit `6fc2e14b9a222301f4432cee26d7ab37d3be86d0`. The ARM64 engine is rebuilt
from that source with Android NDK 28.2.13676358 (r28c), 16 KiB ELF page
alignment, and the Feresa no-OCCT patch. The immutable archive and individual
shared-library SHA-256 values are pinned in
`scripts/fetch-orca-mobile-engine.sh`.

`scripts/build-orca-mobile-engine-16kb.sh` is the corresponding-source recipe
for the published archive. It pins the source archives and revisions, applies
`scripts/patches/orca-mobile-6fc2e14-no-occt-ndk28.patch`, builds with r28c,
and audits the JNI export count, dynamic-library closure, SONAMEs, and 16 KiB
ELF `LOAD` alignment before packaging the exact five-file archive.

The audit reads source objects through `git show <commit>:<path>`; it does not rely on the moving
checkout at `external/orcaslicer-mobile-port`.

## Headless JNI surface

The minimum slice path exported by `app/src/main/jni/slicebeam/beam_native.cpp` is:

- `Native.get_print_config_def(PrintConfigDef): void`
- `Native.set_svg_path_prefix(String): void`
- `Native.model_read_from_file(String, String, int): long`
- `Native.model_get_objects_count(long): int`
- `Native.model_get_bounding_box_exact_global(long): double[]`
- `Native.model_translate(long, int, double, double, double): void`
- `Native.model_scale(long, int, double, double, double): void`
- `Native.model_rotate(long, int, double, double, double): void` (radians)
- `Native.model_ensure_on_bed(long, int): void`
- `Native.model_translate_global(long, double, double, double): void`
- `Native.model_slice(long, String, String, SliceListener, int, int[], int, double, double,
  double): long`
- `Native.model_release(long): void`
- `Native.gcoderesult_get_recommended_name(long): String`
- `Native.gcoderesult_get_used_filament_mm(long, int): double`
- `Native.gcoderesult_get_used_filament_g(long, int): double`
- `Native.gcoderesult_release(long): void`

`model_slice` loads the complete INI with
`DynamicPrintConfig::load(configPath, ForwardCompatibilitySubstitutionRule::Disable)`, normalizes
FFF settings, validates the config and print, and writes G-code directly to the supplied path.

The source-built engine's `JNI_OnLoad` also resolves these class/method
descriptors before any slice:

- `ru/ytkab0bp/slicebeam/slic3r/SliceListener.onProgress(int, String): void`
- `ru/ytkab0bp/slicebeam/slic3r/GLShadersManager.getCurrentShaderPointer(): long`

The latter is an OpenGL-era coupling. Feresa supplies a headless zero-pointer
shim so the pinned JNI implementation can load without importing its
desktop/viewer Java layer.

Passing `numFilaments = 1` and `filamentColors = null` is valid. The native code dereferences the
color array only inside `if (colorsArr != nullptr)`. If the INI itself declares multiple filaments,
the engine raises `numFilaments` from the config and normalizes vector sizes while leaving the null
palette untouched.

## Shared-library closure (ARM64)

The source-built engine's non-system runtime closure is intentionally limited
to:

- `libslic3r.so`, `libc++_shared.so`;
- `libgmp.so`, `libgmpxx.so`, and `libmpfr.so`.

Feresa packages the same r28c `libc++_shared.so` used to link the engine and its
own JNI module. The no-OCCT build has no `libTK*.so` dependency. It excludes
the OCCT-backed STEP import and SVG/TextShape object-construction paths; the
application imports STL, OBJ, and 3MF, normalizing OBJ/3MF geometry to STL
before slicing. Diagnostic SVG output used by the slicer is separate from the
excluded OCCT-backed object-construction path.

The final AAB gate checks every packaged ELF `LOAD` alignment, BundleConfig
16 KiB alignment, APK zip alignment, the exact native archive checksums, and
on-device `Native.isLoaded()` plus real slicing. Static ELF inspection is not
a substitute for that device smoke test.

## Known native API constraints

- G-code result filament getters accept an extrusion-role index. Native code assumes that the
  role exists in its statistics map, so callers must not probe arbitrary/absent roles.
- The pinned API has no cancellation entry point for `model_slice`.
- The listener is invoked from native worker threads and must be non-null.
- The native library does not expose layer count or total estimated time directly on the result;
  Feresa derives portable summary values from the generated G-code.
- SVG diagnostic paths are global. Feresa serializes slices and sets `set_svg_path_prefix` to the
  writable G-code output directory before model processing.

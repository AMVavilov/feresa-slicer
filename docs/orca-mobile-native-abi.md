# OrcaSlicer Mobile native ABI audit

Feresa's headless JNI bridge is pinned to CodeMasterCody3D/OrcaSlicer-Mobile commit
`6fc2e14b9a222301f4432cee26d7ab37d3be86d0` and release `0.4.6`. The audited APK is
`OrcaSlicerMobile_6fc2e14b9a.apk`, SHA-256
`25bd3b72ff698b43991005f0df65ac57f67766ed4b240c48b8f3ec943eafbbdd`.

The audit reads source objects through `git show <commit>:<path>`; it does not rely on the moving
checkout at `external/orcaslicer-mobile-port`.

## Headless JNI surface

The minimum slice path exported by `app/src/main/jni/slicebeam/beam_native.cpp` is:

- `Native.model_read_from_file(String, String, int): long`
- `Native.model_get_objects_count(long): int`
- `Native.model_get_bounding_box_exact_global(long): double[]`
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

The release `JNI_OnLoad` also resolves these class/method descriptors before any slice:

- `ru/ytkab0bp/slicebeam/slic3r/SliceListener.onProgress(int, String): void`
- `ru/ytkab0bp/slicebeam/slic3r/GLShadersManager.getCurrentShaderPointer(): long`

The latter is an OpenGL-era coupling. Feresa supplies a headless zero-pointer shim so the exact
release library can load without importing its desktop/viewer Java layer.

Passing `numFilaments = 1` and `filamentColors = null` is valid. The native code dereferences the
color array only inside `if (colorsArr != nullptr)`. If the INI itself declares multiple filaments,
the engine raises `numFilaments` from the config and normalizes vector sizes while leaving the null
palette untouched.

## Shared-library closure (ARM64)

The release APK contains 55 ARM64 shared libraries. ELF `DT_NEEDED` traversal from
`libslic3r.so` reaches this non-system closure:

- `libslic3r.so`, `libc++_shared.so`
- `libgmp.so`, `libgmpxx.so`, `libmpfr.so`
- `libTKDESTEP.so`, `libTKXCAF.so`, `libTKCAF.so`, `libTKLCAF.so`, `libTKCDF.so`
- `libTKV3d.so`, `libTKMesh.so`, `libTKXMesh.so`, `libTKBO.so`, `libTKPrim.so`
- `libTKHLR.so`, `libTKShHealing.so`, `libTKTopAlgo.so`, `libTKGeomAlgo.so`
- `libTKGeomBase.so`, `libTKBRep.so`, `libTKG3d.so`, `libTKG2d.so`, `libTKMath.so`
- `libTKernel.so`, `libTKDE.so`, `libTKXSBase.so`, `libTKVCAF.so`, `libTKService.so`

System dependencies are `libm`, `libdl`, `liblog`, `libEGL`, `libGLESv3` and `libc`.

Feresa packages NDK 27's `libc++_shared.so` because its own native module is built with NDK 27.
The release APK's NDK 23 libc++ exports 2,276 dynamic symbols; the NDK 27 runtime exports 2,336.
All 419 release-libc++ symbols referenced by the Orca/OCCT/GMP shared-library set are present in
NDK 27 (zero missing symbols). Emulator loading is still required after every engine/runtime
update; this symbol audit is not a substitute for a device smoke test.

## Known native API constraints

- G-code result filament getters accept an extrusion-role index. Native code assumes that the
  role exists in its statistics map, so callers must not probe arbitrary/absent roles.
- The release API has no cancellation entry point for `model_slice`.
- The listener is invoked from native worker threads and must be non-null.
- The native library does not expose layer count or total estimated time directly on the result;
  Feresa derives portable summary values from the generated G-code.
- SVG diagnostic paths are global. Feresa serializes slices and sets `set_svg_path_prefix` to the
  writable G-code output directory before model processing.

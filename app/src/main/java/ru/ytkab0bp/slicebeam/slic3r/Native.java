// SPDX-License-Identifier: AGPL-3.0-only
package ru.ytkab0bp.slicebeam.slic3r;

/**
 * Minimal JVM ABI for the native OrcaSlicer Mobile engine at commit
 * 6fc2e14b9a222301f4432cee26d7ab37d3be86d0.
 *
 * <p>The method names and descriptors below intentionally match the exported JNI symbols in
 * {@code beam_native.cpp}. Keep them stable when the native artifacts are introduced. The loader
 * is tolerant of absent artifacts so JVM tests and source-only builds remain usable.</p>
 */
public final class Native {
    private static final String[] LOAD_ORDER = {
            "c++_shared",
            "gmp",
            "gmpxx",
            "mpfr",
            "TKDESTEP",
            "TKXCAF",
            "TKLCAF",
            "TKCAF",
            "TKCDF",
            "TKV3d",
            "TKMesh",
            "TKXMesh",
            "TKBO",
            "TKPrim",
            "TKHLR",
            "TKShHealing",
            "TKTopAlgo",
            "TKGeomAlgo",
            "TKGeomBase",
            "TKBRep",
            "TKG3d",
            "TKG2d",
            "TKMath",
            "TKernel",
            "TKDE",
            "slic3r",
    };

    private static final boolean LOADED;
    private static final Throwable LOAD_ERROR;

    static {
        boolean loaded = false;
        Throwable error = null;
        try {
            for (String library : LOAD_ORDER) {
                System.loadLibrary(library);
            }
            loaded = true;
        } catch (UnsatisfiedLinkError | SecurityException failure) {
            error = failure;
        }
        LOADED = loaded;
        LOAD_ERROR = error;
    }

    private Native() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static Throwable getLoadError() {
        return LOAD_ERROR;
    }

    public static native void get_print_config_def(PrintConfigDef def);

    public static native void set_svg_path_prefix(String prefix);

    public static native long model_read_from_file(
            String path,
            String baseName,
            int plateId
    ) throws Slic3rRuntimeError;

    static native int model_get_objects_count(long ptr);

    static native double[] model_get_bounding_box_exact_global(long ptr);

    static native void model_translate(long ptr, int objectIndex, double x, double y, double z);

    static native void model_translate_global(long ptr, double x, double y, double z);

    static native void model_ensure_on_bed(long ptr, int objectIndex);

    static native void model_scale(long ptr, int objectIndex, double x, double y, double z);

    static native void model_rotate(long ptr, int objectIndex, double x, double y, double z);

    static native long model_slice(
            long ptr,
            String configPath,
            String gcodePath,
            SliceListener listener,
            int numFilaments,
            int[] filamentColors,
            int calibrationMode,
            double calibrationStart,
            double calibrationEnd,
            double calibrationStep
    ) throws Slic3rRuntimeError;

    static native void model_release(long ptr);

    static native String gcoderesult_get_recommended_name(long ptr);

    static native double gcoderesult_get_used_filament_mm(long ptr, int role);

    static native double gcoderesult_get_used_filament_g(long ptr, int role);

    static native void gcoderesult_release(long ptr);
}

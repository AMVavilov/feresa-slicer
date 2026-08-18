// SPDX-License-Identifier: AGPL-3.0-only
package ru.ytkab0bp.slicebeam.slic3r;

import java.io.File;
import java.util.Objects;

/** Headless model handle backed by OrcaSlicer Mobile's native {@code ModelRef}. */
public final class Model implements AutoCloseable {
    private long pointer;

    public Model(File file) throws Slic3rRuntimeError {
        this(file.getAbsolutePath());
    }

    public Model(String path) throws Slic3rRuntimeError {
        Objects.requireNonNull(path, "path");
        if (!Native.isLoaded()) {
            throw new IllegalStateException("Orca native libraries are not loaded", Native.getLoadError());
        }
        pointer = Native.model_read_from_file(path, baseName(path), 0);
        if (pointer == 0L) {
            throw new Slic3rRuntimeError("Orca native engine returned a null model handle");
        }
    }

    private static String baseName(String path) {
        String name = new File(path).getName();
        int extension = name.lastIndexOf('.');
        return extension > 0 ? name.substring(0, extension) : name;
    }

    public int getObjectsCount() {
        ensureOpen();
        return Native.model_get_objects_count(pointer);
    }

    public double[] getBoundingBoxExactGlobal() {
        ensureOpen();
        return Native.model_get_bounding_box_exact_global(pointer);
    }

    public void translate(int objectIndex, double x, double y, double z) {
        ensureOpen();
        Native.model_translate(pointer, objectIndex, x, y, z);
    }

    public void translate(double x, double y, double z) {
        ensureOpen();
        Native.model_translate_global(pointer, x, y, z);
    }

    public void ensureOnBed(int objectIndex) {
        ensureOpen();
        Native.model_ensure_on_bed(pointer, objectIndex);
    }

    public void scale(int objectIndex, double x, double y, double z) {
        ensureOpen();
        Native.model_scale(pointer, objectIndex, x, y, z);
    }

    /** Angles use radians, matching {@code ModelVolume::set_rotation} in the pinned native code. */
    public void rotate(int objectIndex, double x, double y, double z) {
        ensureOpen();
        Native.model_rotate(pointer, objectIndex, x, y, z);
    }

    public GCodeProcessorResult slice(
            String configPath,
            String gcodePath,
            SliceListener listener
    ) throws Slic3rRuntimeError {
        ensureOpen();
        Objects.requireNonNull(configPath, "configPath");
        Objects.requireNonNull(gcodePath, "gcodePath");
        Objects.requireNonNull(listener, "listener");
        long result = Native.model_slice(
                pointer,
                configPath,
                gcodePath,
                listener,
                1,
                null,
                0,
                0.0,
                0.0,
                0.0
        );
        if (result == 0L) {
            throw new Slic3rRuntimeError("Orca native engine returned a null G-code result handle");
        }
        return new GCodeProcessorResult(result);
    }

    public synchronized void release() {
        if (pointer != 0L) {
            Native.model_release(pointer);
            pointer = 0L;
        }
    }

    @Override
    public void close() {
        release();
    }

    private void ensureOpen() {
        if (pointer == 0L) {
            throw new IllegalStateException("Model has already been released");
        }
    }
}

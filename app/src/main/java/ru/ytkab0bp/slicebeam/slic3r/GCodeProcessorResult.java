// SPDX-License-Identifier: AGPL-3.0-only
package ru.ytkab0bp.slicebeam.slic3r;

/** Headless result handle returned by {@link Model#slice}. */
public final class GCodeProcessorResult implements AutoCloseable {
    /** Matches libvgcode's EGCodeExtrusionRole range used by the pinned Java client. */
    public static final int EXTRUSION_ROLES_COUNT = 15;

    private long pointer;

    GCodeProcessorResult(long pointer) {
        if (pointer == 0L) {
            throw new IllegalArgumentException("pointer must not be zero");
        }
        this.pointer = pointer;
    }

    public double getUsedFilamentMM(int role) {
        ensureOpen();
        checkRole(role);
        return Native.gcoderesult_get_used_filament_mm(pointer, role);
    }

    public double getUsedFilamentG(int role) {
        ensureOpen();
        checkRole(role);
        return Native.gcoderesult_get_used_filament_g(pointer, role);
    }

    public String getRecommendedName() {
        ensureOpen();
        return Native.gcoderesult_get_recommended_name(pointer);
    }

    public synchronized void release() {
        if (pointer != 0L) {
            Native.gcoderesult_release(pointer);
            pointer = 0L;
        }
    }

    @Override
    public void close() {
        release();
    }

    private static void checkRole(int role) {
        if (role < 0 || role >= EXTRUSION_ROLES_COUNT) {
            throw new IllegalArgumentException("Unknown extrusion role: " + role);
        }
    }

    private void ensureOpen() {
        if (pointer == 0L) {
            throw new IllegalStateException("G-code result has already been released");
        }
    }
}

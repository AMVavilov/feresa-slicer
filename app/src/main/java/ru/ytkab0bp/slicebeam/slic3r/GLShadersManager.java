// SPDX-License-Identifier: AGPL-3.0-only
package ru.ytkab0bp.slicebeam.slic3r;

/**
 * Headless compatibility shim required by the release library's JNI_OnLoad.
 *
 * <p>The slicer resolves this exact private static method even when no OpenGL viewer is used.</p>
 */
public final class GLShadersManager {
    private GLShadersManager() {
    }

    @SuppressWarnings("unused")
    private static long getCurrentShaderPointer() {
        return 0L;
    }
}

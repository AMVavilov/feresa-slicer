// SPDX-License-Identifier: AGPL-3.0-only
package ru.ytkab0bp.slicebeam.slic3r

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaMobileNativeAbiTest {
    @Test
    fun `model slice descriptor matches release 0_4_6`() {
        val method = Native::class.java.getDeclaredMethod(
            "model_slice",
            java.lang.Long.TYPE,
            String::class.java,
            String::class.java,
            SliceListener::class.java,
            java.lang.Integer.TYPE,
            IntArray::class.java,
            java.lang.Integer.TYPE,
            java.lang.Double.TYPE,
            java.lang.Double.TYPE,
            java.lang.Double.TYPE,
        )

        assertEquals(java.lang.Long.TYPE, method.returnType)
        assertTrue(Modifier.isStatic(method.modifiers))
        assertTrue(Modifier.isNative(method.modifiers))
    }

    @Test
    fun `model load transform and release descriptors match release`() {
        nativeMethod("set_svg_path_prefix", java.lang.Void.TYPE, String::class.java)
        nativeMethod(
            "model_read_from_file",
            java.lang.Long.TYPE,
            String::class.java,
            String::class.java,
            java.lang.Integer.TYPE,
        )
        nativeMethod(
            "model_scale",
            java.lang.Void.TYPE,
            java.lang.Long.TYPE,
            java.lang.Integer.TYPE,
            java.lang.Double.TYPE,
            java.lang.Double.TYPE,
            java.lang.Double.TYPE,
        )
        nativeMethod(
            "model_rotate",
            java.lang.Void.TYPE,
            java.lang.Long.TYPE,
            java.lang.Integer.TYPE,
            java.lang.Double.TYPE,
            java.lang.Double.TYPE,
            java.lang.Double.TYPE,
        )
        nativeMethod("model_release", java.lang.Void.TYPE, java.lang.Long.TYPE)
    }

    @Test
    fun `listener and JNI OnLoad shader shim have exact callable descriptors`() {
        val progress = SliceListener::class.java.getDeclaredMethod(
            "onProgress",
            java.lang.Integer.TYPE,
            String::class.java,
        )
        assertEquals(java.lang.Void.TYPE, progress.returnType)

        val shaderPointer = GLShadersManager::class.java.getDeclaredMethod("getCurrentShaderPointer")
        assertEquals(java.lang.Long.TYPE, shaderPointer.returnType)
        assertTrue(Modifier.isStatic(shaderPointer.modifiers))
    }

    @Test
    fun `result statistics and release descriptors match release`() {
        nativeMethod(
            "gcoderesult_get_used_filament_mm",
            java.lang.Double.TYPE,
            java.lang.Long.TYPE,
            java.lang.Integer.TYPE,
        )
        nativeMethod(
            "gcoderesult_get_used_filament_g",
            java.lang.Double.TYPE,
            java.lang.Long.TYPE,
            java.lang.Integer.TYPE,
        )
        nativeMethod("gcoderesult_release", java.lang.Void.TYPE, java.lang.Long.TYPE)
    }

    private fun nativeMethod(name: String, returnType: Class<*>, vararg parameters: Class<*>) {
        val method = Native::class.java.getDeclaredMethod(name, *parameters)
        assertEquals(returnType, method.returnType)
        assertTrue(Modifier.isStatic(method.modifiers))
        assertTrue(Modifier.isNative(method.modifiers))
    }
}

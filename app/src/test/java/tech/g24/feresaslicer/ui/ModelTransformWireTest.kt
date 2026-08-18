// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelTransformWireTest {
    @Test
    fun legacyTransformMapsToZRotationAndUniformAxisScale() {
        val json = ModelTransform(
            positionX = 12.0,
            positionY = 34.0,
            rotationDegrees = 45.0,
            scale = 2.0,
        ).toViewerJson()

        assertEquals(0.0, json.getDouble("positionZ"), Epsilon)
        assertEquals(45.0, json.getDouble("rotationZDegrees"), Epsilon)
        assertEquals(2.0, json.getDouble("scaleX"), Epsilon)
        assertEquals(2.0, json.getDouble("scaleY"), Epsilon)
        assertEquals(2.0, json.getDouble("scaleZ"), Epsilon)
    }

    @Test
    fun xyzTransformUsesIndependentAxisValues() {
        val json = ModelTransform(
            positionX = 12.0,
            positionY = 34.0,
            positionZ = 5.0,
            rotationDegrees = 30.0,
            rotationXDegrees = 10.0,
            rotationYDegrees = 20.0,
            scale = 9.0,
            scaleX = 1.0,
            scaleY = 2.0,
            scaleZ = 3.0,
        ).toViewerJson()

        assertEquals(5.0, json.getDouble("positionZ"), Epsilon)
        assertEquals(10.0, json.getDouble("rotationXDegrees"), Epsilon)
        assertEquals(20.0, json.getDouble("rotationYDegrees"), Epsilon)
        assertEquals(30.0, json.getDouble("rotationZDegrees"), Epsilon)
        assertEquals(1.0, json.getDouble("scaleX"), Epsilon)
        assertEquals(2.0, json.getDouble("scaleY"), Epsilon)
        assertEquals(3.0, json.getDouble("scaleZ"), Epsilon)
    }

    @Test
    fun multiObjectLoadIdentityIgnoresSelectedLegacyFile() {
        val first = File("first.stl")
        val second = File("second.stl")
        val objects = listOf(
            ViewerModelObject("first", first, ModelTransform()),
            ViewerModelObject("second", second, ModelTransform()),
        )

        assertNull(viewerLegacyModelFile(first, objects))
        assertNull(viewerLegacyModelFile(second, objects))
        assertEquals(first, viewerLegacyModelFile(first, emptyList()))
    }

    private companion object {
        const val Epsilon = 1e-9
    }
}

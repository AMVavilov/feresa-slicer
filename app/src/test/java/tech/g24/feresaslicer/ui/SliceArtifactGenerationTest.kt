// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SliceArtifactGenerationTest {
    @Test
    fun onlyArtifactFromCurrentGenerationIsUsable() {
        assertEquals("gcode", currentSliceArtifact("gcode", 7L, 7L))
        assertNull(currentSliceArtifact("gcode", 6L, 7L))
        assertNull(currentSliceArtifact("gcode", null, 7L))
        assertNull(currentSliceArtifact<String>(null, 7L, 7L))
    }
}

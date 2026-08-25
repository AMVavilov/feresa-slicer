// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerToolpathSelectionTest {
    @Test
    fun parsesSelectedSegmentMetadataFromViewer() {
        val selection = parseViewerToolpathSelection(
            """
            {
              "selected": true,
              "displayedSegmentCount": 8,
              "eligibleSegmentCount": 14,
              "lineCount": 120,
              "minimumLayerZ": 0.6,
              "maximumLayerZ": 0.8,
              "layer": 3,
              "lineNumber": 84,
              "x": 103.95,
              "y": 134.82,
              "z": 0.8,
              "speed": 45.0,
              "extrusion": true,
              "lineType": "outerWall",
              "lineTypeLabel": "Outer wall",
              "lineWidth": 0.46,
              "layerHeight": 0.2,
              "commands": [
                {"lineNumber": 83, "source": "G0 X100 Y130", "active": false},
                {"lineNumber": 84, "source": "G1 X103.95 Y134.82 E0.1", "active": true}
              ]
            }
            """.trimIndent(),
        )

        requireNotNull(selection)
        assertEquals(true, selection.selected)
        assertEquals(8, selection.displayedSegmentCount)
        assertEquals(14, selection.eligibleSegmentCount)
        assertEquals(120, selection.lineCount)
        assertEquals(0.6, selection.minimumLayerZ!!, 0.000001)
        assertEquals(0.8, selection.maximumLayerZ!!, 0.000001)
        assertEquals(3, selection.layer)
        assertEquals(84, selection.lineNumber)
        assertEquals(103.95, selection.x, 0.000001)
        assertEquals(134.82, selection.y, 0.000001)
        assertEquals(0.8, selection.z, 0.000001)
        assertEquals(45.0, selection.speedMmSeconds, 0.000001)
        assertEquals(true, selection.extrusion)
        assertEquals("outerWall", selection.lineType)
        assertEquals("Outer wall", selection.lineTypeLabel)
        assertEquals(0.46, selection.lineWidthMm!!, 0.000001)
        assertEquals(0.2, selection.layerHeightMm!!, 0.000001)
        assertEquals(2, selection.commands.size)
        assertEquals("G1 X103.95 Y134.82 E0.1", selection.commands[1].source)
        assertEquals(true, selection.commands[1].active)
    }

    @Test
    fun preservesUnavailableWidthAndHeightAsNull() {
        val selection = parseViewerToolpathSelection(
            """
            {
              "selected": true,
              "displayedSegmentCount": 1,
              "eligibleSegmentCount": 2,
              "layer": 0,
              "x": 10.0,
              "y": 20.0,
              "z": 0.2,
              "speed": 100.0,
              "extrusion": false,
              "lineType": "travel",
              "lineTypeLabel": "Travel",
              "lineWidth": null,
              "layerHeight": null
            }
            """.trimIndent(),
        )

        requireNotNull(selection)
        assertNull(selection.lineWidthMm)
        assertNull(selection.layerHeightMm)
    }

    @Test
    fun noVisibleSegmentPreservesLightweightSummary() {
        val selection = parseViewerToolpathSelection(
            """{"selected":false,"displayedSegmentCount":0,"eligibleSegmentCount":0,"lineCount":42}""",
        )

        requireNotNull(selection)
        assertEquals(false, selection.selected)
        assertEquals(42, selection.lineCount)
        assertEquals(0, selection.commands.size)
    }
}

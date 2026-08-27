// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerObjectSelectionWireTest {
    @Test
    fun pointerSelectionPreservesItsInteractionSource() {
        val selection = parseViewerObjectSelection(
            """{"objectId":"model-a","source":"pointer"}""",
        )

        assertEquals("model-a", selection.objectId)
        assertEquals("pointer", selection.source)
    }

    @Test
    fun nullableAndApiSelectionsDoNotBecomePointerInteractions() {
        val emptyBed = parseViewerObjectSelection(
            """{"objectId":null,"source":"pointer"}""",
        )
        val api = parseViewerObjectSelection(
            """{"objectId":"model-a","source":"api"}""",
        )

        assertNull(emptyBed.objectId)
        assertEquals("pointer", emptyBed.source)
        assertEquals("api", api.source)
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.modelimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDocumentPolicyTest {
    @Test
    fun `picker advertises only model MIME types`() {
        val pickerTypes = ModelDocumentPolicy.pickerMimeTypes.toSet()

        assertTrue("model/stl" in pickerTypes)
        assertTrue("model/obj" in pickerTypes)
        assertTrue("model/3mf" in pickerTypes)
        assertFalse("*/*" in pickerTypes)
        assertFalse(ModelDocumentPolicy.GenericBinaryMimeType in pickerTypes)
    }

    @Test
    fun `supported extension accepts generic provider MIME`() {
        assertTrue(ModelDocumentPolicy.accepts("Spirabiner - S.STL", "application/octet-stream"))
        assertTrue(ModelDocumentPolicy.accepts("plate.3Mf", null))
        assertTrue(ModelDocumentPolicy.accepts("mesh.OBJ", "text/plain"))
    }

    @Test
    fun `supported MIME accepts provider document without extension`() {
        assertTrue(ModelDocumentPolicy.accepts("download", "MODEL/STL; charset=binary"))
        assertTrue(
            ModelDocumentPolicy.accepts(
                null,
                "application/vnd.ms-package.3dmanufacturing-3dmodel+xml",
            ),
        )
    }

    @Test
    fun `known unsupported extension is rejected even with forged model MIME`() {
        assertFalse(ModelDocumentPolicy.accepts("toolpath.gcode", "model/stl"))
        assertFalse(ModelDocumentPolicy.accepts("archive.zip", "application/octet-stream"))
    }

    @Test
    fun `VIEW selection uses data and de-duplicates clip URI`() {
        assertEquals(
            listOf("content://models/one.stl", "content://models/two.obj"),
            IncomingModelUriSelection.select(
                action = IncomingModelUriSelection.ActionView,
                dataUri = "content://models/one.stl",
                streamUris = listOf("content://ignored/stream.3mf"),
                clipDataUris = listOf(
                    "content://models/one.stl",
                    "content://models/two.obj",
                ),
            ),
        )
    }

    @Test
    fun `SEND selection keeps all stream documents in stable order`() {
        assertEquals(
            listOf(
                "content://models/one.stl",
                "content://models/two.3mf",
                "content://models/three.obj",
            ),
            IncomingModelUriSelection.select(
                action = IncomingModelUriSelection.ActionSendMultiple,
                dataUri = null,
                streamUris = listOf(
                    "content://models/one.stl",
                    "content://models/two.3mf",
                ),
                clipDataUris = listOf(
                    "content://models/two.3mf",
                    "content://models/three.obj",
                ),
            ),
        )
    }

    @Test
    fun `unrelated actions never select documents`() {
        assertTrue(
            IncomingModelUriSelection.select(
                action = "android.intent.action.MAIN",
                dataUri = "content://models/unexpected.stl",
                streamUris = emptyList(),
                clipDataUris = emptyList(),
            ).isEmpty(),
        )
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeresaGcodeThumbnailInstrumentedTest {
    @Test
    fun androidCanvasProducesDecodableNonEmptyPngPreviews() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val gcode = File(context.cacheDir, "thumbnail-render-test.gcode").apply {
            writeText(
                """
                G90
                M83
                G0 X10 Y10 Z0.2
                G1 X30 Y10 E1
                G1 X30 Y30 E1
                G1 X10 Y30 E1
                G1 X10 Y10 E1
                G0 Z4
                G1 X30 Y30 E1
                """.trimIndent(),
            )
        }

        val thumbnails = AndroidGcodeThumbnailRenderer.render(gcode)

        assertEquals(listOf(32 to 32, 300 to 300), thumbnails.map { it.width to it.height })
        thumbnails.forEach { thumbnail ->
            val bitmap = BitmapFactory.decodeByteArray(thumbnail.png, 0, thumbnail.png.size)
            assertEquals(thumbnail.width, bitmap.width)
            assertEquals(thumbnail.height, bitmap.height)
            var visiblePixels = 0
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    if (Color.alpha(bitmap.getPixel(x, y)) > 0) visiblePixels += 1
                }
            }
            assertTrue("The rendered PNG is fully transparent", visiblePixels > 0)
            bitmap.recycle()
        }
    }
}

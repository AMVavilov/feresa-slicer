// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

/** Android-only bitmap boundary; the parser and G-code writer remain plain JVM code. */
internal object AndroidGcodeThumbnailRenderer {
    fun render(gcode: File): List<GcodeThumbnail> {
        val bounds = scanIsometricToolpathBounds(gcode) ?: return emptyList()
        val largeBitmap = renderLarge(gcode, bounds)
        return try {
            val smallBitmap = Bitmap.createScaledBitmap(
                largeBitmap,
                MiniatureSizePixels,
                MiniatureSizePixels,
                true,
            )
            try {
                listOf(
                    GcodeThumbnail(MiniatureSizePixels, MiniatureSizePixels, smallBitmap.toPng()),
                    GcodeThumbnail(PreviewSizePixels, PreviewSizePixels, largeBitmap.toPng()),
                )
            } finally {
                if (smallBitmap !== largeBitmap) smallBitmap.recycle()
            }
        } finally {
            largeBitmap.recycle()
        }
    }

    private fun renderLarge(gcode: File, bounds: IsometricToolpathBounds): Bitmap {
        val bitmap = Bitmap.createBitmap(
            PreviewSizePixels,
            PreviewSizePixels,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 0x0b, 0x36, 0x31)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 4.8f
        }
        val model = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0x32, 0xc9, 0xa8)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2.4f
        }
        val transform = PreviewTransform.from(bounds, PreviewSizePixels)
        val samplingStride = ceil(bounds.segmentCount.toDouble() / MaximumRenderedSegments)
            .toLong()
            .coerceAtLeast(1L)
        val path = Path()
        var sourceIndex = 0L
        var batchSize = 0

        fun flush() {
            if (batchSize == 0) return
            canvas.drawPath(path, outline)
            canvas.drawPath(path, model)
            path.reset()
            batchSize = 0
        }

        forEachExtrusionToolpathSegment(gcode) { segment ->
            if (sourceIndex % samplingStride == 0L) {
                val start = transform.map(projectIsometric(segment.start))
                val end = transform.map(projectIsometric(segment.end))
                path.moveTo(start.first, start.second)
                path.lineTo(end.first, end.second)
                batchSize += 1
                if (batchSize >= PathBatchSize) flush()
            }
            sourceIndex += 1L
        }
        flush()
        return bitmap
    }

    private fun Bitmap.toPng(): ByteArray = ByteArrayOutputStream().use { output ->
        check(compress(Bitmap.CompressFormat.PNG, 100, output)) {
            "Android could not encode the G-code preview as PNG"
        }
        output.toByteArray()
    }
}

private data class PreviewTransform(
    val scale: Double,
    val left: Double,
    val top: Double,
    val maximumY: Double,
) {
    fun map(point: IsometricPoint): Pair<Float, Float> =
        (left + point.x * scale).toFloat() to (top + (maximumY - point.y) * scale).toFloat()

    companion object {
        fun from(bounds: IsometricToolpathBounds, sizePixels: Int): PreviewTransform {
            val available = sizePixels - PreviewMarginPixels * 2.0
            val width = bounds.width.coerceAtLeast(MinimumProjectionSpan)
            val height = bounds.height.coerceAtLeast(MinimumProjectionSpan)
            val scale = min(available / width, available / height)
            val renderedWidth = bounds.width * scale
            val renderedHeight = bounds.height * scale
            val leftMargin = (sizePixels - renderedWidth) / 2.0
            val topMargin = (sizePixels - renderedHeight) / 2.0
            return PreviewTransform(
                scale = scale,
                left = leftMargin - bounds.minimumX * scale,
                top = topMargin,
                maximumY = bounds.maximumY,
            )
        }
    }
}

private const val MiniatureSizePixels = 32
private const val PreviewSizePixels = 300
private const val PreviewMarginPixels = 18
private const val PathBatchSize = 4_096
private const val MaximumRenderedSegments = 250_000.0
private const val MinimumProjectionSpan = 1e-6

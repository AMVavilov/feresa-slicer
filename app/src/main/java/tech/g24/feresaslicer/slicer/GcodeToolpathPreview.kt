// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.io.File
import kotlin.math.sqrt

internal data class ToolpathPoint3d(
    val x: Double,
    val y: Double,
    val z: Double,
)

internal data class ExtrusionToolpathSegment(
    val start: ToolpathPoint3d,
    val end: ToolpathPoint3d,
)

internal data class IsometricPoint(
    val x: Double,
    val y: Double,
)

internal data class IsometricToolpathBounds(
    val minimumX: Double,
    val maximumX: Double,
    val minimumY: Double,
    val maximumY: Double,
    val segmentCount: Long,
) {
    val width: Double get() = maximumX - minimumX
    val height: Double get() = maximumY - minimumY
}

/**
 * Streams printable extrusion moves without retaining the complete toolpath in Android memory.
 * This is deliberately independent from [analyzeOrcaGcode]: preview rendering needs the segment
 * endpoints, while the production report analyzer also handles distance, timing and arc length.
 */
internal fun forEachExtrusionToolpathSegment(
    gcode: File,
    consumer: (ExtrusionToolpathSegment) -> Unit,
) {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var e = 0.0
    var absolutePosition = true
    var absoluteExtrusion = true

    gcode.useLines { lines ->
        lines.forEach { rawLine ->
            val line = rawLine.substringBefore(';').trim().uppercase()
            when {
                line == "G90" -> absolutePosition = true
                line == "G91" -> absolutePosition = false
                line == "M82" -> absoluteExtrusion = true
                line == "M83" -> absoluteExtrusion = false
                line.startsWith("G92") -> {
                    val words = previewWords(line)
                    words["X"]?.let { x = it }
                    words["Y"]?.let { y = it }
                    words["Z"]?.let { z = it }
                    words["E"]?.let { e = it }
                }
                PreviewMotionCommand.containsMatchIn(line) -> {
                    val words = previewWords(line)
                    val nextX = words["X"]?.let { if (absolutePosition) it else x + it } ?: x
                    val nextY = words["Y"]?.let { if (absolutePosition) it else y + it } ?: y
                    val nextZ = words["Z"]?.let { if (absolutePosition) it else z + it } ?: z
                    val nextE = words["E"]?.let { if (absoluteExtrusion) it else e + it } ?: e
                    val dx = nextX - x
                    val dy = nextY - y
                    val dz = nextZ - z
                    val movementSquared = dx * dx + dy * dy + dz * dz
                    if (nextE > e + ExtrusionEpsilon && movementSquared > MovementEpsilonSquared) {
                        consumer(
                            ExtrusionToolpathSegment(
                                start = ToolpathPoint3d(x, y, z),
                                end = ToolpathPoint3d(nextX, nextY, nextZ),
                            ),
                        )
                    }
                    x = nextX
                    y = nextY
                    z = nextZ
                    e = nextE
                }
            }
        }
    }
}

internal fun projectIsometric(point: ToolpathPoint3d): IsometricPoint = IsometricPoint(
    x = (point.x - point.y) * InverseSquareRootTwo,
    y = (-point.x - point.y + 2.0 * point.z) * InverseSquareRootSix,
)

internal fun scanIsometricToolpathBounds(gcode: File): IsometricToolpathBounds? {
    var minimumX = Double.POSITIVE_INFINITY
    var maximumX = Double.NEGATIVE_INFINITY
    var minimumY = Double.POSITIVE_INFINITY
    var maximumY = Double.NEGATIVE_INFINITY
    var segmentCount = 0L
    forEachExtrusionToolpathSegment(gcode) { segment ->
        val start = projectIsometric(segment.start)
        val end = projectIsometric(segment.end)
        minimumX = minOf(minimumX, start.x, end.x)
        maximumX = maxOf(maximumX, start.x, end.x)
        minimumY = minOf(minimumY, start.y, end.y)
        maximumY = maxOf(maximumY, start.y, end.y)
        segmentCount += 1L
    }
    if (segmentCount == 0L || !minimumX.isFinite() || !minimumY.isFinite()) return null
    return IsometricToolpathBounds(
        minimumX = minimumX,
        maximumX = maximumX,
        minimumY = minimumY,
        maximumY = maximumY,
        segmentCount = segmentCount,
    )
}

private fun previewWords(line: String): Map<String, Double> =
    PreviewGcodeWord.findAll(line).associate { match ->
        match.groupValues[1].uppercase() to match.groupValues[2].toDouble()
    }

private val PreviewGcodeWord =
    Regex("([XYZEF])\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))", RegexOption.IGNORE_CASE)
private val PreviewMotionCommand =
    Regex("^G0?[0123](?=\\s|[XYZEF]|$)", RegexOption.IGNORE_CASE)
private val InverseSquareRootTwo = 1.0 / sqrt(2.0)
private val InverseSquareRootSix = 1.0 / sqrt(6.0)
private const val ExtrusionEpsilon = 1e-7
private const val MovementEpsilonSquared = 1e-14

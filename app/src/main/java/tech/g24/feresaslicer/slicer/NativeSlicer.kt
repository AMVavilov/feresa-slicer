// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.io.File
import kotlin.math.PI
import kotlin.math.sqrt

data class SlicerSettings(
    val layerHeightMm: Double = 0.20,
    val nozzleDiameterMm: Double = 0.40,
    val filamentDiameterMm: Double = 1.75,
    val nozzleTemperatureC: Int = 210,
    val bedTemperatureC: Int = 60,
    val printSpeedMmS: Double = 45.0,
    val bedWidthMm: Double = 220.0,
    val bedDepthMm: Double = 220.0,
    val modelPositionXmm: Double = 110.0,
    val modelPositionYmm: Double = 110.0,
    val modelRotationDegrees: Double = 0.0,
    val modelScale: Double = 1.0,
)

data class SliceReport(
    val success: Boolean,
    val message: String,
    val layers: Long = 0,
    val extrusionSegments: Long = 0,
    val filamentLengthMm: Double = 0.0,
    val estimatedPrintTimeSeconds: Long = 0,
    val extrusionDistanceMm: Double = 0.0,
    val travelDistanceMm: Double = 0.0,
    val travelSegments: Long = 0,
    val filamentWeightGrams: Double = 0.0,
    val minimumZmm: Double = 0.0,
    val maximumZmm: Double = 0.0,
)

private data class GcodeAnalysis(
    val estimatedSeconds: Long,
    val extrusionDistanceMm: Double,
    val travelDistanceMm: Double,
    val travelSegments: Long,
    val minimumZmm: Double,
    val maximumZmm: Double,
)

private val GcodeWord = Regex("([XYZEF])\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))", RegexOption.IGNORE_CASE)

private fun analyzeGcode(file: File): GcodeAnalysis {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var e = 0.0
    var feed = 0.0
    var absolutePosition = true
    var absoluteExtrusion = true
    var seconds = 0.0
    var extrusionDistance = 0.0
    var travelDistance = 0.0
    var travelSegments = 0L
    var minimumZ = Double.POSITIVE_INFINITY
    var maximumZ = 0.0

    file.useLines { lines ->
        lines.forEach { rawLine ->
            val line = rawLine.substringBefore(';').trim().uppercase()
            when {
                line.startsWith("G90") -> absolutePosition = true
                line.startsWith("G91") -> absolutePosition = false
                line.startsWith("M82") -> absoluteExtrusion = true
                line.startsWith("M83") -> absoluteExtrusion = false
                line.startsWith("G92") -> {
                    val words = GcodeWord.findAll(line).associate { it.groupValues[1].uppercase() to it.groupValues[2].toDouble() }
                    words["X"]?.let { x = it }
                    words["Y"]?.let { y = it }
                    words["Z"]?.let { z = it }
                    words["E"]?.let { e = it }
                }
                line.matches(Regex("G0?0(?:\\s.*)?")) || line.matches(Regex("G0?1(?:\\s.*)?")) -> {
                    val words = GcodeWord.findAll(line).associate { it.groupValues[1].uppercase() to it.groupValues[2].toDouble() }
                    val nextX = words["X"]?.let { if (absolutePosition) it else x + it } ?: x
                    val nextY = words["Y"]?.let { if (absolutePosition) it else y + it } ?: y
                    val nextZ = words["Z"]?.let { if (absolutePosition) it else z + it } ?: z
                    val nextE = words["E"]?.let { if (absoluteExtrusion) it else e + it } ?: e
                    words["F"]?.let { feed = it }
                    val distance = sqrt((nextX - x) * (nextX - x) + (nextY - y) * (nextY - y) + (nextZ - z) * (nextZ - z))
                    val isExtrusion = nextE > e + 1e-7 && distance > 1e-7
                    if (isExtrusion) extrusionDistance += distance else if (distance > 1e-7) {
                        travelDistance += distance
                        travelSegments += 1
                    }
                    if (feed > 0.0) seconds += distance / (feed / 60.0)
                    x = nextX
                    y = nextY
                    z = nextZ
                    e = nextE
                    minimumZ = minOf(minimumZ, z)
                    maximumZ = maxOf(maximumZ, z)
                }
            }
        }
    }
    return GcodeAnalysis(
        estimatedSeconds = seconds.toLong(),
        extrusionDistanceMm = extrusionDistance,
        travelDistanceMm = travelDistance,
        travelSegments = travelSegments,
        minimumZmm = if (minimumZ.isFinite()) minimumZ else 0.0,
        maximumZmm = maximumZ,
    )
}

object NativeSlicer {
    init {
        System.loadLibrary("feresa_slicer")
    }

    private external fun slice(
        inputPath: String,
        outputPath: String,
        layerHeight: Double,
        nozzleDiameter: Double,
        filamentDiameter: Double,
        nozzleTemperature: Int,
        bedTemperature: Int,
        printSpeed: Double,
        bedWidth: Double,
        bedDepth: Double,
        positionX: Double,
        positionY: Double,
        rotationDegrees: Double,
        modelScale: Double,
    ): String

    fun sliceModel(
        inputPath: String,
        outputPath: String,
        settings: SlicerSettings,
    ): SliceReport {
        val fields = slice(
            inputPath = inputPath,
            outputPath = outputPath,
            layerHeight = settings.layerHeightMm,
            nozzleDiameter = settings.nozzleDiameterMm,
            filamentDiameter = settings.filamentDiameterMm,
            nozzleTemperature = settings.nozzleTemperatureC,
            bedTemperature = settings.bedTemperatureC,
            printSpeed = settings.printSpeedMmS,
            bedWidth = settings.bedWidthMm,
            bedDepth = settings.bedDepthMm,
            positionX = settings.modelPositionXmm,
            positionY = settings.modelPositionYmm,
            rotationDegrees = settings.modelRotationDegrees,
            modelScale = settings.modelScale,
        ).split('\t', limit = 5)

        val baseReport = SliceReport(
            success = fields.getOrNull(0) == "OK",
            message = fields.getOrNull(1) ?: "Unknown native slicer response",
            layers = fields.getOrNull(2)?.toLongOrNull() ?: 0,
            extrusionSegments = fields.getOrNull(3)?.toLongOrNull() ?: 0,
            filamentLengthMm = fields.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
        )
        if (!baseReport.success) return baseReport

        val analysis = runCatching { analyzeGcode(File(outputPath)) }.getOrNull() ?: return baseReport
        val filamentRadius = settings.filamentDiameterMm / 2.0
        val filamentVolumeMm3 = PI * filamentRadius * filamentRadius * baseReport.filamentLengthMm
        val plaWeightGrams = filamentVolumeMm3 / 1000.0 * 1.24
        return baseReport.copy(
            estimatedPrintTimeSeconds = analysis.estimatedSeconds,
            extrusionDistanceMm = analysis.extrusionDistanceMm,
            travelDistanceMm = analysis.travelDistanceMm,
            travelSegments = analysis.travelSegments,
            filamentWeightGrams = plaWeightGrams,
            minimumZmm = analysis.minimumZmm,
            maximumZmm = analysis.maximumZmm,
        )
    }
}

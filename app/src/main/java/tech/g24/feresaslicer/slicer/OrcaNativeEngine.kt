// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.io.File
import java.util.TreeSet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.Native
import ru.ytkab0bp.slicebeam.slic3r.SliceListener

/**
 * Headless adapter for OrcaSlicer Mobile's pinned `libslic3r` JNI API.
 *
 * Every print/process/printer value is read by native OrcaSlicer from [configPath]. The
 * [SlicerSettings] argument is used only for the model transform and for calculating a fallback
 * filament mass from the generated G-code. This separation prevents a partial scalar bridge from
 * silently overriding or ignoring the complete Orca INI configuration.
 */
class OrcaNativeEngine internal constructor(
    private val isNativeAvailable: () -> Boolean,
    private val nativeLoadFailure: () -> Throwable?,
    private val modelFactory: OrcaModelFactory,
    private val configureNativeWorkDirectory: (String) -> Unit = Native::set_svg_path_prefix,
) {
    constructor() : this(
        isNativeAvailable = Native::isLoaded,
        nativeLoadFailure = Native::getLoadError,
        modelFactory = OrcaModelFactory { path -> JniOrcaModel(Model(path)) },
    )

    fun sliceModel(
        inputPath: String,
        configPath: String,
        outputPath: String,
        settings: SlicerSettings,
        onProgress: ((progress: Int, stage: String) -> Unit)? = null,
    ): SliceReport = synchronized(EngineLock) {
        val validationFailure = validateRequest(inputPath, configPath, outputPath, settings)
        if (validationFailure != null) return@synchronized SliceReport(false, validationFailure)

        if (!isNativeAvailable()) {
            val detail = nativeLoadFailure()?.message?.takeIf(String::isNotBlank)
            return@synchronized SliceReport(
                success = false,
                message = buildString {
                    append("Orca native engine is not installed")
                    if (detail != null) append(": ").append(detail)
                },
            )
        }

        val output = File(outputPath)
        output.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                return@synchronized SliceReport(false, "Cannot create G-code directory: $parent")
            }
        }
        if (output.exists() && !output.delete()) {
            return@synchronized SliceReport(false, "Cannot replace previous G-code: $outputPath")
        }

        runCatching {
            val workDirectory = output.absoluteFile.parentFile
                ?: File(configPath).absoluteFile.parentFile
                ?: error("Cannot resolve a writable Orca work directory")
            configureNativeWorkDirectory(workDirectory.absolutePath + File.separator)
            modelFactory.open(inputPath).use { model ->
                applyTransform(model, settings)
                model.slice(configPath, outputPath, onProgress).use { nativeResult ->
                    require(output.isFile && output.length() > 0L) {
                        "Orca reported success but did not create G-code at $outputPath"
                    }
                    reportFromGcode(
                        gcode = output,
                        filamentDiameterMm = settings.filamentDiameterMm,
                        filamentDensityGcm3 = firstConfigNumber(configPath, "filament_density")
                            ?: 1.24,
                        nativeName = nativeResult.recommendedName(),
                    )
                }
            }
        }.getOrElse { error ->
            SliceReport(
                success = false,
                message = error.message?.takeIf(String::isNotBlank)
                    ?: error::class.java.simpleName,
            )
        }
    }

    private fun applyTransform(model: OrcaModel, settings: SlicerSettings) {
        val objectCount = model.objectCount()
        require(objectCount > 0) { "The input model contains no printable objects" }

        val rotationZ = Math.toRadians(settings.modelRotationDegrees)
        for (objectIndex in 0 until objectCount) {
            if (settings.modelScale != 1.0) {
                model.scale(
                    objectIndex,
                    settings.modelScale,
                    settings.modelScale,
                    settings.modelScale,
                )
            }
            if (rotationZ != 0.0) model.rotate(objectIndex, 0.0, 0.0, rotationZ)
            if (settings.ensureModelOnBed) model.ensureOnBed(objectIndex)
        }

        val bounds = model.exactGlobalBounds()
        require(bounds.size == 6 && bounds.all(Double::isFinite)) {
            "Orca returned an invalid model bounding box"
        }
        val centerX = (bounds[0] + bounds[3]) / 2.0
        val centerY = (bounds[1] + bounds[4]) / 2.0
        model.translate(
            settings.modelPositionXmm - centerX,
            settings.modelPositionYmm - centerY,
            0.0,
        )
    }

    private fun validateRequest(
        inputPath: String,
        configPath: String,
        outputPath: String,
        settings: SlicerSettings,
    ): String? {
        val input = File(inputPath)
        if (!input.isFile) return "Model file does not exist: $inputPath"
        val config = File(configPath)
        if (!config.isFile) return "Orca INI file does not exist: $configPath"
        if (config.length() == 0L) return "Orca INI file is empty: $configPath"
        if (outputPath.isBlank()) return "G-code output path is empty"
        if (!settings.modelScale.isFinite() || settings.modelScale <= 0.0) {
            return "Model scale must be finite and greater than zero"
        }
        if (!settings.modelRotationDegrees.isFinite()) return "Model rotation must be finite"
        if (!settings.modelPositionXmm.isFinite() || !settings.modelPositionYmm.isFinite()) {
            return "Model position must be finite"
        }
        if (!settings.filamentDiameterMm.isFinite() || settings.filamentDiameterMm <= 0.0) {
            return "Filament diameter must be finite and greater than zero"
        }
        return null
    }

    private companion object {
        val EngineLock = Any()
    }
}

internal fun interface OrcaModelFactory {
    fun open(path: String): OrcaModel
}

internal interface OrcaModel : AutoCloseable {
    fun objectCount(): Int
    fun exactGlobalBounds(): DoubleArray
    fun scale(objectIndex: Int, x: Double, y: Double, z: Double)
    fun rotate(objectIndex: Int, x: Double, y: Double, z: Double)
    fun ensureOnBed(objectIndex: Int)
    fun translate(x: Double, y: Double, z: Double)
    fun slice(
        configPath: String,
        outputPath: String,
        onProgress: ((Int, String) -> Unit)?,
    ): OrcaSliceResult
}

internal interface OrcaSliceResult : AutoCloseable {
    fun recommendedName(): String
}

private class JniOrcaModel(
    private val model: Model,
) : OrcaModel {
    override fun objectCount(): Int = model.objectsCount

    override fun exactGlobalBounds(): DoubleArray = model.boundingBoxExactGlobal

    override fun scale(objectIndex: Int, x: Double, y: Double, z: Double) =
        model.scale(objectIndex, x, y, z)

    override fun rotate(objectIndex: Int, x: Double, y: Double, z: Double) =
        model.rotate(objectIndex, x, y, z)

    override fun ensureOnBed(objectIndex: Int) = model.ensureOnBed(objectIndex)

    override fun translate(x: Double, y: Double, z: Double) = model.translate(x, y, z)

    override fun slice(
        configPath: String,
        outputPath: String,
        onProgress: ((Int, String) -> Unit)?,
    ): OrcaSliceResult {
        val listener = SliceListener { progress, stage -> onProgress?.invoke(progress, stage) }
        return JniOrcaSliceResult(model.slice(configPath, outputPath, listener))
    }

    override fun close() = model.close()
}

private class JniOrcaSliceResult(
    private val result: GCodeProcessorResult,
) : OrcaSliceResult {
    override fun recommendedName(): String = result.recommendedName

    override fun close() = result.close()
}

internal data class OrcaGcodeAnalysis(
    val layers: Long,
    val extrusionSegments: Long,
    val filamentLengthMm: Double,
    val estimatedSeconds: Long,
    val extrusionDistanceMm: Double,
    val travelDistanceMm: Double,
    val travelSegments: Long,
    val minimumZmm: Double,
    val maximumZmm: Double,
)

private val OrcaGcodeWord =
    Regex("([XYZEFIJKR])\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))", RegexOption.IGNORE_CASE)

private fun reportFromGcode(
    gcode: File,
    filamentDiameterMm: Double,
    filamentDensityGcm3: Double,
    nativeName: String,
): SliceReport {
    val analysis = analyzeOrcaGcode(gcode)
    val filamentRadius = filamentDiameterMm / 2.0
    val filamentVolumeMm3 = PI * filamentRadius * filamentRadius * analysis.filamentLengthMm
    val filamentWeightGrams = filamentVolumeMm3 / 1000.0 * filamentDensityGcm3
    return SliceReport(
        success = true,
        message = "G-code created",
        recommendedFileName = nativeName.takeIf(String::isNotBlank),
        layers = analysis.layers,
        extrusionSegments = analysis.extrusionSegments,
        filamentLengthMm = analysis.filamentLengthMm,
        estimatedPrintTimeSeconds = analysis.estimatedSeconds,
        extrusionDistanceMm = analysis.extrusionDistanceMm,
        travelDistanceMm = analysis.travelDistanceMm,
        travelSegments = analysis.travelSegments,
        filamentWeightGrams = filamentWeightGrams,
        minimumZmm = analysis.minimumZmm,
        maximumZmm = analysis.maximumZmm,
    )
}

private fun firstConfigNumber(configPath: String, key: String): Double? =
    File(configPath).useLines { lines ->
        lines.firstNotNullOfOrNull { line ->
            val separator = line.indexOf('=')
            if (separator < 0 || line.substring(0, separator).trim() != key) return@firstNotNullOfOrNull null
            line.substring(separator + 1)
                .trim()
                .substringBefore(',')
                .removeSuffix("%")
                .trim()
                .toDoubleOrNull()
                ?.takeIf { value -> value.isFinite() && value > 0.0 }
        }
    }

internal fun analyzeOrcaGcode(file: File): OrcaGcodeAnalysis {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var e = 0.0
    var feed = 0.0
    var absolutePosition = true
    var absoluteExtrusion = true
    var seconds = 0.0
    var filamentLength = 0.0
    var extrusionDistance = 0.0
    var extrusionSegments = 0L
    var travelDistance = 0.0
    var travelSegments = 0L
    var minimumZ = Double.POSITIVE_INFINITY
    var maximumZ = Double.NEGATIVE_INFINITY
    val extrusionLayers = TreeSet<Long>()

    file.useLines { lines ->
        lines.forEach { rawLine ->
            val line = rawLine.substringBefore(';').trim().uppercase()
            when {
                line == "G90" -> absolutePosition = true
                line == "G91" -> absolutePosition = false
                line == "M82" -> absoluteExtrusion = true
                line == "M83" -> absoluteExtrusion = false
                line.startsWith("G92") -> {
                    val words = wordsOf(line)
                    words["X"]?.let { x = it }
                    words["Y"]?.let { y = it }
                    words["Z"]?.let { z = it }
                    words["E"]?.let { e = it }
                }
                MotionCommand.matches(line) -> {
                    val words = wordsOf(line)
                    val command = line.substringBefore(' ').removePrefix("G").toIntOrNull() ?: 0
                    val nextX = words["X"]?.let { if (absolutePosition) it else x + it } ?: x
                    val nextY = words["Y"]?.let { if (absolutePosition) it else y + it } ?: y
                    val nextZ = words["Z"]?.let { if (absolutePosition) it else z + it } ?: z
                    val nextE = words["E"]?.let { if (absoluteExtrusion) it else e + it } ?: e
                    words["F"]?.let { feed = it }
                    val deltaZ = nextZ - z
                    val planarDistance = if (command == 2 || command == 3) {
                        arcPlanarLength(x, y, nextX, nextY, words, clockwise = command == 2)
                    } else {
                        sqrt((nextX - x) * (nextX - x) + (nextY - y) * (nextY - y))
                    }
                    val distance = sqrt(planarDistance * planarDistance + deltaZ * deltaZ)
                    val extrusionDelta = nextE - e
                    if (extrusionDelta > 1e-7 && distance > 1e-7) {
                        // Exclude E-only deretraction: it restores previously retracted filament
                        // but does not consume material in the printed object.
                        filamentLength += extrusionDelta
                        extrusionDistance += distance
                        extrusionSegments++
                        extrusionLayers += (nextZ * 1_000_000.0).toLong()
                    } else if (distance > 1e-7) {
                        travelDistance += distance
                        travelSegments++
                    }
                    if (feed > 0.0 && distance > 0.0) seconds += distance / (feed / 60.0)
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

    return OrcaGcodeAnalysis(
        layers = extrusionLayers.size.toLong(),
        extrusionSegments = extrusionSegments,
        filamentLengthMm = filamentLength,
        estimatedSeconds = seconds.toLong(),
        extrusionDistanceMm = extrusionDistance,
        travelDistanceMm = travelDistance,
        travelSegments = travelSegments,
        minimumZmm = minimumZ.takeIf(Double::isFinite) ?: 0.0,
        maximumZmm = maximumZ.takeIf(Double::isFinite) ?: 0.0,
    )
}

private val MotionCommand = Regex("^G0?[0-3](?:\\s.*)?$")

internal fun arcPlanarLength(
    startX: Double,
    startY: Double,
    endX: Double,
    endY: Double,
    words: Map<String, Double>,
    clockwise: Boolean,
): Double {
    val radiusWord = words["R"]
    if (radiusWord != null) {
        val chord = sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
        val radius = abs(radiusWord)
        if (radius <= 1e-9 || chord > radius * 2.0 + 1e-7) return chord
        val minorSweep = 2.0 * asin((chord / (radius * 2.0)).coerceIn(0.0, 1.0))
        val sweep = if (radiusWord < 0.0) PI * 2.0 - minorSweep else minorSweep
        return radius * sweep
    }

    if ("I" !in words && "J" !in words) {
        return sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
    }
    val centerX = startX + (words["I"] ?: 0.0)
    val centerY = startY + (words["J"] ?: 0.0)
    val radius = sqrt((startX - centerX) * (startX - centerX) + (startY - centerY) * (startY - centerY))
    if (radius <= 1e-9) return 0.0
    val startAngle = atan2(startY - centerY, startX - centerX)
    val endAngle = atan2(endY - centerY, endX - centerX)
    var sweep = endAngle - startAngle
    if (clockwise) {
        while (sweep >= 0.0) sweep -= PI * 2.0
    } else {
        while (sweep <= 0.0) sweep += PI * 2.0
    }
    return radius * abs(sweep)
}

private fun wordsOf(line: String): Map<String, Double> = OrcaGcodeWord.findAll(line)
    .associate { match ->
        match.groupValues[1].uppercase() to match.groupValues[2].toDouble()
    }

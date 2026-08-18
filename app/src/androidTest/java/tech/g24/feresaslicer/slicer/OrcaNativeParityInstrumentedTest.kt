// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.g24.feresaslicer.ui.PrintSettingsState
import tech.g24.feresaslicer.ui.toOrcaProcessSettingsPayload

/**
 * Device-level parity checks for the pinned OrcaSlicer Mobile engine.
 *
 * These tests intentionally exercise the actual packaged ARM64 `libslic3r.so`; a JVM fake cannot
 * prove that a visible setting reaches `DynamicPrintConfig` and changes generated toolpaths.
 */
@RunWith(AndroidJUnit4::class)
class OrcaNativeParityInstrumentedTest {
    @Test
    fun nativeOptionKeysContainEveryVisiblePrintControl() {
        assertTrue("The packaged Orca engine failed to load", ru.ytkab0bp.slicebeam.slic3r.Native.isLoaded())

        val visiblePayload = PrintSettingsState().toOrcaProcessSettingsPayload()
        val nativeFffKeys = OrcaDefaultConfigProvider.fffOptionKeys()
        val missing = visiblePayload.keys - nativeFffKeys

        assertEquals("The Print menu contract changed without a parity test update", 72, visiblePayload.size)
        assertTrue("Visible Print controls unsupported by native Orca: ${missing.sorted()}", missing.isEmpty())
    }

    @Test
    fun wallLoopsChangeRealOrcaPerimeters() {
        val twoWalls = sliceBox("walls-2", baseState.copy(wallLoops = "2"))
        val fiveWalls = sliceBox("walls-5", baseState.copy(wallLoops = "5"))

        assertConfigValue(twoWalls, "wall_loops", "2")
        assertConfigValue(fiveWalls, "wall_loops", "5")
        assertTrue(
            "Five wall loops must create more inner-wall toolpath than two: $twoWalls -> $fiveWalls",
            fiveWalls.extrusionFor("inner wall") > twoWalls.extrusionFor("inner wall") * 2.0,
        )
    }

    @Test
    fun shellLayersAndInfillDensityChangeRealOrcaFeatures() {
        val thinShells = sliceBox(
            "shells-1",
            baseState.copy(topShellLayers = "1", bottomShellLayers = "1", infillDensity = "20"),
        )
        val thickShells = sliceBox(
            "shells-6",
            baseState.copy(topShellLayers = "6", bottomShellLayers = "6", infillDensity = "20"),
        )
        val infill20 = sliceBox("infill-20", baseState.copy(infillDensity = "20"))
        val infill40 = sliceBox("infill-40", baseState.copy(infillDensity = "40"))

        assertConfigValue(thinShells, "top_shell_layers", "1")
        assertConfigValue(thinShells, "bottom_shell_layers", "1")
        assertConfigValue(thickShells, "top_shell_layers", "6")
        assertConfigValue(thickShells, "bottom_shell_layers", "6")
        assertTrue(
            "Six top/bottom shell layers must create more solid feature extrusion",
            thickShells.solidFeatureExtrusionMm > thinShells.solidFeatureExtrusionMm * 1.5,
        )

        assertConfigValue(infill20, "sparse_infill_density", "20%")
        assertConfigValue(infill40, "sparse_infill_density", "40%")
        assertTrue(
            "40% sparse infill must extrude more than 20% sparse infill",
            infill40.extrusionFor("sparse infill") > infill20.extrusionFor("sparse infill") * 1.35,
        )
    }

    @Test
    fun supportToggleAndOuterWallSpeedChangeRealOrcaGcode() {
        val supportOff = slice(
            fixtureAsset = "parity_overhang.stl",
            runName = "support-off",
            state = baseState.copy(enableSupport = false),
        )
        val supportOn = slice(
            fixtureAsset = "parity_overhang.stl",
            runName = "support-on",
            state = baseState.copy(
                enableSupport = true,
                supportType = "normal(auto)",
                supportThresholdAngle = "30",
            ),
        )
        // A large, shallow box keeps each layer above Orca's minimum-layer-time cooling limit, so
        // this assertion measures the requested wall speed rather than the cooling slowdown.
        val speed10 = slice(
            "parity_speed_box.stl",
            "speed-10",
            baseState.copy(outerWallSpeed = "10", infillDensity = "0"),
        )
        val speed20 = slice(
            "parity_speed_box.stl",
            "speed-20",
            baseState.copy(outerWallSpeed = "20", infillDensity = "0"),
        )

        assertConfigValue(supportOff, "enable_support", "0")
        assertConfigValue(supportOn, "enable_support", "1")
        assertEquals("Support disabled must produce no support extrusion", 0.0, supportOff.supportExtrusionMm, 0.001)
        assertTrue("Support enabled must produce support extrusion", supportOn.supportExtrusionMm > 1.0)

        assertConfigValue(speed10, "outer_wall_speed", "10")
        assertConfigValue(speed20, "outer_wall_speed", "20")
        assertTrue(
            "20 mm/s outer-wall setting must produce a higher outer-wall feed than 10 mm/s",
            speed20.medianFeedFor("outer wall") > speed10.medianFeedFor("outer wall") * 1.8,
        )
    }

    @Test
    fun composedPlateSlicesBothSeparatedStlObjects() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val targetContext = instrumentation.targetContext
        val runName = "two-separated-boxes"
        val runDirectory = File(targetContext.cacheDir, "orca-parity/$runName").apply { mkdirs() }
        val source = File(runDirectory, "parity_box.stl")
        testContext.assets.open("parity_box.stl").use { input ->
            source.outputStream().use(input::copyTo)
        }

        val composition = StlPlateComposer.compose(
            placements = listOf(
                StlPlatePlacement(file = source, positionXmm = 50.0, positionYmm = 110.0),
                StlPlatePlacement(file = source, positionXmm = 170.0, positionYmm = 110.0),
            ),
            output = File(runDirectory, "composed-plate.stl"),
        )
        assertEquals("The composed plate must retain both source meshes", 24L, composition.triangleCount)
        val snapshot = slicePreparedModel(
            model = composition.file,
            runDirectory = runDirectory,
            runName = runName,
            state = baseState,
            transform = slicerTransform.copy(
                modelPositionXmm = composition.bounds.centerX,
                modelPositionYmm = composition.bounds.centerY,
            ),
        )

        val printableExtrusions = snapshot.extrusionPoints.filterNot { it.feature == "unknown" }
        assertTrue(
            "Packaged Orca must emit printable extrusion in the left object's X zone: " +
                printableExtrusions.map(GcodeExtrusionPoint::x).distinct().sorted().take(12),
            printableExtrusions.any { it.x in 39.0..61.0 },
        )
        assertTrue(
            "Packaged Orca must emit printable extrusion in the right object's X zone: " +
                printableExtrusions.map(GcodeExtrusionPoint::x).distinct().sortedDescending().take(12),
            printableExtrusions.any { it.x in 159.0..181.0 },
        )
    }

    @Test
    fun xyzRotationAndNonUniformScaleReachPackagedOrca() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val targetContext = instrumentation.targetContext
        val runName = "xyz-non-uniform"
        val runDirectory = File(targetContext.cacheDir, "orca-parity/$runName").apply { mkdirs() }
        val source = File(runDirectory, "parity_box.stl")
        testContext.assets.open("parity_box.stl").use { input ->
            source.outputStream().use(input::copyTo)
        }
        val composition = StlPlateComposer.compose(
            placements = listOf(
                StlPlatePlacement(
                    file = source,
                    positionXmm = 110.0,
                    positionYmm = 110.0,
                    positionZmm = 10.0,
                    rotationXDegrees = 90.0,
                    scaleX = 1.5,
                    scaleY = 1.0,
                    scaleZ = 1.0,
                ),
            ),
            output = File(runDirectory, "xyz-plate.stl"),
        )
        assertEquals(30.0, composition.bounds.width, 0.001)
        assertEquals(8.0, composition.bounds.depth, 0.001)
        assertEquals(20.0, composition.bounds.height, 0.001)
        assertEquals(0.0, composition.bounds.minimumZ, 0.001)

        val snapshot = slicePreparedModel(
            model = composition.file,
            runDirectory = runDirectory,
            runName = runName,
            state = baseState,
            transform = slicerTransform.copy(
                modelPositionXmm = composition.bounds.centerX,
                modelPositionYmm = composition.bounds.centerY,
            ),
        )
        val printable = snapshot.extrusionPoints.filterNot { it.feature == "unknown" }
        assertTrue("Transformed model must retain its 30 mm X span", printable.any { it.x < 96.0 })
        assertTrue("Transformed model must retain its 30 mm X span", printable.any { it.x > 124.0 })
        assertTrue("Transformed model must emit real printable extrusion", printable.isNotEmpty())
    }

    private fun sliceBox(runName: String, state: PrintSettingsState): GcodeSnapshot =
        slice("parity_box.stl", runName, state)

    private fun slice(
        fixtureAsset: String,
        runName: String,
        state: PrintSettingsState,
    ): GcodeSnapshot {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val targetContext = instrumentation.targetContext
        val runDirectory = File(targetContext.cacheDir, "orca-parity/$runName").apply { mkdirs() }
        val model = File(runDirectory, fixtureAsset)
        testContext.assets.open(fixtureAsset).use { input ->
            model.outputStream().use(input::copyTo)
        }

        return slicePreparedModel(model, runDirectory, runName, state, slicerTransform)
    }

    private fun slicePreparedModel(
        model: File,
        runDirectory: File,
        runName: String,
        state: PrintSettingsState,
        transform: SlicerSettings,
    ): GcodeSnapshot {
        val dynamicConfig = OrcaDynamicPrintConfigBuilder.build(
            // No profile on purpose: the production generic configuration must pass Orca's
            // invariants before a user signs in or selects a synced printer preset.
            profiles = OrcaSelectedProfiles(),
            machineFilament = machineFilament,
            liveProcessSettings = state.toOrcaProcessSettingsPayload(),
        )
        val usesRelativeExtrusion = dynamicConfig.settings["use_relative_e_distances"] == "1"
        val beforeLayerGcode = dynamicConfig.settings["before_layer_change_gcode"].orEmpty()
        assertTrue(
            "Generic Orca config must either use absolute E or reset relative E before every " +
                "layer; relative=${dynamicConfig.settings["use_relative_e_distances"]}, " +
                "before_layer_change_gcode=$beforeLayerGcode",
            !usesRelativeExtrusion || beforeLayerGcode.contains("G92 E0", ignoreCase = true),
        )
        val config = dynamicConfig.writeTo(File(runDirectory, "current.ini"))
        val gcode = File(runDirectory, "$runName.gcode")
        val progressEvents = mutableListOf<Int>()
        val report = OrcaNativeEngine().sliceModel(
            inputPath = model.absolutePath,
            configPath = config.absolutePath,
            outputPath = gcode.absolutePath,
            settings = transform,
            onProgress = { progress, _ -> progressEvents += progress },
        )

        assertTrue("Orca slice failed: ${report.message}", report.success)
        assertTrue("Orca did not write G-code", gcode.isFile && gcode.length() > 0L)
        assertTrue("Orca emitted no extrusion segments", report.extrusionSegments > 0L)
        assertTrue("Orca emitted no progress callbacks", progressEvents.isNotEmpty())
        return parseGcode(gcode, dynamicConfig.settings).also { snapshot ->
            val feedHistogram = snapshot.feedByFeatureMmPerMinute.mapValues { (_, values) ->
                values.groupingBy { it }.eachCount()
            }
            Log.i(
                LOG_TAG,
                "$runName bytes=${gcode.length()} layers=${report.layers} " +
                    "segments=${report.extrusionSegments} features=${snapshot.extrusionByFeatureMm} " +
                    "feeds=$feedHistogram",
            )
        }
    }

    private fun assertConfigValue(snapshot: GcodeSnapshot, key: String, expected: String) {
        assertEquals("Native input config did not receive '$key'", expected, snapshot.config.getValue(key))
    }

    private companion object {
        const val LOG_TAG = "OrcaParity"
        val machineFilament = OrcaMachineFilamentScalars(
            bedWidthMm = 220.0,
            bedDepthMm = 220.0,
            printableHeightMm = 250.0,
            nozzleDiameterMm = "0.4",
            filamentDiameterMm = "1.75",
            nozzleTemperatureC = "210",
            bedTemperatureC = "60",
            gcodeFlavor = "marlin",
        )
        val slicerTransform = SlicerSettings(
            filamentDiameterMm = 1.75,
            bedWidthMm = 220.0,
            bedDepthMm = 220.0,
            modelPositionXmm = 110.0,
            modelPositionYmm = 110.0,
        )
        val baseState = PrintSettingsState(
            // Keep layer height below every 0.4 mm nozzle-derived line width. Orca correctly
            // rejects a width less than or equal to layer height before toolpath generation.
            layerHeight = "0.2",
            initialLayerHeight = "0.2",
            wallLoops = "2",
            topShellLayers = "2",
            bottomShellLayers = "2",
            infillDensity = "20",
            infillPattern = "grid",
            skirtLoops = "0",
            brimType = "no_brim",
            brimWidth = "0",
            gcodeComments = true,
        )
    }
}

private data class GcodeSnapshot(
    val config: Map<String, String>,
    val extrusionByFeatureMm: Map<String, Double>,
    val feedByFeatureMmPerMinute: Map<String, List<Double>>,
    val extrusionPoints: List<GcodeExtrusionPoint>,
) {
    fun extrusionFor(feature: String): Double = extrusionByFeatureMm[feature] ?: 0.0

    fun medianFeedFor(feature: String): Double {
        val sorted = feedByFeatureMmPerMinute[feature].orEmpty().sorted()
        if (sorted.isEmpty()) return 0.0
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    val solidFeatureExtrusionMm: Double
        get() = extrusionByFeatureMm.entries.sumOf { (feature, distance) ->
            if (feature.contains("solid infill") ||
                feature.contains("top surface") ||
                feature.contains("bottom surface")
            ) distance else 0.0
        }

    val supportExtrusionMm: Double
        get() = extrusionByFeatureMm.entries.sumOf { (feature, distance) ->
            if (feature.contains("support")) distance else 0.0
        }
}

private data class GcodeExtrusionPoint(
    val x: Double,
    val y: Double,
    val feature: String,
)

private val gcodeWord =
    Regex("([XYZEF])\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))", RegexOption.IGNORE_CASE)

private fun parseGcode(file: File, config: Map<String, String>): GcodeSnapshot {
    var feature = "unknown"
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var e = 0.0
    var feed = 0.0
    var absolutePosition = true
    var absoluteExtrusion = true
    val extrusionByFeature = linkedMapOf<String, Double>()
    val feedByFeature = linkedMapOf<String, MutableList<Double>>()
    val extrusionPoints = mutableListOf<GcodeExtrusionPoint>()

    file.useLines { lines ->
        lines.forEach { rawLine ->
            parseFeature(rawLine)?.let { feature = it }
            val command = rawLine.substringBefore(';').trim().uppercase(Locale.ROOT)
            when {
                command == "G90" -> absolutePosition = true
                command == "G91" -> absolutePosition = false
                command == "M82" -> absoluteExtrusion = true
                command == "M83" -> absoluteExtrusion = false
                command.startsWith("G92") -> {
                    val words = wordsOf(command)
                    words["X"]?.let { x = it }
                    words["Y"]?.let { y = it }
                    words["Z"]?.let { z = it }
                    words["E"]?.let { e = it }
                }
                command.startsWith("G0 ") || command.startsWith("G00 ") ||
                    command.startsWith("G1 ") || command.startsWith("G01 ") -> {
                    val words = wordsOf(command)
                    val nextX = words["X"]?.let { if (absolutePosition) it else x + it } ?: x
                    val nextY = words["Y"]?.let { if (absolutePosition) it else y + it } ?: y
                    val nextZ = words["Z"]?.let { if (absolutePosition) it else z + it } ?: z
                    val nextE = words["E"]?.let { if (absoluteExtrusion) it else e + it } ?: e
                    words["F"]?.let { feed = it }
                    val distance = sqrt(
                        (nextX - x) * (nextX - x) +
                            (nextY - y) * (nextY - y) +
                            (nextZ - z) * (nextZ - z),
                    )
                    if (nextE - e > 1e-7 && distance > 1e-7) {
                        extrusionByFeature[feature] = (extrusionByFeature[feature] ?: 0.0) + distance
                        feedByFeature.getOrPut(feature) { mutableListOf() } += feed
                        extrusionPoints += GcodeExtrusionPoint(nextX, nextY, feature)
                    }
                    x = nextX
                    y = nextY
                    z = nextZ
                    e = nextE
                }
            }
        }
    }

    return GcodeSnapshot(config, extrusionByFeature, feedByFeature, extrusionPoints)
}

private fun parseFeature(line: String): String? {
    val trimmed = line.trim()
    val raw = when {
        trimmed.startsWith("; FEATURE:", ignoreCase = true) -> trimmed.substringAfter(':')
        trimmed.startsWith(";TYPE:", ignoreCase = true) -> trimmed.substringAfter(':')
        else -> return null
    }
    return raw.trim().lowercase(Locale.ROOT)
}

private fun wordsOf(command: String): Map<String, Double> = gcodeWord.findAll(command).associate {
    it.groupValues[1].uppercase(Locale.ROOT) to it.groupValues[2].toDouble()
}

// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.nio.file.Files
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaNativeEngineTest {
    @Test
    fun `passes full INI path applies transform and releases native handles`() {
        val directory = Files.createTempDirectory("feresa-orca-engine-test").toFile()
        val input = directory.resolve("model.stl").apply { writeText("solid test\nendsolid test\n") }
        val config = directory.resolve("full.ini").apply {
            writeText("layer_height = 0.2\nfilament_density = 1.0\n")
        }
        val output = directory.resolve("result.gcode")
        val model = FakeModel(output)
        val progress = mutableListOf<Pair<Int, String>>()
        val engine = OrcaNativeEngine(
            isNativeAvailable = { true },
            nativeLoadFailure = { null },
            modelFactory = OrcaModelFactory { model },
            configureNativeWorkDirectory = {},
        )

        val report = engine.sliceModel(
            inputPath = input.absolutePath,
            configPath = config.absolutePath,
            outputPath = output.absolutePath,
            settings = SlicerSettings(
                filamentDiameterMm = 1.75,
                modelPositionXmm = 110.0,
                modelPositionYmm = 110.0,
                modelRotationDegrees = 90.0,
                modelScale = 2.0,
            ),
            onProgress = { percent, stage -> progress += percent to stage },
        )

        assertTrue(report.message, report.success)
        assertEquals(config.absolutePath, model.configPath)
        assertEquals(output.absolutePath, model.outputPath)
        assertEquals(listOf(2.0, 2.0, 2.0), model.scale?.drop(1))
        assertEquals(Math.PI / 2.0, model.rotation?.get(3) ?: 0.0, 1e-12)
        assertEquals(listOf(100.0, 105.0, 0.0), model.translation)
        assertEquals(listOf(0), model.ensureOnBedCalls)
        assertEquals(listOf(10 to "Slicing"), progress)
        assertEquals(2L, report.layers)
        assertEquals(3L, report.extrusionSegments)
        assertEquals(3.0, report.filamentLengthMm, 1e-9)
        assertEquals(PI * 0.875 * 0.875 * 3.0 / 1000.0, report.filamentWeightGrams, 1e-9)
        assertEquals("model.gcode", report.recommendedFileName)
        assertTrue(model.closed)
        assertTrue(model.result.closed)
    }

    @Test
    fun `preserves precomposed Z when automatic move to bed is disabled`() {
        val directory = Files.createTempDirectory("feresa-orca-precomposed-test").toFile()
        val input = directory.resolve("plate.stl").apply { writeText("solid test\nendsolid test\n") }
        val config = directory.resolve("full.ini").apply {
            writeText("layer_height = 0.2\nfilament_density = 1.0\n")
        }
        val output = directory.resolve("result.gcode")
        val model = FakeModel(output)
        val engine = OrcaNativeEngine(
            isNativeAvailable = { true },
            nativeLoadFailure = { null },
            modelFactory = OrcaModelFactory { model },
            configureNativeWorkDirectory = {},
        )

        val report = engine.sliceModel(
            inputPath = input.absolutePath,
            configPath = config.absolutePath,
            outputPath = output.absolutePath,
            settings = SlicerSettings(
                modelPositionXmm = 10.0,
                modelPositionYmm = 5.0,
                ensureModelOnBed = false,
            ),
        )

        assertTrue(report.message, report.success)
        assertTrue(model.ensureOnBedCalls.isEmpty())
        assertEquals(listOf(0.0, 0.0, 0.0), model.translation)
    }

    @Test
    fun `fails before JNI when full INI file is missing`() {
        val directory = Files.createTempDirectory("feresa-orca-engine-validation").toFile()
        val input = directory.resolve("model.stl").apply { writeText("solid test\nendsolid test\n") }
        var opened = false
        val engine = OrcaNativeEngine(
            isNativeAvailable = { true },
            nativeLoadFailure = { null },
            modelFactory = OrcaModelFactory {
                opened = true
                error("must not open")
            },
            configureNativeWorkDirectory = {},
        )

        val report = engine.sliceModel(
            inputPath = input.absolutePath,
            configPath = directory.resolve("missing.ini").absolutePath,
            outputPath = directory.resolve("out.gcode").absolutePath,
            settings = SlicerSettings(),
        )

        assertFalse(report.success)
        assertTrue(report.message.contains("does not exist"))
        assertFalse(opened)
    }

    @Test
    fun `analyzer measures arcs and excludes E-only deretraction from material`() {
        val gcode = Files.createTempFile("feresa-orca-arc", ".gcode").toFile().apply {
            writeText(
                """
                G90
                M83
                G0 X10 Y0 Z0.2 F6000
                G1 E1 F1200
                G3 X0 Y10 I-10 J0 E1 F1200
                """.trimIndent(),
            )
        }

        val analysis = analyzeOrcaGcode(gcode)

        assertEquals(PI * 5.0, analysis.extrusionDistanceMm, 0.01)
        assertEquals(1.0, analysis.filamentLengthMm, 1e-9)
        assertEquals(1L, analysis.extrusionSegments)
        assertEquals(1L, analysis.layers)
    }

    private class FakeModel(
        private val output: java.io.File,
    ) : OrcaModel {
        var scale: List<Double>? = null
        var rotation: List<Double>? = null
        var translation: List<Double>? = null
        var configPath: String? = null
        var outputPath: String? = null
        val ensureOnBedCalls = mutableListOf<Int>()
        var closed = false
        val result = FakeResult()

        override fun objectCount(): Int = 1

        override fun exactGlobalBounds(): DoubleArray =
            doubleArrayOf(0.0, 0.0, 0.0, 20.0, 10.0, 5.0)

        override fun scale(objectIndex: Int, x: Double, y: Double, z: Double) {
            scale = listOf(objectIndex.toDouble(), x, y, z)
        }

        override fun rotate(objectIndex: Int, x: Double, y: Double, z: Double) {
            rotation = listOf(objectIndex.toDouble(), x, y, z)
        }

        override fun ensureOnBed(objectIndex: Int) {
            ensureOnBedCalls += objectIndex
        }

        override fun translate(x: Double, y: Double, z: Double) {
            translation = listOf(x, y, z)
        }

        override fun slice(
            configPath: String,
            outputPath: String,
            onProgress: ((Int, String) -> Unit)?,
        ): OrcaSliceResult {
            this.configPath = configPath
            this.outputPath = outputPath
            onProgress?.invoke(10, "Slicing")
            output.writeText(
                """
                G90
                M82
                G1 X0 Y0 Z0.2 F6000
                G1 X10 Y0 E1 F600
                G0 X10 Y10
                G1 X0 Y10 E2
                G1 Z0.4 F6000
                G1 X0 Y0 E3 F600
                """.trimIndent(),
            )
            return result
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeResult : OrcaSliceResult {
        var closed = false

        override fun recommendedName(): String = "model.gcode"

        override fun close() {
            closed = true
        }
    }
}

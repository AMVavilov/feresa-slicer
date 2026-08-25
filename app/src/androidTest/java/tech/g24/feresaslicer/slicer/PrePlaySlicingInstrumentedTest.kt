// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.g24.feresaslicer.auth.OrcaCloudProfile
import tech.g24.feresaslicer.auth.OrcaProfileType
import tech.g24.feresaslicer.MainActivity
import tech.g24.feresaslicer.ui.ModelTransform
import tech.g24.feresaslicer.ui.ModelViewer
import tech.g24.feresaslicer.ui.PrintSettingsState
import tech.g24.feresaslicer.ui.ViewerMode
import tech.g24.feresaslicer.ui.toOrcaProcessSettingsPayload

/**
 * Mandatory device tests for the exact Model-screen slicing pipeline.
 *
 * A native abort or low-memory process death terminates instrumentation and therefore fails the
 * release gate. Keep these tests on a real ARM64 target; JVM fakes cannot load packaged libslic3r.
 */
@RunWith(AndroidJUnit4::class)
class PrePlaySlicingInstrumentedTest {
    @Test
    fun representativePlateSlicesThreeTimesWithoutCrashOrRetainedNativeLeak() {
        assertTrue("The packaged Orca engine failed to load", NativeFacade.isLoaded())
        Runtime.getRuntime().gc()
        System.runFinalization()
        SystemClock.sleep(750)
        val baselinePssKb = Debug.getPss().toLong()
        var peakPssKb = baselinePssKb
        var warmedPssKb = baselinePssKb

        val runs = listOf(
            ReleaseSliceCase(
                "stress-default",
                "release-fixtures/preview_stress_medallion.stl",
                basePrintSettings,
            ),
            ReleaseSliceCase(
                "overhang-support",
                "parity_overhang.stl",
                basePrintSettings.copy(
                    enableSupport = true,
                    supportType = "normal(auto)",
                    supportThresholdAngle = "30",
                ),
            ),
            ReleaseSliceCase(
                "stress-five-walls",
                "release-fixtures/preview_stress_medallion.stl",
                basePrintSettings.copy(wallLoops = "5", infillDensity = "35"),
            ),
        )

        runs.forEachIndexed { index, releaseCase ->
            ProcessPssSampler().use { sampler ->
                val result = sliceFixture(
                    runName = releaseCase.name,
                    fixtureAsset = releaseCase.fixture,
                    printSettings = releaseCase.printSettings,
                )
                assertHealthyGcode(result)
                peakPssKb = maxOf(peakPssKb, sampler.peakPssKb())
            }
            if (index == 0) {
                // Orca lazily initializes preset/parser caches during the first slice. Treat that
                // one-time cost as the warm baseline so this assertion detects growth per repeat,
                // not intentional process initialization.
                Runtime.getRuntime().gc()
                System.runFinalization()
                SystemClock.sleep(750)
                warmedPssKb = Debug.getPss().toLong()
                peakPssKb = maxOf(peakPssKb, warmedPssKb)
            }
        }

        Runtime.getRuntime().gc()
        System.runFinalization()
        SystemClock.sleep(750)
        val retainedPssKb = Debug.getPss().toLong()
        peakPssKb = maxOf(peakPssKb, retainedPssKb)
        val repeatedSliceGrowthKb = (retainedPssKb - warmedPssKb).coerceAtLeast(0L)
        Log.i(
            LOG_TAG,
            "memory baseline=${baselinePssKb}KiB warmed=${warmedPssKb}KiB " +
                "peak=${peakPssKb}KiB retained=${retainedPssKb}KiB " +
                "repeatGrowth=${repeatedSliceGrowthKb}KiB",
        )

        assertTrue(
            "Slicing exceeded the mobile process budget: peak=${peakPssKb / 1024} MiB",
            peakPssKb <= MAX_PROCESS_PSS_KB,
        )
        assertTrue(
            "Repeated slices retained too much memory after warm-up: " +
                "${repeatedSliceGrowthKb / 1024} MiB",
            repeatedSliceGrowthKb <= MAX_REPEAT_GROWTH_KB,
        )
    }

    @Test
    fun selectedFilamentValuesReachTheNativeIniAndStillSlice() {
        val selectedPetg = OrcaCloudProfile(
            id = "pre-play-petg",
            name = "Pre-Play PETG",
            type = OrcaProfileType.FILAMENT,
            contentJson = JSONObject()
                .put("name", "Pre-Play PETG")
                .put("type", "filament")
                .put("filament_type", JSONArray().put("PETG"))
                .put("filament_density", JSONArray().put("1.27"))
                .put("filament_max_volumetric_speed", JSONArray().put("8"))
                .toString(),
            updatedTime = 1L,
        )
        val result = sliceFixture(
            runName = "selected-petg",
            fixtureAsset = "parity_box.stl",
            printSettings = basePrintSettings,
            profiles = OrcaSelectedProfiles(filament = selectedPetg),
        )

        assertEquals("PETG", result.nativeSettings["filament_type"])
        assertEquals("1.27", result.nativeSettings["filament_density"])
        assertEquals("8", result.nativeSettings["filament_max_volumetric_speed"])
        assertHealthyGcode(result)
    }

    @Test
    fun slicedToolpathRendersThreeTimesInProductionWebViewWithoutRendererDeath() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        ) as MainActivity
        val gcodeState = mutableStateOf<File?>(null)
        val viewerGeneration = mutableIntStateOf(0)
        val renderLatch = AtomicReference(CountDownLatch(1))
        val renderError = AtomicReference<String?>(null)
        val renderedSegments = AtomicLong(0L)
        var peakPssKb = Debug.getPss().toLong()

        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    key(viewerGeneration.intValue) {
                        gcodeState.value?.let { gcode ->
                            ModelViewer(
                                modelFile = null,
                                gcodeFile = gcode,
                                transform = ModelTransform(),
                                bedWidth = 220.0,
                                bedDepth = 220.0,
                                mode = ViewerMode.TOOLPATH,
                                darkTheme = true,
                                onSceneState = {},
                                onToolpathRendered = { count ->
                                    renderedSegments.set(count.toLong())
                                    renderLatch.get().countDown()
                                },
                                onError = { message ->
                                    renderError.compareAndSet(null, message)
                                    renderLatch.get().countDown()
                                },
                                viewerHeight = null,
                            )
                        }
                    }
                }
            }

            repeat(3) { index ->
                val result = sliceFixture(
                    runName = "viewer-cycle-${index + 1}",
                    fixtureAsset = "release-fixtures/preview_stress_medallion.stl",
                    printSettings = basePrintSettings.copy(infillDensity = (20 + index * 5).toString()),
                )
                assertHealthyGcode(result)
                renderLatch.set(CountDownLatch(1))
                renderError.set(null)
                renderedSegments.set(0L)
                instrumentation.runOnMainSync {
                    gcodeState.value = result.files.gcode
                    viewerGeneration.intValue += 1
                }

                assertTrue(
                    "Timed out waiting for the production toolpath WebView on cycle ${index + 1}",
                    renderLatch.get().await(45, TimeUnit.SECONDS),
                )
                instrumentation.waitForIdleSync()
                SystemClock.sleep(1_000)
                assertTrue(
                    "Toolpath renderer failed on cycle ${index + 1}: ${renderError.get()}",
                    renderError.get() == null,
                )
                assertTrue(
                    "Toolpath renderer produced too few segments on cycle ${index + 1}: ${renderedSegments.get()}",
                    renderedSegments.get() > 1_000L,
                )
                peakPssKb = maxOf(peakPssKb, Debug.getPss().toLong())

                instrumentation.runOnMainSync {
                    gcodeState.value = null
                    viewerGeneration.intValue += 1
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(500)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }

        assertTrue(
            "Post-slice preview exceeded the application memory budget: ${peakPssKb / 1024} MiB",
            peakPssKb <= MAX_PROCESS_PSS_KB,
        )
    }

    private fun sliceFixture(
        runName: String,
        fixtureAsset: String,
        printSettings: PrintSettingsState,
        profiles: OrcaSelectedProfiles = OrcaSelectedProfiles(),
    ): OrcaPlateSliceResult {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val runDirectory = File(
            instrumentation.targetContext.cacheDir,
            "pre-play-slicing/$runName",
        ).apply {
            deleteRecursively()
            check(mkdirs()) { "Cannot create $this" }
        }
        val source = File(runDirectory, File(fixtureAsset).name)
        instrumentation.context.assets.open(fixtureAsset).use { input ->
            source.outputStream().use(input::copyTo)
        }
        val files = OrcaPlateSliceFiles(
            config = File(runDirectory, "current.ini"),
            composedPlate = File(runDirectory, "plate.stl"),
            gcode = File(runDirectory, "$runName.gcode"),
        )

        val result = AtomicReference<OrcaPlateSliceResult?>()
        val failure = AtomicReference<Throwable?>()
        val finished = CountDownLatch(1)
        thread(name = "pre-play-native-slice-$runName", isDaemon = true) {
            try {
                result.set(
                    OrcaPlateSlicePipeline.slice(
                        placements = listOf(
                            StlPlatePlacement(
                                file = source,
                                positionXmm = 110.0,
                                positionYmm = 110.0,
                            ),
                        ),
                        profiles = profiles,
                        machineFilament = machineFilament,
                        liveProcessSettings = printSettings.toOrcaProcessSettingsPayload(),
                        baseSettings = slicerSettings,
                        files = files,
                    ),
                )
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }
        assertTrue(
            "Native Orca slice '$runName' exceeded ${MAX_SLICE_SECONDS}s",
            finished.await(MAX_SLICE_SECONDS, TimeUnit.SECONDS),
        )
        failure.get()?.let { error ->
            throw AssertionError("Native Orca slice '$runName' failed", error)
        }
        return checkNotNull(result.get()) { "Native Orca slice '$runName' returned no result" }
    }

    private fun assertHealthyGcode(result: OrcaPlateSliceResult) {
        val report = result.report
        assertTrue("Orca slice failed: ${report.message}", report.success)
        val output = result.files.gcode
        assertTrue("Orca wrote empty G-code", output.isFile && output.length() > 256L)
        assertTrue("Orca reported no layers", report.layers > 0L)
        assertTrue("Orca reported no extrusion moves", report.extrusionSegments > 0L)
        assertTrue("Orca reported an invalid Z range", report.maximumZmm >= report.minimumZmm)
        var hasMotion = false
        var hasNonFiniteNumber = false
        output.useLines { lines ->
            lines.forEach { rawLine ->
                val command = rawLine.substringBefore(';')
                if (MOTION_COMMAND.containsMatchIn(command)) hasMotion = true
                if (NON_FINITE_GCODE_WORD.containsMatchIn(command)) hasNonFiniteNumber = true
            }
        }
        assertTrue("G-code has no motion commands", hasMotion)
        assertTrue("G-code contains a non-finite numeric value", !hasNonFiniteNumber)
    }

    private data class ReleaseSliceCase(
        val name: String,
        val fixture: String,
        val printSettings: PrintSettingsState,
    )

    private class ProcessPssSampler : AutoCloseable {
        private val running = AtomicBoolean(true)
        private val maximumPssKb = AtomicLong(Debug.getPss().toLong())
        private val worker = thread(name = "pre-play-memory-sampler", isDaemon = true) {
            while (running.get()) {
                maximumPssKb.accumulateAndGet(Debug.getPss().toLong()) { current, sample ->
                    maxOf(current, sample)
                }
                SystemClock.sleep(100)
            }
        }

        fun peakPssKb(): Long = maxOf(maximumPssKb.get(), Debug.getPss().toLong())

        override fun close() {
            running.set(false)
            worker.join(2_000)
        }
    }

    private object NativeFacade {
        fun isLoaded(): Boolean = ru.ytkab0bp.slicebeam.slic3r.Native.isLoaded()
    }

    private companion object {
        const val LOG_TAG = "FeresaPrePlay"
        const val MAX_PROCESS_PSS_KB = 512L * 1024L
        const val MAX_REPEAT_GROWTH_KB = 128L * 1024L
        const val MAX_SLICE_SECONDS = 90L
        val MOTION_COMMAND = Regex("^G0?1\\s", RegexOption.IGNORE_CASE)
        val NON_FINITE_GCODE_WORD = Regex(
            "(?:^|\\s)[XYZEFIJKR]?[+-]?(?:nan|inf(?:inity)?)(?=\\s|$)",
            RegexOption.IGNORE_CASE,
        )

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
        val slicerSettings = SlicerSettings(
            bedWidthMm = 220.0,
            bedDepthMm = 220.0,
            modelPositionXmm = 110.0,
            modelPositionYmm = 110.0,
        )
        val basePrintSettings = PrintSettingsState(
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

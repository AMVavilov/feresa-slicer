// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.g24.feresaslicer.MainActivity
import tech.g24.feresaslicer.plate.PlateBounds
import tech.g24.feresaslicer.plate.PlateModelSource
import tech.g24.feresaslicer.plate.PlateWorkspace
import tech.g24.feresaslicer.plate.RectangularBuildVolume
import tech.g24.feresaslicer.ui.FeresaSlicerApp
import tech.g24.feresaslicer.ui.ModelSliceActionTestTag
import tech.g24.feresaslicer.ui.ModelSliceResultTestTag
import tech.g24.feresaslicer.ui.ModelToolpathViewerTestTag
import tech.g24.feresaslicer.ui.RenderedToolpathSegmentsKey

/**
 * User-visible regression for the Model screen.
 *
 * Unlike the lower-level pipeline tests, this test presses the real Compose action and waits for
 * the real screen state produced by [FeresaSlicerApp]. A native abort, an indefinitely blocked
 * slice, or failure to publish the generated G-code therefore blocks the Play release gate.
 */
@RunWith(AndroidJUnit4::class)
class PrePlayModelScreenE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun realModelScreenSliceActionProducesAUsableGcodeResult() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val fixtureDirectory = File(targetContext.cacheDir, "pre-play-model-screen").apply {
            deleteRecursively()
            check(mkdirs()) { "Cannot create $this" }
        }
        val modelFile = File(fixtureDirectory, "preview_stress_medallion.stl")
        instrumentation.context.assets
            .open("release-fixtures/preview_stress_medallion.stl")
            .use { input -> modelFile.outputStream().use(input::copyTo) }
        val mesh = StlPlateComposer.inspect(modelFile)
        val bounds = mesh.bounds
        val workspace = PlateWorkspace.empty().addCentered(
            source = PlateModelSource(
                file = modelFile,
                displayName = modelFile.name,
                localBounds = PlateBounds(
                    minimumX = bounds.minimumX,
                    minimumY = bounds.minimumY,
                    minimumZ = bounds.minimumZ,
                    maximumX = bounds.maximumX,
                    maximumY = bounds.maximumY,
                    maximumZ = bounds.maximumZ,
                ),
                triangleCount = mesh.triangleCount,
                originalSizeBytes = modelFile.length(),
            ),
            buildVolume = RectangularBuildVolume(220.0, 220.0, 250.0),
        )

        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                FeresaSlicerApp(initialPlateWorkspace = workspace)
            }
        }

        composeRule.waitUntil(timeoutMillis = UI_READY_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(ModelSliceActionTestTag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(ModelSliceActionTestTag)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = SLICE_UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(ModelSliceResultTestTag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(ModelSliceResultTestTag)
            .assertExists()
        composeRule.waitUntil(timeoutMillis = UI_READY_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(ModelToolpathViewerTestTag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(ModelToolpathViewerTestTag)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = TOOLPATH_RENDER_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(ModelToolpathViewerTestTag)
                .fetchSemanticsNodes()
                .any { node ->
                    node.config.contains(RenderedToolpathSegmentsKey) &&
                        node.config[RenderedToolpathSegmentsKey] > MINIMUM_RENDERED_SEGMENTS
                }
        }

        val generatedGcode = targetContext.cacheDir
            .listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    file.name.startsWith("feresa-slicer-output-") &&
                    file.extension.equals("gcode", ignoreCase = true)
            }
            .maxByOrNull(File::lastModified)
        assertTrue(
            "The real Model screen did not retain a usable generated G-code artifact",
            generatedGcode != null && generatedGcode.length() > 256L,
        )
        assertTrue(
            "The Model-screen G-code result contains no motion commands",
            generatedGcode!!.useLines { lines ->
                lines.any { line -> line.startsWith("G1 ") || line.startsWith("G0 ") }
            },
        )
    }

    private companion object {
        const val UI_READY_TIMEOUT_MS = 30_000L
        const val SLICE_UI_TIMEOUT_MS = 180_000L
        const val TOOLPATH_RENDER_TIMEOUT_MS = 120_000L
        const val MINIMUM_RENDERED_SEGMENTS = 1_000L
    }
}

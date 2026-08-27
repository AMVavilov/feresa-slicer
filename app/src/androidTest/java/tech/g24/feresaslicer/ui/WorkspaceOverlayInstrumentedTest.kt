// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.g24.feresaslicer.MainActivity
import tech.g24.feresaslicer.plate.PlateBounds
import tech.g24.feresaslicer.plate.PlateModelSource
import tech.g24.feresaslicer.plate.PlateObject
import tech.g24.feresaslicer.plate.PlateObjectId
import tech.g24.feresaslicer.plate.PlateObjectTransform

@RunWith(AndroidJUnit4::class)
class WorkspaceOverlayInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cameraPresetsRemainOpenAndCompactAcrossConsecutiveSelections() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                var selectedPreset by remember {
                    mutableStateOf<CameraViewPreset?>(CameraViewPreset.ISOMETRIC)
                }
                CompositionLocalProvider(LocalUiLanguage provides UiLanguage.ENGLISH) {
                    MaterialTheme {
                        Box(Modifier.fillMaxSize()) {
                            CameraViewPresetOverlay(
                                selectedPreset = selectedPreset,
                                onSelect = { selectedPreset = it },
                                onFitModel = {},
                                onShowBed = {},
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag(CameraViewOverlayTriggerTestTag).performClick()
        composeRule.onNodeWithContentDescription("Top").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("Hide camera views").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Bottom").performClick().assertIsSelected()
        composeRule.onNodeWithContentDescription("Hide camera views").assertIsDisplayed()

        val density = composeRule.activity.resources.displayMetrics.density
        val itemHeightDp = composeRule.onNodeWithContentDescription("Bottom")
            .fetchSemanticsNode()
            .boundsInRoot
            .height / density
        assertTrue("Camera preset item remained taller than 54 dp: $itemHeightDp", itemHeightDp <= 54f)
    }

    @Test
    fun compactPositionEditorSitsDirectlyAboveWorkspaceNavigation() {
        val model = PlateObject(
            id = PlateObjectId("model-a"),
            source = PlateModelSource(
                file = File(composeRule.activity.cacheDir, "model-a.stl"),
                localBounds = PlateBounds(-10.0, -10.0, 0.0, 10.0, 10.0, 5.0),
            ),
            transform = PlateObjectTransform(
                positionXmm = 110.0,
                positionYmm = 104.5,
                positionZmm = 4.8,
            ),
        )

        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                CompositionLocalProvider(LocalUiLanguage provides UiLanguage.ENGLISH) {
                    MaterialTheme {
                        PositionWorkspaceOverlay(
                            selectedTool = PositionWorkspaceTool.POSITION,
                            selectedModel = model,
                            selectedModelInsideBed = true,
                            hasModels = true,
                            bedWidth = 220.0,
                            bedDepth = 220.0,
                            printableHeight = 250.0,
                            linkScaleAxes = true,
                            isWorking = false,
                            onLinkScaleAxesChange = {},
                            onToolSelected = {},
                            onTransformChange = {},
                            onCenter = {},
                            onPlaceOnBed = {},
                            onRotate90 = {},
                            onResetScale = {},
                            workspaceNavigation = {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(76.dp)
                                        .testTag(TestWorkspaceNavigationTag),
                                )
                            },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("X position").assertIsDisplayed()
        composeRule.onNodeWithText("Y position").assertIsDisplayed()
        composeRule.onNodeWithText("Z position").assertIsDisplayed()
        composeRule.onNodeWithTag(PositionWorkspaceTriggerTestTag).assertIsSelected()

        val editorBounds = composeRule.onNodeWithTag(PositionWorkspaceEditorTestTag)
            .fetchSemanticsNode().boundsInRoot
        val navigationBounds = composeRule.onNodeWithTag(TestWorkspaceNavigationTag)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Position editor overlaps workspace navigation: $editorBounds vs $navigationBounds",
            editorBounds.bottom <= navigationBounds.top,
        )

        val density = composeRule.activity.resources.displayMetrics.density
        assertTrue(
            "Position editor is not compact: ${editorBounds.height / density} dp",
            editorBounds.height / density <= 252f,
        )
    }

    private companion object {
        const val TestWorkspaceNavigationTag = "test-workspace-navigation"
    }
}

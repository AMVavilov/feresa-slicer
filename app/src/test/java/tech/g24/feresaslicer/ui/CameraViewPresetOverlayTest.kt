// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraViewPresetOverlayTest {
    @Test
    fun defaultStateIsCollapsed() {
        assertFalse(CameraViewPresetOverlayUiState().expanded)
    }

    @Test
    fun toggleOpensAndSecondToggleClosesOverlay() {
        val opened = CameraViewPresetOverlayUiState()
            .reduce(CameraViewPresetOverlayEvent.Toggle)
        val closed = opened.reduce(CameraViewPresetOverlayEvent.Toggle)

        assertTrue(opened.expanded)
        assertFalse(closed.expanded)
    }

    @Test
    fun selectionKeepsTheOverlayOpenUntilExplicitlyDismissed() {
        val opened = CameraViewPresetOverlayUiState(expanded = true)

        assertFalse(opened.reduce(CameraViewPresetOverlayEvent.Dismiss).expanded)
        assertTrue(opened.reduce(CameraViewPresetOverlayEvent.ActionSelected).expanded)
        assertFalse(CameraViewPresetOverlayUiState().reduce(CameraViewPresetOverlayEvent.ActionSelected).expanded)
    }

    @Test
    fun actionsContainSevenPresetsThenFitAndBed() {
        val presets = cameraViewOverlayActions.filterIsInstance<CameraViewOverlayAction.SelectPreset>()

        assertEquals(9, cameraViewOverlayActions.size)
        assertEquals(CameraViewPreset.entries, presets.map { it.preset })
        assertEquals(CameraViewOverlayAction.FitModel, cameraViewOverlayActions[7])
        assertEquals(CameraViewOverlayAction.ShowBed, cameraViewOverlayActions[8])
        assertEquals(
            cameraViewOverlayActions.size,
            cameraViewOverlayActions.map(CameraViewOverlayAction::stableKey).distinct().size,
        )
    }

    @Test
    fun freeCameraLeavesEveryPresetUnselected() {
        assertTrue(cameraViewOverlayActions.none { cameraViewOverlayActionIsSelected(it, null) })
        assertEquals(
            listOf(CameraViewPreset.TOP),
            cameraViewOverlayActions
                .filter { cameraViewOverlayActionIsSelected(it, CameraViewPreset.TOP) }
                .filterIsInstance<CameraViewOverlayAction.SelectPreset>()
                .map { it.preset },
        )
    }

    @Test
    fun labelsAreCompleteInRussianAndEnglish() {
        val expectedRussian = listOf(
            "Изометрия", "Сверху", "Снизу", "Спереди", "Сзади", "Слева", "Справа", "Вписать", "Стол",
        )
        val expectedEnglish = listOf(
            "Isometric", "Top", "Bottom", "Front", "Back", "Left", "Right", "Fit", "Bed",
        )

        assertEquals(
            expectedRussian,
            cameraViewOverlayActions.map { cameraViewOverlayVisualLabel(it, UiLanguage.RUSSIAN) },
        )
        assertEquals(
            expectedEnglish,
            cameraViewOverlayActions.map { cameraViewOverlayVisualLabel(it, UiLanguage.ENGLISH) },
        )
    }

    @Test
    fun compactFitAndBedLabelsHaveUnambiguousTalkBackDescriptions() {
        assertEquals(
            "Вписать модель",
            cameraViewOverlayAccessibleLabel(CameraViewOverlayAction.FitModel, UiLanguage.RUSSIAN),
        )
        assertEquals(
            "Fit model",
            cameraViewOverlayAccessibleLabel(CameraViewOverlayAction.FitModel, UiLanguage.ENGLISH),
        )
        assertEquals(
            "Показать стол",
            cameraViewOverlayAccessibleLabel(CameraViewOverlayAction.ShowBed, UiLanguage.RUSSIAN),
        )
        assertEquals(
            "Show bed",
            cameraViewOverlayAccessibleLabel(CameraViewOverlayAction.ShowBed, UiLanguage.ENGLISH),
        )
    }

    @Test
    fun triggerAnnouncesActionAndExpandedStateInBothLanguages() {
        assertEquals("Показать виды камеры", cameraViewOverlayTriggerDescription(false, UiLanguage.RUSSIAN))
        assertEquals("Скрыть виды камеры", cameraViewOverlayTriggerDescription(true, UiLanguage.RUSSIAN))
        assertEquals("Show camera views", cameraViewOverlayTriggerDescription(false, UiLanguage.ENGLISH))
        assertEquals("Hide camera views", cameraViewOverlayTriggerDescription(true, UiLanguage.ENGLISH))
        assertEquals("Свёрнуто", cameraViewOverlayStateDescription(false, UiLanguage.RUSSIAN))
        assertEquals("Expanded", cameraViewOverlayStateDescription(true, UiLanguage.ENGLISH))
    }
}

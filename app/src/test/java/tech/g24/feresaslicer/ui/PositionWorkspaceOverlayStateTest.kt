// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionWorkspaceOverlayStateTest {
    @Test
    fun toolTrayStartsCollapsedAndToggles() {
        val opened = PositionWorkspaceOverlayUiState()
            .reduce(PositionWorkspaceOverlayEvent.ToggleTools)
        val closed = opened.reduce(PositionWorkspaceOverlayEvent.ToggleTools)

        assertTrue(opened.toolsExpanded)
        assertFalse(closed.toolsExpanded)
    }

    @Test
    fun dismissAndSelectionCollapseTheTray() {
        val opened = PositionWorkspaceOverlayUiState(toolsExpanded = true)

        assertFalse(opened.reduce(PositionWorkspaceOverlayEvent.DismissTools).toolsExpanded)
        assertFalse(opened.reduce(PositionWorkspaceOverlayEvent.ToolSelected).toolsExpanded)
    }

    @Test
    fun triggerDescriptionsExposeActionAndStateInBothLanguages() {
        assertEquals(
            "Показать инструменты положения",
            positionWorkspaceOverlayTriggerDescription(false, UiLanguage.RUSSIAN),
        )
        assertEquals(
            "Скрыть инструменты положения",
            positionWorkspaceOverlayTriggerDescription(true, UiLanguage.RUSSIAN),
        )
        assertEquals(
            "Show position tools",
            positionWorkspaceOverlayTriggerDescription(false, UiLanguage.ENGLISH),
        )
        assertEquals(
            "Expanded",
            positionWorkspaceOverlayStateDescription(true, UiLanguage.ENGLISH),
        )
        assertEquals(
            "Инструменты положения модели",
            positionWorkspaceOverlayPanelDescription(UiLanguage.RUSSIAN),
        )
    }
}

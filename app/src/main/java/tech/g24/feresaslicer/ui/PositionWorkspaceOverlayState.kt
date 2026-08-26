// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

/** Events for the collapsible tool tray on the Position workspace. */
internal enum class PositionWorkspaceOverlayEvent {
    ToggleTools,
    DismissTools,
    ToolSelected,
}

/**
 * Pure tray state so outside-tap, Back and selection behavior remain deterministic and testable.
 * The active transform tool is owned by the workspace and deliberately survives tray dismissal.
 */
internal data class PositionWorkspaceOverlayUiState(
    val toolsExpanded: Boolean = false,
) {
    fun reduce(event: PositionWorkspaceOverlayEvent): PositionWorkspaceOverlayUiState = when (event) {
        PositionWorkspaceOverlayEvent.ToggleTools -> copy(toolsExpanded = !toolsExpanded)
        PositionWorkspaceOverlayEvent.DismissTools,
        PositionWorkspaceOverlayEvent.ToolSelected,
        -> copy(toolsExpanded = false)
    }
}

internal fun positionWorkspaceOverlayTriggerDescription(
    expanded: Boolean,
    language: UiLanguage,
): String = when {
    language == UiLanguage.RUSSIAN && expanded -> "Скрыть инструменты положения"
    language == UiLanguage.RUSSIAN -> "Показать инструменты положения"
    expanded -> "Hide position tools"
    else -> "Show position tools"
}

internal fun positionWorkspaceOverlayStateDescription(
    expanded: Boolean,
    language: UiLanguage,
): String = when {
    language == UiLanguage.RUSSIAN && expanded -> "Развёрнуто"
    language == UiLanguage.RUSSIAN -> "Свёрнуто"
    expanded -> "Expanded"
    else -> "Collapsed"
}

internal fun positionWorkspaceOverlayPanelDescription(language: UiLanguage): String =
    if (language == UiLanguage.RUSSIAN) "Инструменты положения модели" else "Model position tools"

// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.g24.feresaslicer.R

internal const val CameraViewOverlayTriggerTestTag = "camera-view-overlay-trigger"
internal const val CameraViewOverlayPanelTestTag = "camera-view-overlay-panel"

/** Actions exposed by the camera overlay. Fit and bed are deliberately not camera presets. */
internal sealed interface CameraViewOverlayAction {
    val stableKey: String

    data class SelectPreset(val preset: CameraViewPreset) : CameraViewOverlayAction {
        override val stableKey: String = "preset:${preset.wireValue}"
    }

    data object FitModel : CameraViewOverlayAction {
        override val stableKey: String = "fit-model"
    }

    data object ShowBed : CameraViewOverlayAction {
        override val stableKey: String = "show-bed"
    }
}

internal val cameraViewOverlayActions: List<CameraViewOverlayAction> =
    CameraViewPreset.entries.map { CameraViewOverlayAction.SelectPreset(it) } +
        listOf(CameraViewOverlayAction.FitModel, CameraViewOverlayAction.ShowBed)

internal enum class CameraViewPresetOverlayEvent {
    Toggle,
    Dismiss,
    ActionSelected,
}

/** Pure state model kept separate so default, Back and selection behavior is testable. */
internal data class CameraViewPresetOverlayUiState(
    val expanded: Boolean = false,
) {
    fun reduce(event: CameraViewPresetOverlayEvent): CameraViewPresetOverlayUiState = when (event) {
        CameraViewPresetOverlayEvent.Toggle -> copy(expanded = !expanded)
        CameraViewPresetOverlayEvent.Dismiss -> copy(expanded = false)
        CameraViewPresetOverlayEvent.ActionSelected -> this
    }
}

/**
 * A full-canvas overlay: while collapsed only the top-right camera button handles input.
 * Selecting a view keeps the tray open so several camera presets can be compared in succession.
 * The tray closes only through its trigger or Android Back, leaving the rest of the canvas free for
 * rotate, pan, zoom, and model selection gestures while it is visible.
 */
@Composable
internal fun CameraViewPresetOverlay(
    selectedPreset: CameraViewPreset?,
    onSelect: (CameraViewPreset) -> Unit,
    onFitModel: () -> Unit,
    onShowBed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var uiState by remember { mutableStateOf(CameraViewPresetOverlayUiState()) }
    val language = currentUiLanguage()
    val panelInteractionSource = remember { MutableInteractionSource() }

    fun dispatch(event: CameraViewPresetOverlayEvent) {
        uiState = uiState.reduce(event)
    }

    fun select(action: CameraViewOverlayAction) {
        dispatch(CameraViewPresetOverlayEvent.ActionSelected)
        when (action) {
            is CameraViewOverlayAction.SelectPreset -> onSelect(action.preset)
            CameraViewOverlayAction.FitModel -> onFitModel()
            CameraViewOverlayAction.ShowBed -> onShowBed()
        }
    }

    BackHandler(enabled = uiState.expanded) {
        dispatch(CameraViewPresetOverlayEvent.Dismiss)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = uiState.expanded,
                modifier = Modifier.weight(1f),
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Keep tray padding inert without affecting gestures outside the tray.
                        .clickable(
                            interactionSource = panelInteractionSource,
                            indication = null,
                            onClick = {},
                        ),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 10.dp,
                    tonalElevation = 4.dp,
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CameraViewOverlayPanelTestTag)
                            .semantics {
                                contentDescription = cameraViewOverlayPanelDescription(language)
                            },
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(cameraViewOverlayActions, key = CameraViewOverlayAction::stableKey) { action ->
                            CameraViewOverlayItem(
                                action = action,
                                selected = cameraViewOverlayActionIsSelected(action, selectedPreset),
                                language = language,
                                onClick = { select(action) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            val triggerDescription = cameraViewOverlayTriggerDescription(uiState.expanded, language)
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .testTag(CameraViewOverlayTriggerTestTag)
                    .clip(RoundedCornerShape(18.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = triggerDescription
                        stateDescription = cameraViewOverlayStateDescription(uiState.expanded, language)
                    }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = triggerDescription,
                        onClick = { dispatch(CameraViewPresetOverlayEvent.Toggle) },
                    ),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 10.dp,
                tonalElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_workspace_camera),
                        contentDescription = null,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
    }
}

internal fun cameraViewOverlayActionIsSelected(
    action: CameraViewOverlayAction,
    selectedPreset: CameraViewPreset?,
): Boolean = action is CameraViewOverlayAction.SelectPreset && action.preset == selectedPreset

@Composable
private fun CameraViewOverlayItem(
    action: CameraViewOverlayAction,
    selected: Boolean,
    language: UiLanguage,
    onClick: () -> Unit,
) {
    val visualLabel = cameraViewOverlayVisualLabel(action, language)
    val accessibleLabel = cameraViewOverlayAccessibleLabel(action, language)
    Surface(
        modifier = Modifier
            .width(76.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibleLabel
                this.selected = selected
            }
            .clickable(
                role = Role.Button,
                onClickLabel = accessibleLabel,
                onClick = onClick,
            ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(15.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(cameraViewOverlayIcon(action)),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = visualLabel,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun cameraViewOverlayIcon(action: CameraViewOverlayAction): Int = when (action) {
    is CameraViewOverlayAction.SelectPreset -> R.drawable.ic_workspace_camera
    CameraViewOverlayAction.FitModel -> R.drawable.ic_nav_model
    CameraViewOverlayAction.ShowBed -> R.drawable.ic_camera_fit_bed
}

internal fun cameraViewOverlayVisualLabel(
    action: CameraViewOverlayAction,
    language: UiLanguage,
): String = when (action) {
    is CameraViewOverlayAction.SelectPreset -> cameraViewOverlayPresetLabel(action.preset, language)
    CameraViewOverlayAction.FitModel -> if (language == UiLanguage.RUSSIAN) "Вписать" else "Fit"
    CameraViewOverlayAction.ShowBed -> if (language == UiLanguage.RUSSIAN) "Стол" else "Bed"
}

internal fun cameraViewOverlayAccessibleLabel(
    action: CameraViewOverlayAction,
    language: UiLanguage,
): String = when (action) {
    is CameraViewOverlayAction.SelectPreset -> cameraViewOverlayPresetLabel(action.preset, language)
    CameraViewOverlayAction.FitModel -> if (language == UiLanguage.RUSSIAN) {
        "Вписать модель"
    } else {
        "Fit model"
    }
    CameraViewOverlayAction.ShowBed -> if (language == UiLanguage.RUSSIAN) {
        "Показать стол"
    } else {
        "Show bed"
    }
}

internal fun cameraViewOverlayPresetLabel(
    preset: CameraViewPreset,
    language: UiLanguage,
): String = when (preset) {
    CameraViewPreset.ISOMETRIC -> if (language == UiLanguage.RUSSIAN) "Изометрия" else "Isometric"
    CameraViewPreset.TOP -> if (language == UiLanguage.RUSSIAN) "Сверху" else "Top"
    CameraViewPreset.BOTTOM -> if (language == UiLanguage.RUSSIAN) "Снизу" else "Bottom"
    CameraViewPreset.FRONT -> if (language == UiLanguage.RUSSIAN) "Спереди" else "Front"
    CameraViewPreset.BACK -> if (language == UiLanguage.RUSSIAN) "Сзади" else "Back"
    CameraViewPreset.LEFT -> if (language == UiLanguage.RUSSIAN) "Слева" else "Left"
    CameraViewPreset.RIGHT -> if (language == UiLanguage.RUSSIAN) "Справа" else "Right"
}

internal fun cameraViewOverlayTriggerDescription(
    expanded: Boolean,
    language: UiLanguage,
): String = when {
    language == UiLanguage.RUSSIAN && expanded -> "Скрыть виды камеры"
    language == UiLanguage.RUSSIAN -> "Показать виды камеры"
    expanded -> "Hide camera views"
    else -> "Show camera views"
}

internal fun cameraViewOverlayStateDescription(
    expanded: Boolean,
    language: UiLanguage,
): String = when {
    language == UiLanguage.RUSSIAN && expanded -> "Развёрнуто"
    language == UiLanguage.RUSSIAN -> "Свёрнуто"
    expanded -> "Expanded"
    else -> "Collapsed"
}

internal fun cameraViewOverlayPanelDescription(language: UiLanguage): String =
    if (language == UiLanguage.RUSSIAN) "Варианты вида камеры" else "Camera view options"

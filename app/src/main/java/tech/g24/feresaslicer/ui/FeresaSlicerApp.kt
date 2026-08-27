// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import tech.g24.feresaslicer.slicer.OrcaMachineFilamentScalars
import tech.g24.feresaslicer.slicer.OrcaPlateSliceFiles
import tech.g24.feresaslicer.slicer.OrcaPlateSlicePipeline
import tech.g24.feresaslicer.slicer.OrcaProfileSettingsResolver
import tech.g24.feresaslicer.slicer.OrcaProcessSettingsPayload
import tech.g24.feresaslicer.slicer.OrcaSelectedProfiles
import tech.g24.feresaslicer.slicer.OrcaSystemPresetCatalog
import tech.g24.feresaslicer.slicer.SliceReport
import tech.g24.feresaslicer.slicer.SlicerSettings
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import tech.g24.feresaslicer.auth.OrcaAuthProvider
import tech.g24.feresaslicer.auth.OrcaAuthMode
import tech.g24.feresaslicer.auth.OrcaAuthState
import tech.g24.feresaslicer.auth.OrcaAuthViewModel
import tech.g24.feresaslicer.R
import tech.g24.feresaslicer.BuildConfig
import tech.g24.feresaslicer.auth.OrcaCloudProfile
import tech.g24.feresaslicer.auth.OrcaPrinterConnection
import tech.g24.feresaslicer.auth.OrcaProfileSyncState
import tech.g24.feresaslicer.auth.OrcaProfileOrigin
import tech.g24.feresaslicer.auth.OrcaProfileType
import tech.g24.feresaslicer.auth.printerConnection
import tech.g24.feresaslicer.catalog.OrcaSystemPrinterCatalog
import tech.g24.feresaslicer.catalog.OrcaSystemPrinterProfile
import tech.g24.feresaslicer.catalog.OrcaPrinterCatalogSelection
import tech.g24.feresaslicer.catalog.filterPrinters
import tech.g24.feresaslicer.printer.NetworkPrinterClient
import tech.g24.feresaslicer.printer.ManualPrinterConnectionDraft
import tech.g24.feresaslicer.printer.ManualPrinterConnectionStore
import tech.g24.feresaslicer.printer.PrinterConnectionService
import tech.g24.feresaslicer.printer.PrinterConnectionTestResult
import tech.g24.feresaslicer.printer.PrinterJobState
import tech.g24.feresaslicer.printer.PrinterOperationalState
import tech.g24.feresaslicer.printer.PrinterStatus
import tech.g24.feresaslicer.printer.SavedManualPrinterConnection
import tech.g24.feresaslicer.modelimport.ModelFileImporter
import tech.g24.feresaslicer.modelimport.ExternalModelOpenRequests
import tech.g24.feresaslicer.modelimport.ModelDocumentPolicy
import tech.g24.feresaslicer.plate.PlateBounds
import tech.g24.feresaslicer.plate.PlateAxis
import tech.g24.feresaslicer.plate.PlateModelSource
import tech.g24.feresaslicer.plate.PlateObject
import tech.g24.feresaslicer.plate.PlateObjectId
import tech.g24.feresaslicer.plate.PlateObjectTransform
import tech.g24.feresaslicer.plate.PlateWorkspace
import tech.g24.feresaslicer.plate.RectangularBuildVolume
import tech.g24.feresaslicer.slicer.StlPlateComposer
import tech.g24.feresaslicer.slicer.StlPlatePlacement

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B57),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F4EA),
    onPrimaryContainer = Color(0xFF002019),
    background = Color(0xFFF6F7F2),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFE2E5E0),
    onSurfaceVariant = Color(0xFF5D655F),
    outlineVariant = Color(0xFFDDE1DA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF62DBC0),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFF82F8DC),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF1A1D1B),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF282D2A),
    onSurfaceVariant = Color(0xFFBEC9C2),
    outlineVariant = Color(0xFF3E4843),
)

internal const val ModelSliceActionTestTag = "model-slice-action"
internal const val ModelSliceResultTestTag = "model-slice-result"
internal const val ModelToolpathViewerTestTag = "model-toolpath-viewer"
internal const val PositionWorkspaceEditorTestTag = "position-workspace-editor"
internal const val PositionWorkspaceTriggerTestTag = "position-workspace-trigger"
internal const val ModelWorkspaceNavigationTestTag = "model-workspace-navigation"
internal val RenderedToolpathSegmentsKey = SemanticsPropertyKey<Long>("RenderedToolpathSegments")
internal var SemanticsPropertyReceiver.renderedToolpathSegments by RenderedToolpathSegmentsKey

private enum class AppThemeMode(val label: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

private const val ThemePreferences = "feresa_slicer_preferences"
private const val ThemeModeKey = "theme_mode"
private const val LanguageModeKey = "language_mode"

internal fun <T> currentSliceArtifact(
    value: T?,
    producedGeneration: Long?,
    currentGeneration: Long,
): T? = value?.takeIf { producedGeneration == currentGeneration }

private val Surface: Color
    @Composable get() = MaterialTheme.colorScheme.surface

private val Accent: Color
    @Composable get() = MaterialTheme.colorScheme.primary

private val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

private enum class AppDestination(
    val title: String,
    val subtitle: String,
) {
    MODEL("Модель", "Модель и печатный стол"),
    PRINTER("Принтер", "Профиль принтера"),
    FILAMENT("Филамент", "Профиль материала"),
    PRINT("Печать", "Параметры печати"),
    APP("Приложение", "Настройки и синхронизация"),
}

private enum class ModelWorkspaceSection(val label: String, val icon: Int) {
    FILE("Файл", R.drawable.ic_nav_project),
    VIEW("Вид", R.drawable.ic_workspace_camera),
    POSITION("Положение", R.drawable.ic_nav_print),
    SLICE("Нарезка", R.drawable.ic_workspace_slice),
}

internal enum class PositionWorkspaceTool(val label: String, val icon: Int) {
    ARRANGE("Расставить", R.drawable.ic_nav_model),
    AUTO_ORIENT("Автоориент. (бета)", R.drawable.ic_nav_print),
    POSITION("Позиция", R.drawable.ic_nav_profiles),
    ROTATION("Поворот", R.drawable.ic_nav_print),
    SCALE("Масштаб", R.drawable.ic_nav_profiles),
    LAY_FLAT("Крупнейшей гранью", R.drawable.ic_nav_model),
}

private data class SuggestedModelOrientation(
    val rotationXDegrees: Double,
    val rotationYDegrees: Double,
    val rotationZDegrees: Double,
    val positionZmm: Double,
)

private enum class ProfileSection(val label: String) {
    PRINTER("Printer"),
    FILAMENT("Filament"),
    PROCESS("Process"),
}

private data class PrinterDialogResult(
    val title: String,
    val message: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeresaSlicerApp(initialPlateWorkspace: PlateWorkspace? = null) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val modelOperationMutex = remember { Mutex() }

    suspend fun <T> withExclusiveModelOperation(block: suspend () -> T): T {
        modelOperationMutex.lock()
        return try {
            block()
        } finally {
            modelOperationMutex.unlock()
        }
    }
    val slicerSettingsStore = remember(applicationContext) {
        SlicerSettingsStore(applicationContext)
    }
    val restoredSlicerSettings = remember(applicationContext) {
        slicerSettingsStore.read()
    }
    val themePreferences = remember(context) {
        context.getSharedPreferences(ThemePreferences, Context.MODE_PRIVATE)
    }
    var themeMode by remember {
        mutableStateOf(
            runCatching {
                AppThemeMode.valueOf(themePreferences.getString(ThemeModeKey, null) ?: AppThemeMode.SYSTEM.name)
            }.getOrDefault(AppThemeMode.SYSTEM),
        )
    }
    var uiLanguage by remember {
        val defaultLanguage = resolveUiLanguage(Locale.getDefault())
        mutableStateOf(
            runCatching {
                UiLanguage.valueOf(
                    themePreferences.getString(LanguageModeKey, null) ?: defaultLanguage.name,
                )
            }.getOrDefault(defaultLanguage),
        )
    }
    val useDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val appColorScheme = if (useDarkTheme) DarkColors else LightColors
    val activity = context as? Activity
    SideEffect {
        activity?.window?.let { window ->
            window.statusBarColor = appColorScheme.background.toArgb()
            window.navigationBarColor = appColorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }
    var plateWorkspace by remember {
        mutableStateOf(initialPlateWorkspace ?: PlateWorkspace.empty())
    }
    var generatedGcode by remember { mutableStateOf<File?>(null) }
    var generatedGcodeGeneration by remember { mutableStateOf<Long?>(null) }
    var report by remember { mutableStateOf<SliceReport?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var sliceGeneration by remember { mutableStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var viewerMessage by remember { mutableStateOf<String?>(null) }
    var showLicense by remember { mutableStateOf(false) }
    var showSendToPrinter by remember { mutableStateOf(false) }
    var isSendingToPrinter by remember { mutableStateOf(false) }
    var isTestingPrinter by remember { mutableStateOf(false) }
    var printerProbeRequestId by remember { mutableStateOf(0L) }
    var printerConnectionStatus by remember { mutableStateOf<String?>(null) }
    var printerConnectionTestResult by remember { mutableStateOf<PrinterConnectionTestResult?>(null) }
    var isTestingSendPrinter by remember { mutableStateOf(false) }
    var sendPrinterProbeRequestId by remember { mutableStateOf(0L) }
    var sendPrinterConnectionStatus by remember { mutableStateOf<String?>(null) }
    var sendPrinterConnectionTestResult by remember { mutableStateOf<PrinterConnectionTestResult?>(null) }
    var printerDialogResult by remember { mutableStateOf<PrinterDialogResult?>(null) }
    var viewerSceneState by remember { mutableStateOf<ViewerSceneState?>(null) }
    var viewerMode by remember { mutableStateOf(ViewerMode.MODEL) }
    var bedWidth by remember { mutableStateOf(restoredSlicerSettings.bedWidth) }
    var bedDepth by remember { mutableStateOf(restoredSlicerSettings.bedDepth) }
    var printableHeight by remember { mutableStateOf(restoredSlicerSettings.printableHeight) }
    var printerFirmware by remember { mutableStateOf(restoredSlicerSettings.printerFirmware) }
    var systemPresetCatalog by remember(applicationContext) {
        mutableStateOf<OrcaSystemPresetCatalog?>(null)
    }
    var compatibleSystemPrinterCatalog by remember(applicationContext) {
        mutableStateOf(OrcaSystemPrinterCatalog())
    }
    var isSystemCatalogLoading by remember(applicationContext) { mutableStateOf(true) }
    val authViewModel: OrcaAuthViewModel = viewModel()
    val authState by authViewModel.state.collectAsState()
    val cloudProfileState by authViewModel.profileState.collectAsState()
    val isReviewerDemo = (authState as? OrcaAuthState.SignedIn)?.mode == OrcaAuthMode.REVIEW_DEMO
    var destination by remember { mutableStateOf(AppDestination.MODEL) }
    var modelWorkspaceSection by remember {
        mutableStateOf(
            if (initialPlateWorkspace?.objects.isNullOrEmpty()) {
                ModelWorkspaceSection.FILE
            } else {
                ModelWorkspaceSection.SLICE
            },
        )
    }
    var renameObjectId by remember { mutableStateOf<PlateObjectId?>(null) }
    var renameObjectValue by remember { mutableStateOf("") }
    var linkScaleAxes by remember { mutableStateOf(true) }
    var modelActionMessage by remember { mutableStateOf<String?>(null) }
    var cameraResetRequest by remember { mutableStateOf(0) }
    var selectedCameraViewPreset by remember { mutableStateOf<CameraViewPreset?>(CameraViewPreset.ISOMETRIC) }
    var cameraViewRequest by remember { mutableStateOf<CameraViewRequest?>(null) }
    var cameraViewRequestId by remember { mutableStateOf(0) }
    var cameraFramingRequest by remember { mutableStateOf<CameraFramingRequest?>(null) }
    var cameraFramingRequestId by remember { mutableStateOf(0) }
    var viewerCameraState by remember { mutableStateOf<ViewerCameraState?>(null) }
    var selectedPositionTool by remember { mutableStateOf<PositionWorkspaceTool?>(null) }
    var toolpathMinimumLayer by remember { mutableStateOf(0) }
    var toolpathMaximumLayer by remember { mutableStateOf(0) }
    var toolpathColorMode by remember { mutableStateOf(ToolpathColorMode.LINE_WIDTH) }
    var toolpathProgress by remember { mutableStateOf(1f) }
    var showExtrusionMoves by remember { mutableStateOf(true) }
    var showTravelMoves by remember { mutableStateOf(false) }

    val selectedPlateObject = plateWorkspace.selectedObject
    val selectedFile = selectedPlateObject?.source?.file
    val selectedName = selectedPlateObject?.sourceName
    val modelTransform = selectedPlateObject?.transform?.toViewerTransform()
        ?: ModelTransform(positionX = bedWidth / 2.0, positionY = bedDepth / 2.0)
    val viewerModelObjects = plateWorkspace.objects.map { model ->
        ViewerModelObject(
            objectId = model.id.value,
            file = model.source.file,
            transform = model.transform.toViewerTransform(),
        )
    }
    val hasPlateModels = plateWorkspace.objects.isNotEmpty()

    fun invalidatePlateSlice() {
        sliceGeneration += 1L
        generatedGcode = null
        generatedGcodeGeneration = null
        report = null
        viewerSceneState = null
        viewerMode = ViewerMode.MODEL
    }

    val currentGeneratedGcode = currentSliceArtifact(
        value = generatedGcode,
        producedGeneration = generatedGcodeGeneration,
        currentGeneration = sliceGeneration,
    )

    fun updateSelectedTransform(update: (PlateObjectTransform) -> PlateObjectTransform) {
        val selectedId = plateWorkspace.selectedObjectId ?: return
        plateWorkspace = plateWorkspace.updateTransform(selectedId, update)
        invalidatePlateSlice()
        modelActionMessage = null
    }

    fun commitPlateWorkspace(updated: PlateWorkspace, message: String? = null) {
        plateWorkspace = updated
        invalidatePlateSlice()
        viewerMessage = null
        modelActionMessage = message
    }

    fun commitPlateWorkspaceOnExactBed(
        updated: PlateWorkspace,
        objectId: PlateObjectId,
        message: String,
    ) {
        scope.launch {
            withExclusiveModelOperation {
                isWorking = true
                try {
                    val exactWorkspace = withContext(Dispatchers.Default) {
                        updated.moveObjectToExactBed(objectId)
                    }
                    commitPlateWorkspace(exactWorkspace, message)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    errorMessage = error.message ?: "Не удалось разместить модель на столе"
                } finally {
                    isWorking = false
                }
            }
        }
    }

    fun orientSelectedModel(
        useBasicAutoOrientation: Boolean,
        centerAfterOrientation: Boolean,
        message: String,
    ) {
        val selectedModel = plateWorkspace.selectedObject ?: return
        val selectedId = selectedModel.id
        val selectedFile = selectedModel.source.file
        val buildVolume = RectangularBuildVolume(bedWidth, bedDepth, printableHeight)
        scope.launch {
            withExclusiveModelOperation {
                isWorking = true
                try {
                    runCatching {
                    val orientation = withContext(Dispatchers.Default) {
                        if (useBasicAutoOrientation) {
                            StlPlateComposer.suggestBasicAutoOrientation(
                                file = selectedFile,
                                bedWidthMm = bedWidth,
                                bedDepthMm = bedDepth,
                                maximumHeightMm = printableHeight,
                            )?.let {
                                SuggestedModelOrientation(
                                    rotationXDegrees = it.rotationXDegrees,
                                    rotationYDegrees = it.rotationYDegrees,
                                    rotationZDegrees = it.rotationZDegrees,
                                    positionZmm = it.positionZmm,
                                )
                            }
                        } else {
                            StlPlateComposer.suggestLayFlat(selectedFile)?.let {
                                SuggestedModelOrientation(
                                    rotationXDegrees = it.rotationXDegrees,
                                    rotationYDegrees = it.rotationYDegrees,
                                    rotationZDegrees = it.rotationZDegrees,
                                    positionZmm = it.positionZmm,
                                )
                            }
                        } ?: error("Не удалось найти подходящую ориентацию")
                    }

                    // Apply the computed orientation to the latest plate snapshot. This preserves
                    // a selection change that may happen while the geometry heuristic is running.
                    val currentWorkspace = plateWorkspace
                    val currentModel = currentWorkspace.objectOrNull(selectedId)
                        ?: error("Модель больше не находится на столе")
                    require(currentModel.source.file == selectedFile) {
                        "Исходная модель изменилась во время автоориентации"
                    }
                    var orientedWorkspace = currentWorkspace.updateTransform(selectedId) {
                        it.copy(
                            rotationXDegrees = orientation.rotationXDegrees,
                            rotationYDegrees = orientation.rotationYDegrees,
                            rotationDegrees = orientation.rotationZDegrees,
                            positionZmm = orientation.positionZmm,
                        )
                    }
                    if (centerAfterOrientation) {
                        orientedWorkspace = orientedWorkspace.center(selectedId, buildVolume)
                    }
                    withContext(Dispatchers.Default) {
                        orientedWorkspace.moveObjectToExactBed(selectedId)
                    }
                    }.onSuccess { exactWorkspace ->
                        commitPlateWorkspace(exactWorkspace, message)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        errorMessage = error.message ?: "Не удалось ориентировать модель"
                    }
                } finally {
                    isWorking = false
                }
            }
        }
    }

    fun applyViewerSelection(selection: ViewerObjectSelection) {
        val selectedId = selection.objectId?.let(::PlateObjectId)
        if (selectedId == null || plateWorkspace.objectOrNull(selectedId) != null) {
            plateWorkspace = plateWorkspace.select(selectedId)
            viewerSceneState = null
            when (
                positionControlsViewerSelectionEffect(
                    selection = selection,
                    positionWorkspaceActive = destination == AppDestination.MODEL &&
                        modelWorkspaceSection == ModelWorkspaceSection.POSITION,
                )
            ) {
                PositionControlsViewerSelectionEffect.NONE -> Unit
                PositionControlsViewerSelectionEffect.OPEN_POSITION -> {
                    selectedPositionTool = PositionWorkspaceTool.POSITION
                }

                PositionControlsViewerSelectionEffect.CLOSE -> {
                    selectedPositionTool = null
                }
            }
        }
    }

    var printSettings by remember { mutableStateOf(restoredSlicerSettings.printSettings) }
    var dirtyProcessSettingKeys by remember {
        mutableStateOf(restoredSlicerSettings.dirtyProcessSettingKeys)
    }
    var printDetailLevel by remember { mutableStateOf(restoredSlicerSettings.printDetailLevel) }
    var printSettingsCategory by remember { mutableStateOf(restoredSlicerSettings.printSettingsCategory) }
    val layerHeight = printSettings.layerHeight
    val printSpeed = printSettings.printSpeed
    var nozzleDiameter by remember { mutableStateOf(restoredSlicerSettings.nozzleDiameter) }
    var filamentDiameter by remember { mutableStateOf(restoredSlicerSettings.filamentDiameter) }
    var nozzleTemperature by remember { mutableStateOf(restoredSlicerSettings.nozzleTemperature) }
    var bedTemperature by remember { mutableStateOf(restoredSlicerSettings.bedTemperature) }
    var printerProfileName by remember { mutableStateOf(restoredSlicerSettings.printerProfileName) }
    var filamentProfileName by remember { mutableStateOf(restoredSlicerSettings.filamentProfileName) }
    var processProfileName by remember { mutableStateOf(restoredSlicerSettings.processProfileName) }
    var selectedPrinterProfileRef by remember {
        mutableStateOf(restoredSlicerSettings.printerProfileRef)
    }
    var selectedFilamentProfileRef by remember {
        mutableStateOf(restoredSlicerSettings.filamentProfileRef)
    }
    var selectedProcessProfileRef by remember {
        mutableStateOf(restoredSlicerSettings.processProfileRef)
    }
    val manualPrinterConnectionStore = remember(applicationContext) {
        ManualPrinterConnectionStore(applicationContext)
    }
    var savedManualPrinterConnection by remember(applicationContext) {
        mutableStateOf(manualPrinterConnectionStore.read())
    }

    val activePrinterProfile = resolvePersistedProfileRef(
        reference = selectedPrinterProfileRef,
        authState = authState,
        cloudProfiles = cloudProfileState.profiles,
        cloudProfileOwnerAccountId = cloudProfileState.ownerAccountId,
        systemCatalog = systemPresetCatalog,
    )
    val activeFilamentProfile = resolvePersistedProfileRef(
        reference = selectedFilamentProfileRef,
        authState = authState,
        cloudProfiles = cloudProfileState.profiles,
        cloudProfileOwnerAccountId = cloudProfileState.ownerAccountId,
        systemCatalog = systemPresetCatalog,
    )
    val activeProcessProfile = resolvePersistedProfileRef(
        reference = selectedProcessProfileRef,
        authState = authState,
        cloudProfiles = cloudProfileState.profiles,
        cloudProfileOwnerAccountId = cloudProfileState.ownerAccountId,
        systemCatalog = systemPresetCatalog,
    )
    fun currentSlicerSettingsSnapshot() = PersistedSlicerSettings(
        printSettings = printSettings,
        dirtyProcessSettingKeys = dirtyProcessSettingKeys,
        printDetailLevel = printDetailLevel,
        printSettingsCategory = printSettingsCategory,
        nozzleDiameter = nozzleDiameter,
        filamentDiameter = filamentDiameter,
        nozzleTemperature = nozzleTemperature,
        bedTemperature = bedTemperature,
        bedWidth = bedWidth,
        bedDepth = bedDepth,
        printableHeight = printableHeight,
        printerFirmware = printerFirmware,
        printerProfileName = printerProfileName,
        filamentProfileName = filamentProfileName,
        processProfileName = processProfileName,
        printerProfileRef = selectedPrinterProfileRef,
        filamentProfileRef = selectedFilamentProfileRef,
        processProfileRef = selectedProcessProfileRef,
    )

    val latestSlicerSettings = rememberUpdatedState(currentSlicerSettingsSnapshot())
    val slicerSettingsWriteQueue = remember(slicerSettingsStore) {
        SlicerSettingsWriteQueue(
            writeSettings = slicerSettingsStore::write,
            onFailure = { Log.e("FeresaSettings", "Could not persist slicer settings", it) },
        )
    }
    LaunchedEffect(slicerSettingsWriteQueue) {
        snapshotFlow { currentSlicerSettingsSnapshot() }
            .distinctUntilChanged()
            .collect(slicerSettingsWriteQueue::enqueue)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, slicerSettingsWriteQueue) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                slicerSettingsWriteQueue.enqueue(latestSlicerSettings.value)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    DisposableEffect(slicerSettingsWriteQueue) {
        onDispose { slicerSettingsWriteQueue.close(latestSlicerSettings.value) }
    }
    val profilePrinterConnection = activePrinterProfile?.printerConnection()
    // Google Play review mode is deliberately air-gapped: even a connection previously saved on
    // the device is ignored until the reviewer leaves the local demo.
    val activePrinterConnection = if (isReviewerDemo) {
        null
    } else {
        savedManualPrinterConnection
            ?.takeIf(SavedManualPrinterConnection::isActive)
            ?.connection
            ?: profilePrinterConnection
    }
    val activePrinterConnectionSource = when {
        isReviewerDemo -> null
        savedManualPrinterConnection?.isActive == true -> "Ручное подключение"
        profilePrinterConnection != null -> "Профиль OrcaCloud"
        else -> null
    }

    LaunchedEffect(activePrinterConnection) {
        printerProbeRequestId += 1L
        isTestingPrinter = false
        printerConnectionTestResult = null
        printerConnectionStatus = null
    }

    LaunchedEffect(applicationContext) {
        val loadedCatalogs = runCatching {
            withContext(Dispatchers.IO) {
                val printerCatalog = OrcaSystemPrinterCatalog.load(applicationContext)
                val presetCatalog = OrcaSystemPresetCatalog.load(applicationContext)
                val compatiblePrinters = printerCatalog.filterPrinters { profile ->
                    presetCatalog.hasBundledProfile(
                        type = OrcaProfileType.PRINTER,
                        name = profile.name,
                        contextHint = "${profile.vendor} ${profile.family} ${profile.model}",
                    )
                }
                presetCatalog to compatiblePrinters
            }
        }
        loadedCatalogs.onSuccess { (presetCatalog, printerCatalog) ->
            systemPresetCatalog = presetCatalog
            compatibleSystemPrinterCatalog = printerCatalog
        }.onFailure { error ->
            errorMessage = error.message ?: "Не удалось загрузить системные профили Orca"
        }
        isSystemCatalogLoading = false
    }

    LaunchedEffect(cloudProfileState.profiles, systemPresetCatalog) {
        val connectedProfile = cloudProfileState.profiles
            .firstOrNull { it.printerConnection()?.hostType?.canSendGcode == true }
        val presetCatalog = systemPresetCatalog ?: return@LaunchedEffect
        if (printerProfileName == "Generic 220" && selectedPrinterProfileRef == null) {
            connectedProfile?.let { profile ->
                runCatching {
                    resolveProfileSettingsForUi(
                        catalog = presetCatalog,
                        profile = profile,
                        availableProfiles = cloudProfileState.profiles,
                    )
                }.onSuccess { resolved ->
                    printerProfileName = profile.name
                    selectedPrinterProfileRef = profile.toPersistedCloudRef(
                        authState = authState,
                        cachedOwnerAccountId = cloudProfileState.ownerAccountId,
                    )
                    firstProfileSetting(resolved, "nozzle_diameter")?.let { nozzleDiameter = it }
                    printerDimensions(resolved)?.let { (width, depth) ->
                        bedWidth = width
                        bedDepth = depth
                        plateWorkspace.selectedObjectId?.let { selectedId ->
                            plateWorkspace = plateWorkspace.updateTransform(selectedId) {
                                it.copy(positionXmm = width / 2.0, positionYmm = depth / 2.0)
                            }
                        }
                    }
                    firstProfileSetting(resolved, "printable_height", "max_print_height")
                        ?.toDoubleOrNull()
                        ?.takeIf { it > 0.0 }
                        ?.let { printableHeight = it }
                    firstProfileSetting(resolved, "gcode_flavor")?.let { printerFirmware = it }
                    printerConnectionStatus = "Подключение загружено из OrcaCloud"
                    invalidatePlateSlice()
                }.onFailure { error ->
                    errorMessage = error.message ?: "Не удалось разрешить профиль принтера Orca"
                }
            }
        }
    }

    // A sync, sign-out, or parent-profile update can change the effective native config even when
    // the selected profile name stays the same. Never keep a slice produced from the old snapshot.
    LaunchedEffect(cloudProfileState.profiles) {
        invalidatePlateSlice()
    }

    val importModelUris: suspend (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            withExclusiveModelOperation {
                try {
                    isWorking = true
                    errorMessage = null
                    val importedModels = withContext(Dispatchers.IO) {
                        uris.map { uri ->
                            val id = PlateObjectId.newId()
                            id to copyModelToCache(context, uri, id)
                        }
                    }
                    var updatedWorkspace = plateWorkspace
                    val buildVolume = RectangularBuildVolume(bedWidth, bedDepth, printableHeight)
                    importedModels.forEach { (id, source) ->
                        val transform = findInitialPlateTransform(updatedWorkspace, source, buildVolume)
                        updatedWorkspace = updatedWorkspace.add(source, id, transform, select = true)
                    }
                    plateWorkspace = updatedWorkspace
                    destination = AppDestination.MODEL
                    modelWorkspaceSection = ModelWorkspaceSection.VIEW
                    invalidatePlateSlice()
                    viewerMessage = null
                    modelActionMessage = "Добавлено моделей: ${importedModels.size}"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    errorMessage = error.message ?: "Не удалось импортировать выбранный файл"
                } finally {
                    isWorking = false
                }
            }
        }
    }
    val latestImportModelUris by rememberUpdatedState(importModelUris)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch { latestImportModelUris(uris) }
    }

    LaunchedEffect(Unit) {
        ExternalModelOpenRequests.requests.collect { request ->
            latestImportModelUris(listOf(request.uri))
        }
    }

    val gcodeSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x.gcode")
    ) { uri ->
        val source = currentGeneratedGcode
        if (uri != null && source != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            source.inputStream().use { input -> input.copyTo(output) }
                        } ?: error("Не удалось открыть выбранную папку")
                    }
                }.onFailure { error ->
                    errorMessage = error.message ?: "Не удалось сохранить G-code"
                }
            }
        }
    }

    val removeCurrentModel: () -> Unit = {
        plateWorkspace.selectedObjectId?.let { selectedId ->
            plateWorkspace = plateWorkspace.remove(selectedId)
        }
        if (plateWorkspace.objects.isEmpty()) {
            modelWorkspaceSection = ModelWorkspaceSection.FILE
            selectedPositionTool = null
            viewerCameraState = null
        }
        invalidatePlateSlice()
        viewerMessage = null
        modelActionMessage = null
    }

    val saveCurrentGcode: () -> Unit = {
        val defaultName = report
            ?.recommendedFileName
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { File(it).name }
            ?: selectedName
                ?.substringBeforeLast('.')
                ?.plus(".gcode")
            ?: "feresa-slicer.gcode"
        gcodeSaver.launch(defaultName)
    }

    val sliceCurrentModel: () -> Unit = slice@{
        if (isWorking) return@slice
        val plateSnapshot = plateWorkspace
        val presetCatalogSnapshot = systemPresetCatalog
        if (plateSnapshot.objects.isEmpty()) return@slice
        val plateValidation = plateSnapshot.validate(
            RectangularBuildVolume(bedWidth, bedDepth, printableHeight),
        )
        if (!plateValidation.insideBuildVolume) {
            errorMessage = "Один или несколько объектов выходят за пределы области печати"
            return@slice
        }
        val settings = parseSettings(
            layerHeight,
            nozzleDiameter,
            filamentDiameter,
            nozzleTemperature,
            bedTemperature,
            printSpeed,
            bedWidth,
            bedDepth,
            modelTransform,
        )
        if (settings == null) {
            errorMessage = "Проверьте числовые параметры печати"
            return@slice
        }
        sliceGeneration += 1L
        val generationSnapshot = sliceGeneration
        val runToken = UUID.randomUUID().toString()

        scope.launch {
            withExclusiveModelOperation {
                if (sliceGeneration != generationSnapshot) return@withExclusiveModelOperation
                isWorking = true
                errorMessage = null
                report = null
                generatedGcode = null
                generatedGcodeGeneration = null
                viewerMode = ViewerMode.MODEL
                val outputFile = File(context.cacheDir, "feresa-slicer-output-$runToken.gcode")
                val configFile = File(context.cacheDir, "feresa-orca-$runToken.ini")
                val plateFile = File(context.cacheDir, "feresa-slicer-plate-$runToken.stl")
                try {
                    runCatching {
                withContext(Dispatchers.Default) {
                    val completeProcessPayload = printSettings.toOrcaProcessSettingsPayload()
                    val liveProcessPayload = if (activeProcessProfile == null) {
                        completeProcessPayload
                    } else {
                        OrcaProcessSettingsPayload.from(
                            completeProcessPayload.asMap().filterKeys(dirtyProcessSettingKeys::contains),
                        )
                    }
                    val selectedProfiles = OrcaSelectedProfiles(
                        printer = activePrinterProfile,
                        filament = activeFilamentProfile,
                        process = activeProcessProfile,
                        availableCloudProfiles = cloudProfileState.profiles,
                    )
                    val hydratedProfiles = if (
                        selectedProfiles.printer != null ||
                        selectedProfiles.filament != null ||
                        selectedProfiles.process != null
                    ) {
                        (presetCatalogSnapshot
                            ?: error("Системные профили Orca ещё загружаются"))
                            .augment(selectedProfiles)
                    } else {
                        selectedProfiles
                    }
                    val machineFilament = OrcaMachineFilamentScalars(
                        bedWidthMm = bedWidth,
                        bedDepthMm = bedDepth,
                        printableHeightMm = printableHeight,
                        nozzleDiameterMm = nozzleDiameter,
                        filamentDiameterMm = filamentDiameter,
                        nozzleTemperatureC = nozzleTemperature,
                        bedTemperatureC = bedTemperature,
                        gcodeFlavor = printerFirmware,
                    )
                    OrcaPlateSlicePipeline.slice(
                        placements = plateSnapshot.objects.map { it.toStlPlatePlacement() },
                        profiles = hydratedProfiles,
                        machineFilament = machineFilament,
                        liveProcessSettings = liveProcessPayload,
                        baseSettings = settings,
                        files = OrcaPlateSliceFiles(
                            config = configFile,
                            composedPlate = plateFile,
                            gcode = outputFile,
                        ),
                        modelNames = plateSnapshot.objects.map(PlateObject::sourceName),
                    ).report
                }
                    }.onSuccess { result ->
                        if (sliceGeneration != generationSnapshot) {
                            outputFile.delete()
                            return@onSuccess
                        }
                        report = result
                        if (result.success) {
                            generatedGcode = outputFile
                            generatedGcodeGeneration = generationSnapshot
                            viewerMode = ViewerMode.TOOLPATH
                            modelWorkspaceSection = ModelWorkspaceSection.SLICE
                            toolpathMinimumLayer = 0
                            toolpathMaximumLayer = (result.layers.toInt() - 1).coerceAtLeast(0)
                            toolpathProgress = 1f
                            showExtrusionMoves = true
                            showTravelMoves = false
                        } else {
                            outputFile.delete()
                            generatedGcode = null
                            generatedGcodeGeneration = null
                            errorMessage = result.message
                        }
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        outputFile.delete()
                        if (sliceGeneration != generationSnapshot) return@onFailure
                        generatedGcode = null
                        generatedGcodeGeneration = null
                        errorMessage = error.message ?: "Ошибка слайсера"
                    }
                } finally {
                    configFile.delete()
                    plateFile.delete()
                    if (generatedGcode != outputFile) outputFile.delete()
                    isWorking = false
                }
            }
        }
    }

    LaunchedEffect(destination, modelWorkspaceSection) {
        if (destination != AppDestination.MODEL || modelWorkspaceSection != ModelWorkspaceSection.POSITION) {
            selectedPositionTool = null
        }
    }
    BackHandler(
        enabled = destination == AppDestination.MODEL &&
            modelWorkspaceSection == ModelWorkspaceSection.POSITION &&
            selectedPositionTool != null,
    ) {
        selectedPositionTool = null
    }

    CompositionLocalProvider(LocalUiLanguage provides uiLanguage) {
        MaterialTheme(colorScheme = appColorScheme) {
            Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (
                        destination == AppDestination.MODEL &&
                        hasPlateModels &&
                        modelWorkspaceSection != ModelWorkspaceSection.VIEW &&
                        modelWorkspaceSection != ModelWorkspaceSection.POSITION &&
                        modelWorkspaceSection != ModelWorkspaceSection.SLICE
                    ) {
                        ModelWorkspaceNavigation(
                            selected = modelWorkspaceSection,
                            onSelect = { modelWorkspaceSection = it },
                        )
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        NavigationBarItem(
                            selected = destination == AppDestination.MODEL,
                            onClick = {
                                if (destination == AppDestination.MODEL && hasPlateModels) {
                                    modelWorkspaceSection = ModelWorkspaceSection.FILE
                                } else if (hasPlateModels && modelWorkspaceSection == ModelWorkspaceSection.FILE) {
                                    modelWorkspaceSection = ModelWorkspaceSection.VIEW
                                }
                                destination = AppDestination.MODEL
                            },
                            icon = { androidx.compose.material3.Icon(painterResource(R.drawable.ic_nav_model), null) },
                            label = { Text("Модель") },
                        )
                        NavigationBarItem(
                            selected = destination == AppDestination.PRINTER,
                            onClick = { destination = AppDestination.PRINTER },
                            icon = { androidx.compose.material3.Icon(painterResource(R.drawable.ic_nav_printer), null) },
                            label = { Text("Принтер") },
                        )
                        NavigationBarItem(
                            selected = destination == AppDestination.FILAMENT,
                            onClick = { destination = AppDestination.FILAMENT },
                            icon = { androidx.compose.material3.Icon(painterResource(R.drawable.ic_nav_filament), null) },
                            label = { Text("Филамент") },
                        )
                        NavigationBarItem(
                            selected = destination == AppDestination.PRINT,
                            onClick = { destination = AppDestination.PRINT },
                            icon = { androidx.compose.material3.Icon(painterResource(R.drawable.ic_nav_print), null) },
                            label = { Text("Печать") },
                        )
                        NavigationBarItem(
                            selected = destination == AppDestination.APP,
                            onClick = { destination = AppDestination.APP },
                            icon = { androidx.compose.material3.Icon(painterResource(R.drawable.ic_nav_app), null) },
                            label = { Text("Меню") },
                        )
                    }
                }
            },
        ) { contentPadding ->
            key(destination) {
            val showPersistentModelCanvas =
                destination == AppDestination.MODEL &&
                    hasPlateModels &&
                    (
                        modelWorkspaceSection == ModelWorkspaceSection.VIEW ||
                            modelWorkspaceSection == ModelWorkspaceSection.POSITION ||
                            modelWorkspaceSection == ModelWorkspaceSection.SLICE
                    )

            if (showPersistentModelCanvas) {
                Box(
                    modifier = Modifier
                        .padding(
                            top = contentPadding.calculateTopPadding(),
                            bottom = contentPadding.calculateBottomPadding(),
                        )
                        .fillMaxSize(),
                ) {
                    if (modelWorkspaceSection == ModelWorkspaceSection.SLICE) {
                        OrcaSliceWorkspace(
                            modelFile = selectedFile,
                            gcodeFile = currentGeneratedGcode,
                            report = report,
                            transform = modelTransform,
                            bedWidth = bedWidth,
                            bedDepth = bedDepth,
                            darkTheme = useDarkTheme,
                            isWorking = isWorking,
                            minimumLayer = toolpathMinimumLayer,
                            maximumLayer = toolpathMaximumLayer,
                            colorMode = toolpathColorMode,
                            showExtrusion = showExtrusionMoves,
                            showTravel = showTravelMoves,
                            progress = toolpathProgress,
                            layerHeightMm = layerHeight.toDoubleOrNull() ?: 0.2,
                            cameraResetRequest = cameraResetRequest,
                            initialCameraState = viewerCameraState,
                            modelObjects = viewerModelObjects,
                            selectedObjectId = plateWorkspace.selectedObjectId?.value,
                            onObjectSelected = ::applyViewerSelection,
                            onSceneState = {
                                viewerSceneState = it
                                viewerMessage = null
                            },
                            onCameraStateChange = { state ->
                                viewerCameraState = state
                                selectedCameraViewPreset = state.preset
                                    .takeIf { state.mode == ViewerCameraMode.PRESET }
                            },
                            onViewerError = { viewerMessage = it },
                            onSlice = sliceCurrentModel,
                            onMinimumLayerChange = { toolpathMinimumLayer = it.coerceAtMost(toolpathMaximumLayer) },
                            onMaximumLayerChange = { toolpathMaximumLayer = it.coerceAtLeast(toolpathMinimumLayer) },
                            onColorModeChange = { toolpathColorMode = it },
                            onShowExtrusionChange = { showExtrusionMoves = it },
                            onShowTravelChange = { showTravelMoves = it },
                            onProgressChange = { toolpathProgress = it },
                            onResetCamera = { cameraResetRequest += 1 },
                            onSaveGcode = saveCurrentGcode,
                            onSendToPrinter = {
                                printerConnectionTestResult = null
                                showSendToPrinter = true
                            },
                            isSendingToPrinter = isSendingToPrinter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ModelViewer(
                            modelFile = selectedFile,
                            gcodeFile = null,
                            transform = modelTransform,
                            bedWidth = bedWidth,
                            bedDepth = bedDepth,
                            mode = ViewerMode.MODEL,
                            darkTheme = useDarkTheme,
                            cameraResetRequest = cameraResetRequest,
                            cameraViewRequest = cameraViewRequest,
                            cameraFramingRequest = cameraFramingRequest,
                            initialCameraState = viewerCameraState,
                            modelObjects = viewerModelObjects,
                            selectedObjectId = plateWorkspace.selectedObjectId?.value,
                            onObjectSelected = ::applyViewerSelection,
                            onSceneState = {
                                viewerSceneState = it
                                viewerMessage = null
                            },
                            onCameraStateChange = { state ->
                                viewerCameraState = state
                                selectedCameraViewPreset = state.preset
                                    .takeIf { state.mode == ViewerCameraMode.PRESET }
                            },
                            onError = { viewerMessage = it },
                            viewerHeight = null,
                            showStatus = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (modelWorkspaceSection == ModelWorkspaceSection.VIEW) {
                        CameraViewPresetOverlay(
                            selectedPreset = selectedCameraViewPreset,
                            onSelect = { preset ->
                                selectedCameraViewPreset = preset
                                cameraViewRequestId += 1
                                cameraViewRequest = CameraViewRequest(
                                    requestId = cameraViewRequestId,
                                    preset = preset,
                                )
                            },
                            onFitModel = {
                                cameraFramingRequestId += 1
                                cameraFramingRequest = CameraFramingRequest(
                                    requestId = cameraFramingRequestId,
                                    target = if (plateWorkspace.selectedObjectId != null) {
                                        CameraFramingTarget.SELECTED_MODEL
                                    } else {
                                        CameraFramingTarget.MODELS
                                    },
                                )
                            },
                            onShowBed = {
                                cameraFramingRequestId += 1
                                cameraFramingRequest = CameraFramingRequest(
                                    requestId = cameraFramingRequestId,
                                    target = CameraFramingTarget.PRINT_BED,
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (modelWorkspaceSection == ModelWorkspaceSection.POSITION) {
                        PositionWorkspaceOverlay(
                            selectedTool = selectedPositionTool,
                            selectedModel = selectedPlateObject,
                            selectedModelInsideBed = plateWorkspace.selectedObjectId?.let { selectedId ->
                                plateWorkspace.validate(
                                    RectangularBuildVolume(bedWidth, bedDepth, printableHeight),
                                ).objectResult(selectedId)?.insideBuildVolume
                            },
                            hasModels = plateWorkspace.objects.isNotEmpty(),
                            bedWidth = bedWidth,
                            bedDepth = bedDepth,
                            printableHeight = printableHeight,
                            linkScaleAxes = linkScaleAxes,
                            isWorking = isWorking,
                            onLinkScaleAxesChange = { linkScaleAxes = it },
                            onToolSelected = { tool ->
                                when (tool) {
                                    PositionWorkspaceTool.ARRANGE -> {
                                        val arranged = plateWorkspace.autoArrange(
                                            RectangularBuildVolume(bedWidth, bedDepth, printableHeight),
                                        )
                                        commitPlateWorkspace(
                                            arranged.workspace,
                                            if (arranged.allPlaced) {
                                                "Модели автоматически расставлены"
                                            } else {
                                                "Не поместилось моделей: ${arranged.unplacedObjectIds.size}"
                                            },
                                        )
                                    }

                                    PositionWorkspaceTool.AUTO_ORIENT -> orientSelectedModel(
                                        useBasicAutoOrientation = true,
                                        centerAfterOrientation = true,
                                        message = "Модель автоматически ориентирована",
                                    )

                                    PositionWorkspaceTool.LAY_FLAT -> orientSelectedModel(
                                        useBasicAutoOrientation = false,
                                        centerAfterOrientation = false,
                                        message = "Модель положена крупнейшей гранью",
                                    )

                                    else -> {
                                        selectedPositionTool = if (selectedPositionTool == tool) null else tool
                                    }
                                }
                            },
                            onTransformChange = ::updateSelectedTransform,
                            onCenter = {
                                plateWorkspace.selectedObjectId?.let { selectedId ->
                                    commitPlateWorkspace(
                                        plateWorkspace.center(
                                            selectedId,
                                            RectangularBuildVolume(bedWidth, bedDepth, printableHeight),
                                        ),
                                        "Модель размещена по центру",
                                    )
                                }
                            },
                            onPlaceOnBed = {
                                plateWorkspace.selectedObjectId?.let { selectedId ->
                                    commitPlateWorkspaceOnExactBed(
                                        updated = plateWorkspace,
                                        objectId = selectedId,
                                        message = "Модель опущена на стол",
                                    )
                                }
                            },
                            onRotate90 = {
                                plateWorkspace.selectedObjectId?.let { selectedId ->
                                    commitPlateWorkspaceOnExactBed(
                                        updated = plateWorkspace.rotate(selectedId, PlateAxis.Z, 90.0),
                                        objectId = selectedId,
                                        message = "Модель повёрнута на 90°",
                                    )
                                }
                            },
                            onResetScale = {
                                updateSelectedTransform {
                                    it.copy(scale = 1.0, scaleX = 1.0, scaleY = 1.0, scaleZ = 1.0)
                                }
                            },
                            workspaceNavigation = {
                                ModelWorkspaceNavigation(
                                    selected = modelWorkspaceSection,
                                    onSelect = { modelWorkspaceSection = it },
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val workspaceMessage = if (modelWorkspaceSection == ModelWorkspaceSection.SLICE) {
                        errorMessage ?: viewerMessage
                    } else {
                        viewerMessage
                    }
                    workspaceMessage?.let { message ->
                        androidx.compose.material3.Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(
                                    horizontal = 72.dp,
                                    vertical = if (modelWorkspaceSection == ModelWorkspaceSection.SLICE) 76.dp else 12.dp,
                                ),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(14.dp),
                            shadowElevation = 6.dp,
                            tonalElevation = 2.dp,
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 12.sp,
                            )
                        }
                    }
                    if (modelWorkspaceSection != ModelWorkspaceSection.POSITION) {
                        ModelWorkspaceNavigation(
                            selected = modelWorkspaceSection,
                            onSelect = { modelWorkspaceSection = it },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            } else {
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (destination) {
                    AppDestination.MODEL -> {
                if (modelWorkspaceSection == ModelWorkspaceSection.FILE) {
                    SectionCard(title = "Модели на столе") {
                        if (plateWorkspace.objects.isEmpty()) {
                            Text("Файл не выбран", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Поддерживаются STL, OBJ и 3MF. Можно выбрать несколько файлов сразу.",
                                color = Muted,
                                fontSize = 13.sp,
                            )
                        } else {
                            Text("Объектов: ${plateWorkspace.objects.size}", color = Muted, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            plateWorkspace.objects.forEach { model ->
                                val isSelected = model.id == plateWorkspace.selectedObjectId
                                androidx.compose.material3.Surface(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            plateWorkspace = plateWorkspace.select(model.id)
                                            viewerSceneState = null
                                            viewerMode = ViewerMode.MODEL
                                        },
                                ) {
                                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Text(
                                            model.sourceName,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            String.format(
                                                Locale.US,
                                                "%s · %.1f × %.1f × %.1f мм",
                                                model.source.sourceFormat,
                                                model.sourceBounds.width,
                                                model.sourceBounds.depth,
                                                model.sourceBounds.height,
                                            ),
                                            color = Muted,
                                            fontSize = 12.sp,
                                        )
                                        Text(
                                            buildString {
                                                model.source.triangleCount?.let { append("Треугольников: $it") }
                                                model.source.originalSizeBytes?.let { size ->
                                                    if (isNotEmpty()) append(" · ")
                                                    append(formatFileSize(size))
                                                }
                                            }.ifBlank { formatFileSize(model.source.file.length()) },
                                            color = Muted,
                                            fontSize = 11.sp,
                                        )
                                        if (isSelected) {
                                            Text("Выбран", color = Accent, fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(
                                if (currentGeneratedGcode != null) "Состояние: нарезка готова" else "Состояние: модель загружена",
                                color = Accent,
                                fontSize = 13.sp,
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { filePicker.launch(ModelDocumentPolicy.pickerMimeTypes) },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (plateWorkspace.objects.isEmpty()) "Загрузить модель" else "Добавить модель")
                        }
                        TextButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Не видите STL, OBJ или 3MF? Показать все файлы")
                        }
                        if (plateWorkspace.selectedObject != null) {
                            TextButton(
                                onClick = removeCurrentModel,
                                enabled = !isWorking,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Удалить выбранную модель")
                            }
                        }
                    }
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                        )
                    }
                } else if (modelWorkspaceSection == ModelWorkspaceSection.VIEW) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ModelViewer(
                            modelFile = selectedFile,
                            gcodeFile = null,
                            transform = modelTransform,
                            bedWidth = bedWidth,
                            bedDepth = bedDepth,
                            mode = ViewerMode.MODEL,
                            darkTheme = useDarkTheme,
                            cameraResetRequest = cameraResetRequest,
                            modelObjects = viewerModelObjects,
                            selectedObjectId = plateWorkspace.selectedObjectId?.value,
                            onObjectSelected = ::applyViewerSelection,
                            onSceneState = { viewerSceneState = it },
                            onError = { viewerMessage = it },
                            viewerHeight = 650.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                run {
                    val sourceModel = selectedFile
                    SectionCard(title = "Расположение модели") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(330.dp),
                        ) {
                            ModelViewer(
                                modelFile = sourceModel,
                                gcodeFile = null,
                                transform = modelTransform,
                                bedWidth = bedWidth,
                                bedDepth = bedDepth,
                                mode = ViewerMode.MODEL,
                                darkTheme = useDarkTheme,
                                cameraResetRequest = cameraResetRequest,
                                modelObjects = viewerModelObjects,
                                selectedObjectId = plateWorkspace.selectedObjectId?.value,
                                onObjectSelected = ::applyViewerSelection,
                                onSceneState = { viewerSceneState = it },
                                onError = { viewerMessage = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                            ModelContextRail(
                                section = modelWorkspaceSection,
                                hasModel = hasPlateModels,
                                hasGcode = currentGeneratedGcode != null,
                                isWorking = isWorking,
                                onImportModel = { filePicker.launch(ModelDocumentPolicy.pickerMimeTypes) },
                                onRemoveModel = removeCurrentModel,
                                onResetCamera = { cameraResetRequest += 1 },
                                onShowModel = { viewerMode = ViewerMode.MODEL },
                                onShowToolpath = { viewerMode = ViewerMode.TOOLPATH },
                                onCenterModel = {
                                    plateWorkspace.selectedObjectId?.let { selectedId ->
                                        commitPlateWorkspace(
                                            plateWorkspace.center(
                                                selectedId,
                                                RectangularBuildVolume(bedWidth, bedDepth, printableHeight),
                                            ),
                                            "Модель размещена по центру",
                                        )
                                    }
                                },
                                onRotateModel = {
                                    plateWorkspace.selectedObjectId?.let { selectedId ->
                                        commitPlateWorkspace(
                                            plateWorkspace.rotate(selectedId, PlateAxis.Z, 90.0).moveToBed(selectedId),
                                            "Модель повёрнута на 90°",
                                        )
                                    }
                                },
                                onResetScale = {
                                    updateSelectedTransform {
                                        it.copy(scale = 1.0, scaleX = 1.0, scaleY = 1.0, scaleZ = 1.0)
                                    }
                                },
                                onSlice = sliceCurrentModel,
                                onSendToPrinter = {
                                    printerConnectionTestResult = null
                                    showSendToPrinter = true
                                },
                                onSaveGcode = saveCurrentGcode,
                                modifier = Modifier
                                    .width(76.dp)
                                    .fillMaxHeight(),
                            )
                        }

                        viewerSceneState?.let { sceneState ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (sceneState.insideBed) {
                                    "Модель находится в пределах стола ${bedWidth.toInt()} × ${bedDepth.toInt()} мм"
                                } else {
                                    "Модель выходит за пределы печатного стола"
                                },
                                color = if (sceneState.insideBed) Accent else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Границы X %.1f–%.1f · Y %.1f–%.1f · В %.1f мм",
                                    sceneState.minimumX,
                                    sceneState.maximumX,
                                    sceneState.minimumY,
                                    sceneState.maximumY,
                                    sceneState.height,
                                ),
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        }

                        viewerMessage?.let { message ->
                            Spacer(Modifier.height(8.dp))
                            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(14.dp))
                        val selectedModel = selectedPlateObject
                        if (selectedModel == null) {
                            Text("Выберите модель на столе", color = Muted)
                        } else {
                            val selectedTransform = selectedModel.transform
                            val selectedId = selectedModel.id
                            val buildVolume = RectangularBuildVolume(bedWidth, bedDepth, printableHeight)
                            val selectedValidation = plateWorkspace.validate(buildVolume).objectResult(selectedId)
                            val selectedCollisions = plateWorkspace.collisions().count { collision ->
                                collision.firstObjectId == selectedId || collision.secondObjectId == selectedId
                            }

                            Text(selectedModel.sourceName, fontWeight = FontWeight.SemiBold)
                            if (selectedCollisions > 0) {
                                Text(
                                    "Пересечение габаритов с другими моделями: $selectedCollisions",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                )
                            }
                            if (selectedValidation?.insideBuildVolume == false) {
                                Text(
                                    "Объект выходит за пределы области печати",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                )
                            }
                            modelActionMessage?.let { message ->
                                Text(message, color = Accent, fontSize = 12.sp)
                            }

                            Spacer(Modifier.height(10.dp))
                            Text("Положение XYZ", fontWeight = FontWeight.SemiBold)
                            TransformSlider(
                                label = "Позиция X",
                                value = selectedTransform.positionXmm,
                                range = 0f..bedWidth.toFloat(),
                                suffix = "мм",
                                onValueChange = { value ->
                                    updateSelectedTransform { it.copy(positionXmm = value) }
                                },
                            )
                            TransformSlider(
                                label = "Позиция Y",
                                value = selectedTransform.positionYmm,
                                range = 0f..bedDepth.toFloat(),
                                suffix = "мм",
                                onValueChange = { value ->
                                    updateSelectedTransform { it.copy(positionYmm = value) }
                                },
                            )
                            TransformSlider(
                                label = "Позиция Z",
                                value = selectedTransform.positionZmm,
                                range = 0f..printableHeight.toFloat(),
                                suffix = "мм",
                                onValueChange = { value ->
                                    updateSelectedTransform { it.copy(positionZmm = value) }
                                },
                            )

                            Spacer(Modifier.height(6.dp))
                            Text("Поворот XYZ", fontWeight = FontWeight.SemiBold)
                            TransformSlider(
                                label = "Поворот X",
                                value = selectedTransform.rotationXDegrees,
                                range = 0f..360f,
                                suffix = "°",
                                onValueChange = { value ->
                                    updateSelectedTransform { it.copy(rotationXDegrees = value) }
                                },
                            )
                            TransformSlider(
                                label = "Поворот Y",
                                value = selectedTransform.rotationYDegrees,
                                range = 0f..360f,
                                suffix = "°",
                                onValueChange = { value ->
                                    updateSelectedTransform { it.copy(rotationYDegrees = value) }
                                },
                            )
                            TransformSlider(
                                label = "Поворот Z",
                                value = selectedTransform.rotationZDegrees,
                                range = 0f..360f,
                                suffix = "°",
                                onValueChange = { value ->
                                    updateSelectedTransform { it.copy(rotationDegrees = value) }
                                },
                            )

                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Сохранять пропорции", fontWeight = FontWeight.SemiBold)
                                Switch(checked = linkScaleAxes, onCheckedChange = { linkScaleAxes = it })
                            }
                            if (linkScaleAxes) {
                                TransformSlider(
                                    label = "Масштаб",
                                    value = selectedTransform.effectiveScaleX,
                                    range = 0.1f..3f,
                                    suffix = "×",
                                    decimals = 2,
                                    onValueChange = { value ->
                                        updateSelectedTransform {
                                            it.copy(
                                                scale = value,
                                                scaleX = 1.0,
                                                scaleY = 1.0,
                                                scaleZ = 1.0,
                                            )
                                        }
                                    },
                                )
                            } else {
                                listOf(
                                    Triple("Масштаб X", selectedTransform.effectiveScaleX, PlateAxis.X),
                                    Triple("Масштаб Y", selectedTransform.effectiveScaleY, PlateAxis.Y),
                                    Triple("Масштаб Z", selectedTransform.effectiveScaleZ, PlateAxis.Z),
                                ).forEach { (label, value, axis) ->
                                    TransformSlider(
                                        label = label,
                                        value = value,
                                        range = 0.1f..3f,
                                        suffix = "×",
                                        decimals = 2,
                                        onValueChange = { nextValue ->
                                            commitPlateWorkspace(
                                                plateWorkspace.setAxisScale(selectedId, axis, nextValue),
                                            )
                                        },
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        commitPlateWorkspace(
                                            plateWorkspace.center(selectedId, buildVolume),
                                            "Модель размещена по центру",
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("По центру") }
                                OutlinedButton(
                                    onClick = {
                                        commitPlateWorkspaceOnExactBed(
                                            updated = plateWorkspace,
                                            objectId = selectedId,
                                            message = "Модель опущена на стол",
                                        )
                                    },
                                    enabled = !isWorking,
                                    modifier = Modifier.weight(1f),
                                ) { Text("На стол") }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        commitPlateWorkspaceOnExactBed(
                                            updated = plateWorkspace.rotate(selectedId, PlateAxis.Z, 90.0),
                                            objectId = selectedId,
                                            message = "Модель повёрнута на 90°",
                                        )
                                    },
                                    enabled = !isWorking,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Повернуть Z 90°") }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isWorking = true
                                            val workspaceSnapshot = plateWorkspace
                                            runCatching {
                                                withContext(Dispatchers.Default) {
                                                    val orientation = StlPlateComposer
                                                        .suggestLayFlat(selectedModel.source.file)
                                                        ?: error("Не удалось найти подходящую грань")
                                                    workspaceSnapshot.updateTransform(selectedId) {
                                                        it.copy(
                                                            rotationXDegrees = orientation.rotationXDegrees,
                                                            rotationYDegrees = orientation.rotationYDegrees,
                                                            rotationDegrees = orientation.rotationZDegrees,
                                                            positionZmm = orientation.positionZmm,
                                                        )
                                                    }.moveObjectToExactBed(selectedId)
                                                }
                                            }.onSuccess { exactWorkspace ->
                                                commitPlateWorkspace(
                                                    exactWorkspace,
                                                    "Модель положена крупнейшей гранью",
                                                )
                                            }.onFailure { error ->
                                                errorMessage = error.message ?: "Не удалось положить модель гранью"
                                            }
                                            isWorking = false
                                        }
                                    },
                                    enabled = !isWorking,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Положить гранью") }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val arranged = plateWorkspace
                                            .duplicate(selectedId)
                                            .autoArrange(buildVolume)
                                        commitPlateWorkspace(
                                            arranged.workspace,
                                            if (arranged.allPlaced) "Создана копия модели" else "Копия создана; не всё поместилось на стол",
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Дублировать") }
                                OutlinedButton(
                                    onClick = {
                                        renameObjectId = selectedId
                                        renameObjectValue = selectedModel.sourceName
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Переименовать") }
                            }
                            Button(
                                onClick = {
                                    val arranged = plateWorkspace.autoArrange(buildVolume)
                                    commitPlateWorkspace(
                                        arranged.workspace,
                                        if (arranged.allPlaced) {
                                            "Модели автоматически расставлены"
                                        } else {
                                            "Не поместилось моделей: ${arranged.unplacedObjectIds.size}"
                                        },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Автоматически расставить") }
                        }
                    }
                }

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    )
                }
                }

                    }

                    AppDestination.PRINTER,
                    AppDestination.FILAMENT,
                    AppDestination.PRINT -> ProfilesScreen(
                        selected = when (destination) {
                            AppDestination.PRINTER -> ProfileSection.PRINTER
                            AppDestination.FILAMENT -> ProfileSection.FILAMENT
                            AppDestination.PRINT -> ProfileSection.PROCESS
                            else -> error("Неподдерживаемый раздел профиля")
                        },
                        printSettings = printSettings,
                        printDetailLevel = printDetailLevel,
                        printSettingsCategory = printSettingsCategory,
                        onPrintDetailLevelChange = { printDetailLevel = it },
                        onPrintSettingsCategoryChange = { printSettingsCategory = it },
                        onPrintSettingsChange = {
                            val previous = printSettings.toOrcaProcessSettingsPayload().asMap()
                            val next = it.toOrcaProcessSettingsPayload().asMap()
                            dirtyProcessSettingKeys = dirtyProcessSettingKeys + next.keys.filter { key ->
                                previous[key] != next[key]
                            }
                            printSettings = it
                            invalidatePlateSlice()
                        },
                        nozzleDiameter = nozzleDiameter,
                        onNozzleDiameter = {
                            nozzleDiameter = it
                            invalidatePlateSlice()
                        },
                        filamentDiameter = filamentDiameter,
                        onFilamentDiameter = {
                            filamentDiameter = it
                            invalidatePlateSlice()
                        },
                        nozzleTemperature = nozzleTemperature,
                        onNozzleTemperature = {
                            nozzleTemperature = it
                            invalidatePlateSlice()
                        },
                        bedTemperature = bedTemperature,
                        onBedTemperature = {
                            bedTemperature = it
                            invalidatePlateSlice()
                        },
                        bedWidth = bedWidth,
                        bedDepth = bedDepth,
                        printableHeight = printableHeight,
                        printerFirmware = printerFirmware,
                        systemCatalog = compatibleSystemPrinterCatalog,
                        systemCatalogLoading = isSystemCatalogLoading,
                        cloudState = cloudProfileState,
                        isOrcaSignedIn = authState is OrcaAuthState.SignedIn,
                        isReviewerDemo = isReviewerDemo,
                        printerProfileName = printerProfileName,
                        filamentProfileName = filamentProfileName,
                        processProfileName = processProfileName,
                        activePrinterProfileId = activePrinterProfile?.id,
                        activeFilamentProfileId = activeFilamentProfile?.id,
                        activeProcessProfileId = activeProcessProfile?.id,
                        activePrinterConnection = activePrinterConnection,
                        activePrinterConnectionSource = activePrinterConnectionSource,
                        savedManualPrinterConnection = savedManualPrinterConnection,
                        printerConnectionStatus = printerConnectionStatus,
                        printerConnectionTestResult = printerConnectionTestResult,
                        isTestingPrinter = isTestingPrinter,
                        onTestPrinter = {
                            val connection = activePrinterConnection ?: return@ProfilesScreen
                            val requestId = printerProbeRequestId + 1L
                            printerProbeRequestId = requestId
                            scope.launch {
                                isTestingPrinter = true
                                printerConnectionStatus = null
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        PrinterConnectionService.test(connection)
                                    }
                                }.onSuccess { result ->
                                    if (printerProbeRequestId == requestId) {
                                        printerConnectionTestResult = result
                                        printerConnectionStatus = printerConnectionResultMessage(result)
                                    }
                                }.onFailure { error ->
                                    if (printerProbeRequestId == requestId) {
                                        printerConnectionTestResult = null
                                        printerConnectionStatus = error.message ?: "Не удалось подключиться к принтеру"
                                    }
                                }
                                if (printerProbeRequestId == requestId) {
                                    isTestingPrinter = false
                                }
                            }
                        },
                        onSaveManualPrinter = { draft ->
                            runCatching {
                                draft.validatedConnection().also { connection ->
                                    manualPrinterConnectionStore.write(connection, isActive = true)
                                }
                            }
                                .onSuccess { connection ->
                                    savedManualPrinterConnection = SavedManualPrinterConnection(
                                        connection = connection,
                                        isActive = true,
                                    )
                                    printerConnectionStatus = "Ручное подключение сохранено"
                                }
                                .onFailure { error ->
                                    printerConnectionStatus = error.message ?: "Не удалось сохранить подключение"
                                }
                        },
                        onActivateManualPrinter = {
                            savedManualPrinterConnection?.let { saved ->
                                runCatching {
                                    manualPrinterConnectionStore.write(saved.connection, isActive = true)
                                }.onSuccess {
                                    savedManualPrinterConnection = saved.copy(isActive = true)
                                    printerConnectionStatus = "Ручное подключение выбрано"
                                }.onFailure { error ->
                                    printerConnectionStatus = error.message ?: "Не удалось сохранить подключение"
                                }
                            }
                        },
                        onDeleteManualPrinter = {
                            runCatching { manualPrinterConnectionStore.clear() }
                                .onSuccess {
                                    savedManualPrinterConnection = null
                                    printerConnectionStatus = "Ручное подключение удалено"
                                }
                                .onFailure { error ->
                                    printerConnectionStatus = error.message ?: "Не удалось удалить подключение"
                                }
                        },
                        onSyncCloud = authViewModel::syncProfiles,
                        onOpenApp = { destination = AppDestination.APP },
                        onApplyCloudProfile = { profile ->
                            runCatching {
                                val presetCatalog = systemPresetCatalog
                                    ?: error("Системные профили Orca ещё загружаются")
                                resolveProfileSettingsForUi(
                                    catalog = presetCatalog,
                                    profile = profile,
                                    availableProfiles = cloudProfileState.profiles,
                                    printerContext = activePrinterProfile.takeUnless {
                                        profile.type == OrcaProfileType.PRINTER
                                    },
                                )
                            }.onSuccess { resolved ->
                                when (profile.type) {
                                    OrcaProfileType.PRINTER -> {
                                        printerProfileName = profile.name
                                        selectedPrinterProfileRef = profile.toPersistedCloudRef(
                                            authState = authState,
                                            cachedOwnerAccountId = cloudProfileState.ownerAccountId,
                                        )
                                        profile.printerConnection()?.let {
                                            savedManualPrinterConnection?.let { saved ->
                                                manualPrinterConnectionStore.write(saved.connection, isActive = false)
                                                savedManualPrinterConnection = saved.copy(isActive = false)
                                            }
                                        }
                                        firstProfileSetting(resolved, "nozzle_diameter")
                                            ?.let { nozzleDiameter = it }
                                        printerDimensions(resolved)?.let { (width, depth) ->
                                            bedWidth = width
                                            bedDepth = depth
                                            plateWorkspace.selectedObjectId?.let { selectedId ->
                                                plateWorkspace = plateWorkspace.updateTransform(selectedId) {
                                                    it.copy(positionXmm = width / 2.0, positionYmm = depth / 2.0)
                                                }
                                            }
                                        }
                                        firstProfileSetting(
                                            resolved,
                                            "printable_height",
                                            "max_print_height",
                                        )
                                            ?.toDoubleOrNull()
                                            ?.takeIf { it > 0.0 }
                                            ?.let { printableHeight = it }
                                        firstProfileSetting(resolved, "gcode_flavor")
                                            ?.let { printerFirmware = it }
                                        printerConnectionStatus = profile.printerConnection()?.let {
                                            "Подключение загружено из OrcaCloud"
                                        }
                                    }
                                    OrcaProfileType.FILAMENT -> {
                                        filamentProfileName = profile.name
                                        selectedFilamentProfileRef = profile.toPersistedCloudRef(
                                            authState = authState,
                                            cachedOwnerAccountId = cloudProfileState.ownerAccountId,
                                        )
                                        firstProfileSetting(resolved, "filament_diameter")
                                            ?.let { filamentDiameter = it }
                                        firstProfileSetting(
                                            resolved,
                                            "nozzle_temperature",
                                            "nozzle_temperature_initial_layer",
                                        )?.let { nozzleTemperature = it }
                                        firstProfileSetting(
                                            resolved,
                                            "hot_plate_temp",
                                            "textured_plate_temp",
                                            "cool_plate_temp",
                                        )?.let { bedTemperature = it }
                                    }
                                    OrcaProfileType.PROCESS -> {
                                        processProfileName = profile.name
                                        selectedProcessProfileRef = profile.toPersistedCloudRef(
                                            authState = authState,
                                            cachedOwnerAccountId = cloudProfileState.ownerAccountId,
                                        )
                                        printSettings = printSettings.applyOrcaSettings(resolved)
                                        dirtyProcessSettingKeys = if (
                                            resolved["support_type"].orEmpty().contains("(manual)")
                                        ) {
                                            setOf("support_type")
                                        } else {
                                            emptySet()
                                        }
                                        generatedGcode = null
                                        report = null
                                        viewerMode = ViewerMode.MODEL
                                    }
                                    OrcaProfileType.OTHER -> Unit
                                }
                                if (profile.type != OrcaProfileType.OTHER) invalidatePlateSlice()
                            }.onFailure { error ->
                                errorMessage = error.message ?: "Не удалось применить профиль Orca"
                            }
                        },
                        onApplySystemProfile = { profile ->
                            runCatching {
                                val presetCatalog = systemPresetCatalog
                                    ?: error("Системные профили Orca ещё загружаются")
                                val contextHint = "${profile.vendor} ${profile.family} ${profile.model}"
                                val bundledPrinter = presetCatalog.bundledProfile(
                                    type = OrcaProfileType.PRINTER,
                                    name = profile.name,
                                    contextHint = contextHint,
                                )
                                val printerSettings = resolveProfileSettingsForUi(
                                    catalog = presetCatalog,
                                    profile = bundledPrinter,
                                    availableProfiles = listOf(bundledPrinter),
                                )
                                val bundledProcess = profile.defaultPrintProfile
                                    .takeIf(String::isNotBlank)
                                    ?.let { processName ->
                                        runCatching {
                                            presetCatalog.bundledProfile(
                                                type = OrcaProfileType.PROCESS,
                                                name = processName,
                                                contextHint = "$contextHint ${profile.name}",
                                            )
                                        }.getOrNull()
                                    }
                                val processSettings = bundledProcess?.let { process ->
                                    resolveProfileSettingsForUi(
                                        catalog = presetCatalog,
                                        profile = process,
                                        availableProfiles = listOf(process),
                                    )
                                }
                                Triple(bundledPrinter, printerSettings, bundledProcess to processSettings)
                            }.onSuccess { (bundledPrinter, resolvedPrinter, process) ->
                                val contextHint = "${profile.vendor} ${profile.family} ${profile.model}"
                                printerProfileName = profile.name
                                selectedPrinterProfileRef = PersistedProfileRef(
                                    origin = PersistedProfileOrigin.SYSTEM,
                                    type = OrcaProfileType.PRINTER,
                                    id = bundledPrinter.id,
                                    name = bundledPrinter.name,
                                    contextHint = contextHint,
                                )
                                firstProfileSetting(resolvedPrinter, "nozzle_diameter")
                                    ?.let { nozzleDiameter = it }
                                    ?: run { nozzleDiameter = formatProfileNumber(profile.nozzleDiameter) }
                                val dimensions = printerDimensions(resolvedPrinter)
                                    ?: (profile.bedWidth to profile.bedDepth)
                                bedWidth = dimensions.first
                                bedDepth = dimensions.second
                                firstProfileSetting(
                                    resolvedPrinter,
                                    "printable_height",
                                    "max_print_height",
                                )
                                    ?.toDoubleOrNull()
                                    ?.takeIf { it > 0.0 }
                                    ?.let { printableHeight = it }
                                    ?: run {
                                        printableHeight = profile.printableHeight
                                            .takeIf { it > 0.0 }
                                            ?: printableHeight
                                    }
                                firstProfileSetting(resolvedPrinter, "gcode_flavor")
                                    ?.let { printerFirmware = it }
                                    ?: run { printerFirmware = profile.gcodeFlavor }

                                selectedProcessProfileRef = process.first?.let { bundledProcess ->
                                    PersistedProfileRef(
                                        origin = PersistedProfileOrigin.SYSTEM,
                                        type = OrcaProfileType.PROCESS,
                                        id = bundledProcess.id,
                                        name = bundledProcess.name,
                                        contextHint = "$contextHint ${profile.name}",
                                    )
                                }
                                if (process.first != null && process.second != null) {
                                    processProfileName = process.first!!.name
                                    printSettings = printSettings.applyOrcaSettings(process.second!!)
                                    dirtyProcessSettingKeys = if (
                                        process.second!!["support_type"].orEmpty().contains("(manual)")
                                    ) {
                                        setOf("support_type")
                                    } else {
                                        emptySet()
                                    }
                                } else {
                                    processProfileName = "Standard quality"
                                    printSettings = PrintSettingsState()
                                    dirtyProcessSettingKeys = emptySet()
                                }
                                plateWorkspace.selectedObjectId?.let { selectedId ->
                                    plateWorkspace = plateWorkspace.updateTransform(selectedId) {
                                        it.copy(positionXmm = bedWidth / 2.0, positionYmm = bedDepth / 2.0)
                                    }
                                }
                                invalidatePlateSlice()
                            }.onFailure { error ->
                                errorMessage = error.message ?: "Не удалось загрузить системный профиль Orca"
                            }
                        },
                    )

                    AppDestination.APP -> {
                        SectionCard(title = "Настройки приложения") {
                            Text("Язык интерфейса", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            LanguageSelector(
                                selected = uiLanguage,
                                onSelect = { language ->
                                    uiLanguage = language
                                    themePreferences.edit().putString(LanguageModeKey, language.name).apply()
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Тема", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            ThemeModeSelector(
                                selected = themeMode,
                                onSelect = { mode ->
                                    themeMode = mode
                                    themePreferences.edit().putString(ThemeModeKey, mode.name).apply()
                                },
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Локальный слайсер", fontWeight = FontWeight.SemiBold)
                            Text("Включён и всегда доступен без интернета", color = Muted, fontSize = 13.sp)
                        }
                        StatusCard()
                        OrcaCloudAccountCard(
                            state = authState,
                            onSignIn = authViewModel::signIn,
                            onReviewerDemoSignIn = authViewModel::enterReviewerDemo,
                            onCancel = authViewModel::cancelSignIn,
                            onRetry = authViewModel::retryRestore,
                            onSignOut = authViewModel::signOut,
                            onManageAccount = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(BuildConfig.ORCA_ACCOUNT_SETTINGS_URL),
                                        ),
                                    )
                                }.onFailure {
                                    errorMessage = "Не удалось открыть управление аккаунтом Orca Cloud"
                                }
                            },
                        )
                        SectionCard(title = "Синхронизация профилей") {
                            val printerCount = cloudProfileState.profiles.count { it.type == OrcaProfileType.PRINTER }
                            val filamentCount = cloudProfileState.profiles.count { it.type == OrcaProfileType.FILAMENT }
                            val processCount = cloudProfileState.profiles.count { it.type == OrcaProfileType.PROCESS }
                            if (cloudProfileState.profiles.isNotEmpty()) {
                                Text(
                                    if (isReviewerDemo) {
                                        "Локальный demo для проверки Google Play"
                                    } else {
                                        "Загружено из OrcaCloud"
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Принтеры: $printerCount · филаменты: $filamentCount · печать: $processCount",
                                    color = Muted,
                                    fontSize = 13.sp,
                                )
                                if (cloudProfileState.origin == OrcaProfileOrigin.REVIEW_DEMO) {
                                    Text(
                                        "Тестовые профили встроены в приложение; сеть не используется.",
                                        color = Muted,
                                        fontSize = 12.sp,
                                    )
                                } else if (cloudProfileState.isCached) {
                                    Text("Показаны данные из локального кэша", color = Muted, fontSize = 12.sp)
                                }
                            } else {
                                Text("После входа здесь появятся ваши профили OrcaCloud.", color = Muted)
                            }
                            cloudProfileState.error?.let { message ->
                                Spacer(Modifier.height(8.dp))
                                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            }
                            if (authState is OrcaAuthState.SignedIn) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = authViewModel::syncProfiles,
                                    enabled = !cloudProfileState.isLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        when {
                                            cloudProfileState.isLoading -> "Синхронизация…"
                                            isReviewerDemo -> "Сбросить demo-профили"
                                            else -> "Обновить профили"
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (isReviewerDemo) {
                                    "Demo не подключается к OrcaCloud, принтерам или пользовательским данным."
                                } else {
                                    "Только чтение: приложение загружает профили, но не изменяет данные OrcaCloud."
                                },
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)),
                                    )
                                }.onFailure {
                                    errorMessage = "Не удалось открыть политику конфиденциальности"
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("Политика конфиденциальности", color = Accent)
                        }
                        TextButton(
                            onClick = { showLicense = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("AGPL-3.0 · Лицензия и исходный код", color = Accent)
                        }
                    }
                }
            }
            }
            }
        }
        }

    renameObjectId?.let { objectId ->
        AlertDialog(
            onDismissRequest = { renameObjectId = null },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching { plateWorkspace.rename(objectId, renameObjectValue) }
                            .onSuccess { updated ->
                                commitPlateWorkspace(updated, "Модель переименована")
                                renameObjectId = null
                            }
                            .onFailure { error ->
                                errorMessage = error.message ?: "Не удалось переименовать модель"
                            }
                    },
                    enabled = renameObjectValue.isNotBlank(),
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { renameObjectId = null }) { Text("Отмена") }
            },
            title = { Text("Переименовать модель") },
            text = {
                OutlinedTextField(
                    value = renameObjectValue,
                    onValueChange = { renameObjectValue = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SOURCE_CODE_URL)),
                            )
                        }.onFailure {
                            errorMessage = "Не удалось открыть исходный код"
                        }
                    },
                ) { Text("Открыть исходный код") }
            },
            dismissButton = {
                TextButton(onClick = { showLicense = false }) { Text("Закрыть") }
            },
            title = { Text("Свободное программное обеспечение") },
            text = {
                Text(
                    "Feresa Slicer распространяется по лицензии GNU AGPL версии 3. " +
                        "Исходный код, сценарии сборки, уведомления и текст лицензии " +
                        "включены в проект. Программа предоставляется без гарантий.",
                )
            },
        )
    }

    LaunchedEffect(showSendToPrinter, activePrinterConnection) {
        val connection = activePrinterConnection
        val requestId = sendPrinterProbeRequestId + 1L
        sendPrinterProbeRequestId = requestId
        sendPrinterConnectionTestResult = null
        sendPrinterConnectionStatus = null
        if (!showSendToPrinter || connection == null) {
            isTestingSendPrinter = false
            return@LaunchedEffect
        }
        isTestingSendPrinter = true
        runCatching {
            withContext(Dispatchers.IO) { PrinterConnectionService.test(connection) }
        }.onSuccess { result ->
            if (sendPrinterProbeRequestId == requestId) {
                sendPrinterConnectionTestResult = result
                sendPrinterConnectionStatus = printerConnectionResultMessage(result)
            }
        }.onFailure { error ->
            if (sendPrinterProbeRequestId == requestId) {
                sendPrinterConnectionTestResult = null
                sendPrinterConnectionStatus = error.message ?: "Не удалось подключиться к принтеру"
            }
        }
        if (sendPrinterProbeRequestId == requestId) {
            isTestingSendPrinter = false
        }
    }

    if (showSendToPrinter) {
        val connection = activePrinterConnection
        val connectedStatus = (sendPrinterConnectionTestResult as? PrinterConnectionTestResult.Connected)?.status
        val uploadOnly: () -> Unit = upload@{
            val target = connection ?: return@upload
            val gcode = currentGeneratedGcode ?: return@upload
            val remoteName = selectedName
                ?.substringBeforeLast('.')
                ?.plus(".gcode")
                ?: "feresa-slicer.gcode"
            showSendToPrinter = false
            scope.launch {
                isSendingToPrinter = true
                runCatching {
                    withContext(Dispatchers.IO) {
                        NetworkPrinterClient.upload(target, gcode, remoteName)
                    }
                }.onSuccess { receipt ->
                    printerDialogResult = PrinterDialogResult(
                        title = "G-code загружен",
                        message = "${target.printerName}: файл ${receipt.remotePath} загружен. Печать не запускалась.",
                    )
                }.onFailure { error ->
                    printerDialogResult = PrinterDialogResult(
                        title = "Не удалось отправить",
                        message = error.message ?: "Ошибка соединения с принтером",
                    )
                }
                isSendingToPrinter = false
            }
        }
        val uploadAndStart: () -> Unit = upload@{
            val target = connection ?: return@upload
            val gcode = currentGeneratedGcode ?: return@upload
            val generationSnapshot = sliceGeneration
            val remoteName = selectedName
                ?.substringBeforeLast('.')
                ?.plus(".gcode")
                ?: "feresa-slicer.gcode"
            showSendToPrinter = false
            scope.launch {
                isSendingToPrinter = true
                val upload = runCatching {
                    withContext(Dispatchers.IO) {
                        NetworkPrinterClient.upload(target, gcode, remoteName)
                    }
                }
                upload.onSuccess { receipt ->
                    val artifactStillCurrent = sliceGeneration == generationSnapshot &&
                        currentSliceArtifact(
                            value = generatedGcode,
                            producedGeneration = generatedGcodeGeneration,
                            currentGeneration = sliceGeneration,
                        ) == gcode
                    if (!artifactStillCurrent) {
                        printerDialogResult = PrinterDialogResult(
                            title = "Файл загружен, печать не запущена",
                            message = "${target.printerName}: ${receipt.remotePath} сохранён на принтере. " +
                                "Модель или настройки изменились во время загрузки — выполните новую нарезку.",
                        )
                    } else {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                NetworkPrinterClient.start(target, receipt.remotePath)
                            }
                        }.onSuccess {
                            printerDialogResult = PrinterDialogResult(
                                title = "Печать запущена",
                                message = "${target.printerName}: файл ${receipt.remotePath} загружен, команда печати отправлена.",
                            )
                        }.onFailure { error ->
                            printerDialogResult = PrinterDialogResult(
                                title = "Файл загружен, печать не запущена",
                                message = "${target.printerName}: ${receipt.remotePath} сохранён на принтере. " +
                                    (error.message ?: "Принтер отклонил команду запуска"),
                            )
                        }
                    }
                }.onFailure { error ->
                    printerDialogResult = PrinterDialogResult(
                        title = "Не удалось отправить",
                        message = error.message ?: "Ошибка соединения с принтером",
                    )
                }
                isSendingToPrinter = false
            }
        }
        AlertDialog(
            onDismissRequest = { showSendToPrinter = false },
            confirmButton = {
                if (connection?.hostType?.canSendGcode == true) {
                    Column(horizontalAlignment = Alignment.End) {
                        Button(
                            onClick = uploadAndStart,
                            enabled = !isTestingSendPrinter && connectedStatus?.canStart == true && currentGeneratedGcode != null,
                        ) { Text("Загрузить и начать") }
                        TextButton(
                            onClick = uploadOnly,
                            enabled = !isTestingSendPrinter && connectedStatus != null && currentGeneratedGcode != null,
                        ) { Text("Только загрузить") }
                    }
                } else {
                    TextButton(
                        onClick = {
                            showSendToPrinter = false
                            destination = AppDestination.PRINTER
                        },
                    ) { Text("Открыть раздел «Принтер»") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendToPrinter = false }) { Text("Отмена") }
            },
            title = {
                Text(if (connection?.hostType?.canSendGcode == true) "Отправить на печать?" else "Принтер не подключён")
            },
            text = {
                if (connection?.hostType?.canSendGcode == true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${connection.printerName}\n${connection.hostType.label} · ${connection.host}")
                        if (isTestingSendPrinter) {
                            Text("Проверяем состояние принтера…", color = Muted)
                        } else {
                            sendPrinterConnectionTestResult?.let { PrinterConnectionStatusBlock(it) }
                            if (sendPrinterConnectionTestResult == null) {
                                sendPrinterConnectionStatus?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        Text(
                            "Можно только загрузить G-code либо загрузить и сразу начать печать. " +
                                "Запуск доступен только после свежей проверки готовности принтера.",
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    Text(
                        "G-code готов, но в активном профиле нет поддерживаемого адреса сетевого принтера. " +
                            "Добавьте Moonraker или OctoPrint в разделе «Принтер».",
                    )
                }
            },
        )
    }

    printerDialogResult?.let { result ->
        AlertDialog(
            onDismissRequest = { printerDialogResult = null },
            confirmButton = {
                TextButton(onClick = { printerDialogResult = null }) { Text("Закрыть") }
            },
            title = { Text(result.title) },
            text = { Text(result.message) },
        )
    }
    }
}

@Composable
private fun LanguageSelector(
    selected: UiLanguage,
    onSelect: (UiLanguage) -> Unit,
) {
    val labels = mapOf(
        UiLanguage.RUSSIAN to "Русский",
        UiLanguage.ENGLISH to "Английский",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UiLanguage.entries.forEach { language ->
            val isSelected = language == selected
            androidx.compose.material3.Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(language) },
            ) {
                Text(
                    text = labels.getValue(language),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 11.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selected: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            androidx.compose.material3.Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(mode) },
            ) {
                Text(
                    text = mode.label,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 11.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun PositionWorkspaceOverlay(
    selectedTool: PositionWorkspaceTool?,
    selectedModel: PlateObject?,
    selectedModelInsideBed: Boolean?,
    hasModels: Boolean,
    bedWidth: Double,
    bedDepth: Double,
    printableHeight: Double,
    linkScaleAxes: Boolean,
    isWorking: Boolean,
    onLinkScaleAxesChange: (Boolean) -> Unit,
    onToolSelected: (PositionWorkspaceTool) -> Unit,
    onTransformChange: ((PlateObjectTransform) -> PlateObjectTransform) -> Unit,
    onCenter: () -> Unit,
    onPlaceOnBed: () -> Unit,
    onRotate90: () -> Unit,
    onResetScale: () -> Unit,
    workspaceNavigation: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentUiLanguage()
    var uiState by remember { mutableStateOf(PositionWorkspaceOverlayUiState()) }
    val trayInteractionSource = remember { MutableInteractionSource() }
    val overlayActive = positionWorkspaceOverlayIsActive(
        state = uiState,
        positionEditorOpen = selectedTool != null,
    )

    fun dispatch(event: PositionWorkspaceOverlayEvent) {
        uiState = uiState.reduce(event)
    }

    BackHandler(enabled = uiState.toolsExpanded) {
        dispatch(PositionWorkspaceOverlayEvent.DismissTools)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PositionWorkspaceEditor(
                selectedTool = selectedTool,
                selectedModel = selectedModel,
                selectedModelInsideBed = selectedModelInsideBed,
                bedWidth = bedWidth,
                bedDepth = bedDepth,
                printableHeight = printableHeight,
                linkScaleAxes = linkScaleAxes,
                isWorking = isWorking,
                onLinkScaleAxesChange = onLinkScaleAxesChange,
                onTransformChange = onTransformChange,
                onCenter = onCenter,
                onPlaceOnBed = onPlaceOnBed,
                onRotate90 = onRotate90,
                onResetScale = onResetScale,
            )
            workspaceNavigation()
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = uiState.toolsExpanded,
                modifier = Modifier.weight(1f),
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = trayInteractionSource,
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
                            .semantics {
                                contentDescription = positionWorkspaceOverlayPanelDescription(language)
                            },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        items(PositionWorkspaceTool.entries, key = PositionWorkspaceTool::name) { tool ->
                            val enabled = !isWorking && when (tool) {
                                PositionWorkspaceTool.ARRANGE -> hasModels
                                else -> selectedModel != null
                            }
                            val isSelected = tool == selectedTool
                            val tint = when {
                                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            androidx.compose.material3.Surface(
                                modifier = Modifier
                                    .width(78.dp)
                                    .clickable(enabled = enabled) {
                                        dispatch(PositionWorkspaceOverlayEvent.ToolSelected)
                                        onToolSelected(tool)
                                    },
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                                contentColor = tint,
                                shape = RoundedCornerShape(15.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(tool.icon),
                                        contentDescription = localizeUiText(tool.label, language),
                                        tint = tint,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text(
                                        text = localizeUiText(tool.label, language),
                                        color = tint,
                                        fontSize = 9.sp,
                                        lineHeight = 10.sp,
                                        fontWeight = if (isSelected) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            val triggerDescription = positionWorkspaceOverlayTriggerDescription(
                uiState.toolsExpanded,
                language,
            )
            androidx.compose.material3.Surface(
                onClick = { dispatch(PositionWorkspaceOverlayEvent.ToggleTools) },
                modifier = Modifier
                    .size(52.dp)
                    .testTag(PositionWorkspaceTriggerTestTag)
                    .semantics {
                        contentDescription = triggerDescription
                        stateDescription = positionWorkspaceOverlayStateDescription(
                            uiState.toolsExpanded,
                            language,
                        )
                        selected = overlayActive
                    },
                color = if (overlayActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                },
                contentColor = if (overlayActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 10.dp,
                tonalElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_print),
                        contentDescription = null,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionWorkspaceEditor(
    selectedTool: PositionWorkspaceTool?,
    selectedModel: PlateObject?,
    selectedModelInsideBed: Boolean?,
    bedWidth: Double,
    bedDepth: Double,
    printableHeight: Double,
    linkScaleAxes: Boolean,
    isWorking: Boolean,
    onLinkScaleAxesChange: (Boolean) -> Unit,
    onTransformChange: ((PlateObjectTransform) -> PlateObjectTransform) -> Unit,
    onCenter: () -> Unit,
    onPlaceOnBed: () -> Unit,
    onRotate90: () -> Unit,
    onResetScale: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentUiLanguage()
    val selectedTransform = selectedModel?.transform
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (
            selectedTransform != null &&
            selectedTool in setOf(
                PositionWorkspaceTool.POSITION,
                PositionWorkspaceTool.ROTATION,
                PositionWorkspaceTool.SCALE,
            )
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .testTag(PositionWorkspaceEditorTestTag),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 12.dp,
                tonalElevation = 5.dp,
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 252.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            localizeUiText(selectedTool?.label.orEmpty(), language),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        selectedModelInsideBed?.let { insideBed ->
                            Text(
                                localizeUiText(
                                    if (insideBed) "В пределах стола" else "Вне стола",
                                    language,
                                ),
                                color = if (insideBed) Accent else MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    when (selectedTool) {
                        PositionWorkspaceTool.POSITION -> {
                            CompactTransformSlider(
                                label = localizeUiText("Позиция X", language),
                                value = selectedTransform.positionXmm,
                                range = 0f..bedWidth.toFloat(),
                                suffix = localizeUiText("мм", language),
                                enabled = !isWorking,
                                onValueChange = { value ->
                                    onTransformChange { it.copy(positionXmm = value) }
                                },
                            )
                            CompactTransformSlider(
                                label = localizeUiText("Позиция Y", language),
                                value = selectedTransform.positionYmm,
                                range = 0f..bedDepth.toFloat(),
                                suffix = localizeUiText("мм", language),
                                enabled = !isWorking,
                                onValueChange = { value ->
                                    onTransformChange { it.copy(positionYmm = value) }
                                },
                            )
                            CompactTransformSlider(
                                label = localizeUiText("Позиция Z", language),
                                value = selectedTransform.positionZmm,
                                range = 0f..printableHeight.toFloat(),
                                suffix = localizeUiText("мм", language),
                                enabled = !isWorking,
                                onValueChange = { value ->
                                    onTransformChange { it.copy(positionZmm = value) }
                                },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = onCenter,
                                    enabled = !isWorking,
                                    modifier = Modifier.weight(1f),
                                ) { Text(localizeUiText("По центру", language)) }
                                OutlinedButton(
                                    onClick = onPlaceOnBed,
                                    enabled = !isWorking,
                                    modifier = Modifier.weight(1f),
                                ) { Text(localizeUiText("На стол", language)) }
                            }
                        }

                        PositionWorkspaceTool.ROTATION -> {
                            CompactTransformSlider(
                                label = localizeUiText("Поворот X", language),
                                value = selectedTransform.rotationXDegrees,
                                range = 0f..360f,
                                suffix = "°",
                                enabled = !isWorking,
                                onValueChange = { value ->
                                    onTransformChange { it.copy(rotationXDegrees = value) }
                                },
                            )
                            CompactTransformSlider(
                                label = localizeUiText("Поворот Y", language),
                                value = selectedTransform.rotationYDegrees,
                                range = 0f..360f,
                                suffix = "°",
                                enabled = !isWorking,
                                onValueChange = { value ->
                                    onTransformChange { it.copy(rotationYDegrees = value) }
                                },
                            )
                            CompactTransformSlider(
                                label = localizeUiText("Поворот Z", language),
                                value = selectedTransform.rotationZDegrees,
                                range = 0f..360f,
                                suffix = "°",
                                enabled = !isWorking,
                                onValueChange = { value ->
                                    onTransformChange { it.copy(rotationDegrees = value) }
                                },
                            )
                            OutlinedButton(
                                onClick = onRotate90,
                                enabled = !isWorking,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(localizeUiText("Повернуть Z 90°", language)) }
                        }

                        PositionWorkspaceTool.SCALE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    localizeUiText("Сохранять пропорции", language),
                                    fontWeight = FontWeight.Medium,
                                )
                                Switch(
                                    checked = linkScaleAxes,
                                    onCheckedChange = onLinkScaleAxesChange,
                                    enabled = !isWorking,
                                )
                            }
                            if (linkScaleAxes) {
                                CompactTransformSlider(
                                    label = localizeUiText("Масштаб", language),
                                    value = selectedTransform.effectiveScaleX,
                                    range = 0.1f..3f,
                                    suffix = "×",
                                    decimals = 2,
                                    enabled = !isWorking,
                                    onValueChange = { value ->
                                        onTransformChange {
                                            it.copy(
                                                scale = value,
                                                scaleX = 1.0,
                                                scaleY = 1.0,
                                                scaleZ = 1.0,
                                            )
                                        }
                                    },
                                )
                            } else {
                                listOf(
                                    Triple(
                                        localizeUiText("Масштаб X", language),
                                        selectedTransform.effectiveScaleX,
                                        PlateAxis.X,
                                    ),
                                    Triple(
                                        localizeUiText("Масштаб Y", language),
                                        selectedTransform.effectiveScaleY,
                                        PlateAxis.Y,
                                    ),
                                    Triple(
                                        localizeUiText("Масштаб Z", language),
                                        selectedTransform.effectiveScaleZ,
                                        PlateAxis.Z,
                                    ),
                                ).forEach { (label, value, axis) ->
                                    CompactTransformSlider(
                                        label = label,
                                        value = value,
                                        range = 0.1f..3f,
                                        suffix = "×",
                                        decimals = 2,
                                        enabled = !isWorking,
                                        onValueChange = { nextValue ->
                                            onTransformChange { transform ->
                                                when (axis) {
                                                    PlateAxis.X -> transform.copy(scaleX = nextValue / transform.scale)
                                                    PlateAxis.Y -> transform.copy(scaleY = nextValue / transform.scale)
                                                    PlateAxis.Z -> transform.copy(scaleZ = nextValue / transform.scale)
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onResetScale,
                                enabled = !isWorking,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(localizeUiText("Масштаб 1:1", language)) }
                        }

                        else -> Unit
                    }
                }
            }
        }

    }
}

@Composable
private fun ModelWorkspaceNavigation(
    selected: ModelWorkspaceSection,
    onSelect: (ModelWorkspaceSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = currentUiLanguage()
    val sections = listOf(
        ModelWorkspaceSection.VIEW,
        ModelWorkspaceSection.POSITION,
        ModelWorkspaceSection.SLICE,
    )
    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ModelWorkspaceNavigationTestTag)
            .padding(horizontal = 58.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 10.dp,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            sections.forEach { section ->
                val isSelected = section == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            RoundedCornerShape(18.dp),
                        )
                        .clickable { onSelect(section) }
                        .padding(horizontal = 3.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        painter = painterResource(section.icon),
                        contentDescription = localizeUiText(section.label, language),
                        tint = if (isSelected) Accent else Muted,
                        modifier = Modifier.size(23.dp),
                    )
                    Text(
                        text = localizeUiText(section.label, language),
                        color = if (isSelected) Accent else Muted,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private data class ModelRailAction(
    val label: String,
    val icon: Int,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun ModelContextRail(
    section: ModelWorkspaceSection,
    hasModel: Boolean,
    hasGcode: Boolean,
    isWorking: Boolean,
    onImportModel: () -> Unit,
    onRemoveModel: () -> Unit,
    onResetCamera: () -> Unit,
    onShowModel: () -> Unit,
    onShowToolpath: () -> Unit,
    onCenterModel: () -> Unit,
    onRotateModel: () -> Unit,
    onResetScale: () -> Unit,
    onSlice: () -> Unit,
    onSendToPrinter: () -> Unit,
    onSaveGcode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = when (section) {
        ModelWorkspaceSection.FILE -> listOf(
            ModelRailAction("Модели", R.drawable.ic_nav_model, onClick = onImportModel),
            ModelRailAction("Удалить", R.drawable.ic_nav_project, hasModel, onRemoveModel),
        )

        ModelWorkspaceSection.VIEW -> listOf(
            ModelRailAction("Общий вид", R.drawable.ic_workspace_camera, onClick = onResetCamera),
            ModelRailAction("Модель", R.drawable.ic_nav_model, hasModel, onShowModel),
            ModelRailAction("Траектория", R.drawable.ic_workspace_slice, hasGcode, onShowToolpath),
        )

        ModelWorkspaceSection.POSITION -> listOf(
            ModelRailAction("По центру", R.drawable.ic_nav_model, hasModel, onCenterModel),
            ModelRailAction("Повернуть 90°", R.drawable.ic_nav_print, hasModel, onRotateModel),
            ModelRailAction("Масштаб 1:1", R.drawable.ic_nav_profiles, hasModel, onResetScale),
        )

        ModelWorkspaceSection.SLICE -> listOf(
            ModelRailAction("Нарезать", R.drawable.ic_workspace_slice, hasModel && !isWorking, onSlice),
            ModelRailAction("Траектория", R.drawable.ic_nav_model, hasGcode, onShowToolpath),
            ModelRailAction("На печать", R.drawable.ic_nav_printer, hasGcode, onSendToPrinter),
            ModelRailAction("Сохранить", R.drawable.ic_nav_project, hasGcode, onSaveGcode),
        )
    }

    Column(
        modifier = modifier
            .background(Surface.copy(alpha = 0.96f), RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        actions.forEach { action ->
            val tint = if (action.enabled) Muted else Muted.copy(alpha = 0.35f)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(enabled = action.enabled, onClick = action.onClick)
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(action.icon),
                    contentDescription = localizeUiText(action.label, currentUiLanguage()),
                    tint = tint,
                    modifier = Modifier.size(21.dp),
                )
                Text(
                    text = action.label,
                    color = tint,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ProjectProfileCard(
    printerName: String,
    filamentName: String,
    processName: String,
    layerHeight: String,
    nozzleDiameter: String,
    filamentDiameter: String,
    onOpenProfiles: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$printerName · $filamentName", fontWeight = FontWeight.SemiBold)
                Text(
                    "$processName · $layerHeight mm · Nozzle $nozzleDiameter mm · Filament $filamentDiameter mm",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
            TextButton(onClick = onOpenProfiles) { Text("Изменить ›") }
        }
    }
}

@Composable
private fun ProfilesScreen(
    selected: ProfileSection,
    printSettings: PrintSettingsState,
    printDetailLevel: PrintDetailLevel,
    printSettingsCategory: PrintSettingsCategory,
    onPrintDetailLevelChange: (PrintDetailLevel) -> Unit,
    onPrintSettingsCategoryChange: (PrintSettingsCategory) -> Unit,
    onPrintSettingsChange: (PrintSettingsState) -> Unit,
    nozzleDiameter: String,
    onNozzleDiameter: (String) -> Unit,
    filamentDiameter: String,
    onFilamentDiameter: (String) -> Unit,
    nozzleTemperature: String,
    onNozzleTemperature: (String) -> Unit,
    bedTemperature: String,
    onBedTemperature: (String) -> Unit,
    bedWidth: Double,
    bedDepth: Double,
    printableHeight: Double,
    printerFirmware: String,
    systemCatalog: OrcaSystemPrinterCatalog,
    systemCatalogLoading: Boolean,
    cloudState: OrcaProfileSyncState,
    isOrcaSignedIn: Boolean,
    isReviewerDemo: Boolean,
    printerProfileName: String,
    filamentProfileName: String,
    processProfileName: String,
    activePrinterProfileId: String?,
    activeFilamentProfileId: String?,
    activeProcessProfileId: String?,
    activePrinterConnection: OrcaPrinterConnection?,
    activePrinterConnectionSource: String?,
    savedManualPrinterConnection: SavedManualPrinterConnection?,
    printerConnectionStatus: String?,
    printerConnectionTestResult: PrinterConnectionTestResult?,
    isTestingPrinter: Boolean,
    onTestPrinter: () -> Unit,
    onSaveManualPrinter: (ManualPrinterConnectionDraft) -> Unit,
    onActivateManualPrinter: () -> Unit,
    onDeleteManualPrinter: () -> Unit,
    onSyncCloud: () -> Unit,
    onOpenApp: () -> Unit,
    onApplyCloudProfile: (OrcaCloudProfile) -> Unit,
    onApplySystemProfile: (OrcaSystemPrinterProfile) -> Unit,
) {
    var showManualPrinterEditor by remember { mutableStateOf(false) }
    var manualPrinterDraft by remember(savedManualPrinterConnection?.connection) {
        mutableStateOf(ManualPrinterConnectionDraft.from(savedManualPrinterConnection?.connection))
    }
    var manualPrinterEditorError by remember { mutableStateOf<String?>(null) }

    if (selected == ProfileSection.PRINTER) {
        SectionCard(title = "Подключение к принтеру") {
            if (activePrinterConnection != null) {
                Text(activePrinterConnection.printerName, fontWeight = FontWeight.SemiBold)
                activePrinterConnectionSource?.let { source ->
                    Text("Источник: $source", color = Accent, fontSize = 12.sp)
                }
                Text("Адрес: ${activePrinterConnection.host}", color = Muted, fontSize = 13.sp)
                Text("Протокол: ${activePrinterConnection.hostType.label}", color = Muted, fontSize = 13.sp)
                if (activePrinterConnection.port.isNotBlank()) {
                    Text("Порт: ${activePrinterConnection.port}", color = Muted, fontSize = 13.sp)
                }
                Text(
                    if (activePrinterConnection.hasAuthentication) "Авторизация настроена" else "Без API-ключа",
                    color = Muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onTestPrinter,
                    enabled = !isTestingPrinter && activePrinterConnection.hostType.canSendGcode,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (isTestingPrinter) "Проверка…" else "Проверить подключение") }
                printerConnectionTestResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    PrinterConnectionStatusBlock(result)
                } ?: printerConnectionStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Muted, fontSize = 13.sp)
                }
            } else {
                Text("Принтер не подключён", fontWeight = FontWeight.SemiBold)
                Text(
                    "Добавьте адрес Moonraker или OctoPrint вручную либо выберите профиль OrcaCloud с подключением.",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        manualPrinterDraft = ManualPrinterConnectionDraft.from(
                            savedManualPrinterConnection?.connection ?: activePrinterConnection,
                        )
                        manualPrinterEditorError = null
                        showManualPrinterEditor = !showManualPrinterEditor
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (savedManualPrinterConnection == null) "Добавить вручную" else "Изменить вручную")
                }
                if (savedManualPrinterConnection != null && !savedManualPrinterConnection.isActive) {
                    OutlinedButton(
                        onClick = onActivateManualPrinter,
                        modifier = Modifier.weight(1f),
                    ) { Text("Использовать") }
                }
            }

            if (showManualPrinterEditor) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))
                Text("Ручное подключение", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualPrinterDraft.printerName,
                    onValueChange = { manualPrinterDraft = manualPrinterDraft.copy(printerName = it) },
                    label = { Text("Название принтера") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val isMoonraker = manualPrinterDraft.hostType == tech.g24.feresaslicer.auth.PrinterHostType.MOONRAKER
                    if (isMoonraker) {
                        Button(
                            onClick = { manualPrinterDraft = manualPrinterDraft.copy(hostType = tech.g24.feresaslicer.auth.PrinterHostType.MOONRAKER) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Moonraker") }
                    } else {
                        OutlinedButton(
                            onClick = { manualPrinterDraft = manualPrinterDraft.copy(hostType = tech.g24.feresaslicer.auth.PrinterHostType.MOONRAKER) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Moonraker") }
                    }
                    if (!isMoonraker) {
                        Button(
                            onClick = { manualPrinterDraft = manualPrinterDraft.copy(hostType = tech.g24.feresaslicer.auth.PrinterHostType.OCTOPRINT) },
                            modifier = Modifier.weight(1f),
                        ) { Text("OctoPrint") }
                    } else {
                        OutlinedButton(
                            onClick = { manualPrinterDraft = manualPrinterDraft.copy(hostType = tech.g24.feresaslicer.auth.PrinterHostType.OCTOPRINT) },
                            modifier = Modifier.weight(1f),
                        ) { Text("OctoPrint") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualPrinterDraft.host,
                    onValueChange = { manualPrinterDraft = manualPrinterDraft.copy(host = it) },
                    label = { Text("IP-адрес или URL") },
                    placeholder = { Text("192.168.1.42") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualPrinterDraft.port,
                    onValueChange = { manualPrinterDraft = manualPrinterDraft.copy(port = it.filter(Char::isDigit)) },
                    label = { Text("Порт, если нестандартный") },
                    placeholder = { Text(if (manualPrinterDraft.hostType == tech.g24.feresaslicer.auth.PrinterHostType.MOONRAKER) "7125" else "5000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualPrinterDraft.apiKey,
                    onValueChange = { manualPrinterDraft = manualPrinterDraft.copy(apiKey = it) },
                    label = { Text("API-ключ, если нужен") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = manualPrinterDraft.username,
                        onValueChange = { manualPrinterDraft = manualPrinterDraft.copy(username = it) },
                        label = { Text("Логин") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = manualPrinterDraft.password,
                        onValueChange = { manualPrinterDraft = manualPrinterDraft.copy(password = it) },
                        label = { Text("Пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!manualPrinterDraft.host.trim().startsWith("https://", ignoreCase = true) &&
                    (manualPrinterDraft.apiKey.isNotBlank() || manualPrinterDraft.username.isNotBlank())
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "HTTP не шифрует ключ и пароль. Используйте его только в доверенной локальной сети.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
                manualPrinterEditorError?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            runCatching { manualPrinterDraft.validatedConnection() }
                                .onSuccess {
                                    onSaveManualPrinter(manualPrinterDraft)
                                    manualPrinterEditorError = null
                                    showManualPrinterEditor = false
                                }
                                .onFailure { error ->
                                    manualPrinterEditorError = error.message ?: "Проверьте параметры подключения"
                                }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Сохранить") }
                    OutlinedButton(
                        onClick = { showManualPrinterEditor = false },
                        modifier = Modifier.weight(1f),
                    ) { Text("Отмена") }
                }
                if (savedManualPrinterConnection != null) {
                    TextButton(
                        onClick = {
                            onDeleteManualPrinter()
                            showManualPrinterEditor = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Удалить ручное подключение") }
                }
            }
        }
    }

    if (selected == ProfileSection.PROCESS) {
        PrintSettingsPanel(
            profileName = processProfileName,
            activeProfileId = activeProcessProfileId,
            cloudProfiles = cloudState.profiles.filter { it.type == OrcaProfileType.PROCESS },
            cloudProfilesLoading = cloudState.isLoading,
            isSignedIn = isOrcaSignedIn,
            isReviewerDemo = isReviewerDemo,
            settings = printSettings,
            detailLevel = printDetailLevel,
            category = printSettingsCategory,
            onDetailLevelChange = onPrintDetailLevelChange,
            onCategoryChange = onPrintSettingsCategoryChange,
            onSettingsChange = onPrintSettingsChange,
            onProfileSelect = onApplyCloudProfile,
            onRefreshProfiles = onSyncCloud,
            onOpenAccount = onOpenApp,
        )
    }

    val requestedType = when (selected) {
        ProfileSection.PRINTER -> OrcaProfileType.PRINTER
        ProfileSection.FILAMENT -> OrcaProfileType.FILAMENT
        ProfileSection.PROCESS -> OrcaProfileType.PROCESS
    }
    if (selected != ProfileSection.PROCESS) {
        OrcaCloudProfilesCard(
            state = cloudState,
            requestedType = requestedType,
            activeProfileId = when (requestedType) {
                OrcaProfileType.PRINTER -> activePrinterProfileId
                OrcaProfileType.FILAMENT -> activeFilamentProfileId
                OrcaProfileType.PROCESS -> activeProcessProfileId
                OrcaProfileType.OTHER -> null
            },
            isSignedIn = isOrcaSignedIn,
            isReviewerDemo = isReviewerDemo,
            onRefresh = onSyncCloud,
            onOpenApp = onOpenApp,
            onApply = onApplyCloudProfile,
        )
    }

    when (selected) {
        ProfileSection.PRINTER -> {
            OrcaSystemCatalogCard(
                catalog = systemCatalog,
                isLoading = systemCatalogLoading,
                activeName = printerProfileName,
                onApply = onApplySystemProfile,
            )
            SectionCard(title = printerProfileName) {
                Text(
                    "Область печати: ${bedWidth.toInt()} × ${bedDepth.toInt()} × ${printableHeight.toInt()} мм",
                    color = Muted,
                )
                Text("Формат G-code: $printerFirmware", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = nozzleDiameter,
                    onValueChange = onNozzleDiameter,
                    label = { Text("Диаметр сопла, мм") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        ProfileSection.FILAMENT -> SectionCard(title = filamentProfileName) {
            SettingRow(
                firstLabel = "Диаметр, мм",
                firstValue = filamentDiameter,
                onFirstChange = onFilamentDiameter,
                secondLabel = "Сопло, °C",
                secondValue = nozzleTemperature,
                onSecondChange = onNozzleTemperature,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = bedTemperature,
                onValueChange = onBedTemperature,
                label = { Text("Температура стола, °C") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ProfileSection.PROCESS -> Unit
    }

    if (selected != ProfileSection.PROCESS) {
        SectionCard(title = "Хранение профилей") {
            Text("Профили OrcaCloud сохраняются на устройстве и доступны без интернета.")
            Text(
                "Приложение только читает данные OrcaCloud и ничего не изменяет в облаке.",
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
}

private fun printerConnectionResultMessage(result: PrinterConnectionTestResult): String = when (result) {
    is PrinterConnectionTestResult.Failed -> result.failure.userMessage
    is PrinterConnectionTestResult.Connected -> when (val status = result.status) {
        is PrinterStatus.Moonraker -> "Moonraker доступен · Klipper: ${status.klippyState}"
        is PrinterStatus.OctoPrint -> "OctoPrint ${status.serverVersion} доступен"
    }
}

@Composable
private fun PrinterConnectionStatusBlock(result: PrinterConnectionTestResult) {
    when (result) {
        is PrinterConnectionTestResult.Failed -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text("Подключение не установлено", fontWeight = FontWeight.SemiBold)
                Text(result.failure.userMessage, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                result.failure.httpStatus?.let { status ->
                    Text("HTTP $status", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                }
            }
        }

        is PrinterConnectionTestResult.Connected -> {
            val status = result.status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "Состояние: ${printerOperationalStateLabel(status.operationalState)}",
                    fontWeight = FontWeight.SemiBold,
                )
                when (status) {
                    is PrinterStatus.Moonraker -> {
                        status.moonrakerVersion?.let { Text("Moonraker $it", fontSize = 12.sp) }
                        status.warnings.firstOrNull()?.let { warning ->
                            Text("Предупреждение: $warning", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                    is PrinterStatus.OctoPrint -> Text("OctoPrint ${status.serverVersion}", fontSize = 12.sp)
                }
                status.job.fileName?.let { fileName ->
                    Text("Задание: $fileName", fontSize = 13.sp)
                }
                if (status.job.state != PrinterJobState.UNKNOWN && status.job.state != PrinterJobState.IDLE) {
                    val progress = status.job.progress?.let { " · ${(it * 100).roundToInt()}%" }.orEmpty()
                    Text("Печать: ${printerJobStateLabel(status.job.state)}$progress", fontSize = 13.sp)
                }
                val temperatures = listOfNotNull(
                    status.temperatures.tool?.let { temperature ->
                        formatPrinterTemperature("Сопло", temperature.actualCelsius, temperature.targetCelsius)
                    },
                    status.temperatures.bed?.let { temperature ->
                        formatPrinterTemperature("Стол", temperature.actualCelsius, temperature.targetCelsius)
                    },
                )
                if (temperatures.isNotEmpty()) {
                    Text(temperatures.joinToString(" · "), fontSize = 13.sp)
                }
                Text(
                    if (status.canStart) "Принтер готов к запуску" else "Запуск сейчас недоступен",
                    color = if (status.canStart) Accent else Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun printerOperationalStateLabel(state: PrinterOperationalState): String = when (state) {
    PrinterOperationalState.ONLINE -> "в сети"
    PrinterOperationalState.READY -> "готов"
    PrinterOperationalState.PRINTING -> "печатает"
    PrinterOperationalState.PAUSED -> "пауза"
    PrinterOperationalState.STARTING -> "запускается"
    PrinterOperationalState.ERROR -> "ошибка"
    PrinterOperationalState.OFFLINE -> "не готов"
    PrinterOperationalState.UNKNOWN -> "неизвестно"
}

private fun printerJobStateLabel(state: PrinterJobState): String = when (state) {
    PrinterJobState.IDLE -> "ожидание"
    PrinterJobState.PRINTING -> "идёт"
    PrinterJobState.PAUSED -> "приостановлена"
    PrinterJobState.COMPLETE -> "завершена"
    PrinterJobState.CANCELLED -> "отменена"
    PrinterJobState.ERROR -> "ошибка"
    PrinterJobState.UNKNOWN -> "неизвестно"
}

private fun formatPrinterTemperature(label: String, actual: Double?, target: Double?): String? {
    if (actual == null && target == null) return null
    val actualText = actual?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
    val targetText = target?.let { String.format(Locale.US, "%.0f", it) }
    return if (targetText != null) "$label $actualText/$targetText °C" else "$label $actualText °C"
}

@Composable
private fun OrcaSystemCatalogCard(
    catalog: OrcaSystemPrinterCatalog,
    isLoading: Boolean,
    activeName: String,
    onApply: (OrcaSystemPrinterProfile) -> Unit,
) {
    var query by remember(catalog) { mutableStateOf("") }
    var selectedVendor by remember(catalog, activeName) { mutableStateOf<String?>(null) }
    var selectedModel by remember(catalog, activeName) { mutableStateOf<String?>(null) }
    var selectedProfileName by remember(catalog, activeName) { mutableStateOf<String?>(null) }

    val matchingProfiles = remember(catalog, query) {
        OrcaPrinterCatalogSelection.matchingProfiles(catalog, query)
    }
    val activeProfile = remember(catalog, activeName) {
        catalog.printers.firstOrNull { it.name == activeName }
    }
    val vendors = remember(matchingProfiles) {
        OrcaPrinterCatalogSelection.vendors(matchingProfiles)
    }
    val effectiveVendor = selectedVendor
        ?.takeIf(vendors::contains)
        ?: activeProfile?.vendor?.takeIf(vendors::contains)
        ?: vendors.firstOrNull()
    val models = remember(matchingProfiles, effectiveVendor) {
        effectiveVendor?.let { OrcaPrinterCatalogSelection.models(matchingProfiles, it) }.orEmpty()
    }
    val activeModel = activeProfile?.model?.ifBlank { activeProfile.name }
    val effectiveModel = selectedModel
        ?.takeIf(models::contains)
        ?: activeModel?.takeIf(models::contains)
        ?: models.firstOrNull()
    val profiles = remember(matchingProfiles, effectiveVendor, effectiveModel) {
        if (effectiveVendor == null || effectiveModel == null) {
            emptyList()
        } else {
            OrcaPrinterCatalogSelection.profiles(
                profiles = matchingProfiles,
                vendor = effectiveVendor,
                model = effectiveModel,
            )
        }
    }
    val effectiveProfile = selectedProfileName
        ?.let { selectedName -> profiles.firstOrNull { it.name == selectedName } }
        ?: activeProfile?.takeIf { it in profiles }
        ?: profiles.firstOrNull()

    SectionCard(title = "Каталог принтеров OrcaSlicer") {
        if (isLoading) {
            Text("Загрузка встроенного каталога…", color = Muted)
            return@SectionCard
        }
        if (catalog.printers.isEmpty()) {
            Text("Не удалось открыть встроенный каталог.", color = MaterialTheme.colorScheme.error)
            return@SectionCard
        }
        Text(
            "${catalog.vendorCount} производителя · ${catalog.printers.size} профилей · доступно офлайн",
            color = Muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Производитель или модель") },
            placeholder = { Text("Например, Kingroon KP3S") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        if (matchingProfiles.isEmpty()) {
            Text("Профили не найдены", color = Muted)
        } else {
            Text(
                "Найдено профилей: ${matchingProfiles.size}",
                color = Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            CatalogDropdown(
                label = "Производитель",
                value = effectiveVendor,
                options = vendors,
                optionText = { it },
                onSelect = { vendor ->
                    selectedVendor = vendor
                    selectedModel = null
                    selectedProfileName = null
                },
            )
            Spacer(Modifier.height(8.dp))
            CatalogDropdown(
                label = "Модель",
                value = effectiveModel,
                options = models,
                optionText = { it },
                onSelect = { model ->
                    selectedModel = model
                    selectedProfileName = null
                },
            )
            Spacer(Modifier.height(8.dp))
            CatalogDropdown(
                label = "Сопло / профиль",
                value = effectiveProfile,
                options = profiles,
                optionText = { profile ->
                    "${formatProfileNumber(profile.nozzleDiameter)} мм · ${profile.name}"
                },
                onSelect = { profile -> selectedProfileName = profile.name },
            )

            effectiveProfile?.let { profile ->
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Text(profile.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Стол ${profile.bedWidth.toInt()} × ${profile.bedDepth.toInt()} × " +
                            "${profile.printableHeight.toInt()} мм · сопло " +
                            "${formatProfileNumber(profile.nozzleDiameter)} мм",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                    if (profile.name == activeName) {
                        Text("Активный профиль", color = Accent, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onApply(profile) },
                    enabled = profile.name != activeName,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (profile.name == activeName) "Профиль выбран" else "Применить профиль")
                }
            }
        }
    }
}

@Composable
private fun <T> CatalogDropdown(
    label: String,
    value: T?,
    options: List<T>,
    optionText: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = options.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(label, color = Muted, fontSize = 11.sp)
                    Text(
                        value?.let(optionText) ?: "Нет вариантов",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text("⌄", fontSize = 18.sp)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp, max = 340.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            optionText(option),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun formatProfileNumber(value: Double): String =
    String.format(Locale.US, if (value % 1.0 == 0.0) "%.0f" else "%.2f", value)

@Composable
private fun OrcaCloudProfilesCard(
    state: OrcaProfileSyncState,
    requestedType: OrcaProfileType,
    activeProfileId: String?,
    isSignedIn: Boolean,
    isReviewerDemo: Boolean,
    onRefresh: () -> Unit,
    onOpenApp: () -> Unit,
    onApply: (OrcaCloudProfile) -> Unit,
) {
    var showAllProfiles by remember(requestedType) { mutableStateOf(false) }
    SectionCard(title = if (isReviewerDemo) "Локальные demo-профили" else "Профили OrcaCloud") {
        if (state.isLoading) {
            Text(
                if (state.profiles.isEmpty()) "Загрузка профилей…" else "Обновление профилей…",
                color = Muted,
            )
            Spacer(Modifier.height(8.dp))
        }

        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        val profiles = state.profiles.filter { it.type == requestedType }
        when {
            profiles.isNotEmpty() -> {
                Text(
                    "Профилей: ${profiles.size} · ${when {
                        isReviewerDemo -> "локальный demo"
                        state.isCached -> "локальный кэш"
                        else -> "загружены из облака"
                    }}",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                val visibleProfiles = if (showAllProfiles) profiles else profiles.take(8)
                visibleProfiles.forEach { profile ->
                    val isSelectedProfile = profileMatchesSelection(
                        profile = profile,
                        activeProfileId = activeProfileId,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.Medium)
                            if (requestedType == OrcaProfileType.PRINTER) {
                                profile.printerConnection()?.let { connection ->
                                    Text(
                                        "${connection.hostType.label} · ${connection.host}",
                                        color = Muted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            if (isSelectedProfile) {
                                Text("Активен", color = Accent, fontSize = 12.sp)
                            }
                        }
                        TextButton(onClick = { onApply(profile) }) {
                            Text(
                                when {
                                    isSelectedProfile -> "Выбран"
                                    else -> "Выбрать"
                                }
                            )
                        }
                    }
                }
                if (profiles.size > 8) {
                    TextButton(
                        onClick = { showAllProfiles = !showAllProfiles },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (showAllProfiles) "Свернуть список" else "Показать все (${profiles.size})")
                    }
                }
            }
            isSignedIn && !state.isLoading -> Text("В OrcaCloud нет пользовательских профилей этого типа.", color = Muted)
            !isSignedIn && state.profiles.isEmpty() -> {
                Text("Войдите в OrcaCloud, чтобы загрузить свои профили.", color = Muted)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
                    Text("Открыть меню приложения")
                }
            }
        }

        if (isSignedIn) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        state.isLoading -> "Синхронизация…"
                        isReviewerDemo -> "Сбросить demo-профили"
                        else -> "Обновить из OrcaCloud"
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Локальный слайсер бесплатный", fontWeight = FontWeight.SemiBold)
                Text("Синхронизация профилей OrcaCloud доступна после входа.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun OrcaCloudAccountCard(
    state: OrcaAuthState,
    onSignIn: (OrcaAuthProvider) -> Unit,
    onReviewerDemoSignIn: (String, String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onManageAccount: () -> Unit,
) {
    var showReviewerDemoLogin by remember { mutableStateOf(false) }
    var reviewerUsername by remember { mutableStateOf("") }
    var reviewerPassword by remember { mutableStateOf("") }

    SectionCard(title = "Учётная запись OrcaCloud") {
        when (state) {
            OrcaAuthState.Loading -> Text("Проверка сохранённой сессии…", color = Muted)
            OrcaAuthState.SignedOut -> {
                Text("Безопасный вход откроется в браузере. Приложение не получает ваш пароль.", color = Muted)
                Spacer(Modifier.height(10.dp))
                AuthProviderButtons(onSignIn)
                ReviewerDemoLogin(
                    expanded = showReviewerDemoLogin,
                    username = reviewerUsername,
                    password = reviewerPassword,
                    onExpandedChange = { showReviewerDemoLogin = it },
                    onUsernameChange = { reviewerUsername = it },
                    onPasswordChange = { reviewerPassword = it },
                    onSignIn = { onReviewerDemoSignIn(reviewerUsername, reviewerPassword) },
                )
            }
            is OrcaAuthState.WaitingForBrowser -> {
                Text("Завершите вход через ${state.provider.label} в браузере.", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Отменить вход")
                }
            }
            is OrcaAuthState.SignedIn -> {
                if (state.mode == OrcaAuthMode.REVIEW_DEMO) {
                    Text("Локальный demo для проверки Google Play", color = Accent, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                }
                Text(state.account.displayName, fontWeight = FontWeight.SemiBold)
                if (state.account.email.isNotBlank()) Text(state.account.email, color = Muted, fontSize = 13.sp)
                if (state.mode == OrcaAuthMode.REVIEW_DEMO) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Используются только встроенные тестовые профили. Сеть и реальные данные отключены.",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Text("Выйти")
                }
            }
            is OrcaAuthState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Повторить") }
                    Button(
                        onClick = { onSignIn(OrcaAuthProvider.GOOGLE) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Войти") }
                }
                ReviewerDemoLogin(
                    expanded = showReviewerDemoLogin,
                    username = reviewerUsername,
                    password = reviewerPassword,
                    onExpandedChange = { showReviewerDemoLogin = it },
                    onUsernameChange = { reviewerUsername = it },
                    onPasswordChange = { reviewerPassword = it },
                    onSignIn = { onReviewerDemoSignIn(reviewerUsername, reviewerPassword) },
                )
            }
        }
        val isReviewerDemo = (state as? OrcaAuthState.SignedIn)?.mode == OrcaAuthMode.REVIEW_DEMO
        if (!isReviewerDemo && state !is OrcaAuthState.Loading && state !is OrcaAuthState.WaitingForBrowser) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onManageAccount, modifier = Modifier.fillMaxWidth()) {
                Text("Управление и удаление аккаунта", color = Accent)
            }
        }
    }
}

@Composable
private fun ReviewerDemoLogin(
    expanded: Boolean,
    username: String,
    password: String,
    onExpandedChange: (Boolean) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = { onExpandedChange(!expanded) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (expanded) "Скрыть локальный demo Google Play" else "Локальный demo для проверки Google Play")
    }
    if (!expanded) return

    Text(
        "Вход открывает встроенные тестовые профили без подключения к OrcaCloud или принтеру.",
        color = Muted,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Demo-логин") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Demo-пароль") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onSignIn,
        enabled = username.isNotBlank() && password.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Открыть локальный demo")
    }
}

@Composable
private fun AuthProviderButtons(onSignIn: (OrcaAuthProvider) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onSignIn(OrcaAuthProvider.GOOGLE) },
            modifier = Modifier.weight(1f),
        ) { Text("Google") }
        OutlinedButton(
            onClick = { onSignIn(OrcaAuthProvider.GITHUB) },
            modifier = Modifier.weight(1f),
        ) { Text("GitHub") }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(
    firstLabel: String,
    firstValue: String,
    onFirstChange: (String) -> Unit,
    secondLabel: String,
    secondValue: String,
    onSecondChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = firstValue,
            onValueChange = onFirstChange,
            label = { Text(firstLabel) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = secondValue,
            onValueChange = onSecondChange,
            label = { Text(secondLabel) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactTransformSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    decimals: Int = 1,
    enabled: Boolean = true,
    onValueChange: (Double) -> Unit,
) {
    val formattedValue = String.format(Locale.US, "%.${decimals}f", value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(min = 92.dp, max = 112.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                maxLines = 1,
            )
            Text(
                text = "$formattedValue $suffix",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 14.sp,
                maxLines = 1,
            )
        }
        Slider(
            value = value.toFloat().coerceIn(range.start, range.endInclusive),
            onValueChange = { onValueChange(it.toDouble()) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            valueRange = range,
        )
    }
}

@Composable
private fun TransformSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    decimals: Int = 1,
    enabled: Boolean = true,
    onValueChange: (Double) -> Unit,
) {
    val formattedValue = String.format(Locale.US, "%.${decimals}f", value)
    Text("$label: $formattedValue $suffix", fontWeight = FontWeight.Medium)
    Slider(
        value = value.toFloat().coerceIn(range.start, range.endInclusive),
        onValueChange = { onValueChange(it.toDouble()) },
        valueRange = range,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrcaSliceWorkspace(
    modelFile: File?,
    gcodeFile: File?,
    report: SliceReport?,
    transform: ModelTransform,
    bedWidth: Double,
    bedDepth: Double,
    darkTheme: Boolean,
    isWorking: Boolean,
    minimumLayer: Int,
    maximumLayer: Int,
    colorMode: ToolpathColorMode,
    showExtrusion: Boolean,
    showTravel: Boolean,
    progress: Float,
    layerHeightMm: Double,
    cameraResetRequest: Int,
    initialCameraState: ViewerCameraState?,
    modelObjects: List<ViewerModelObject>,
    selectedObjectId: String?,
    onObjectSelected: (ViewerObjectSelection) -> Unit,
    onSceneState: (ViewerSceneState) -> Unit,
    onCameraStateChange: (ViewerCameraState) -> Unit,
    onViewerError: (String) -> Unit,
    onSlice: () -> Unit,
    onMinimumLayerChange: (Int) -> Unit,
    onMaximumLayerChange: (Int) -> Unit,
    onColorModeChange: (ToolpathColorMode) -> Unit,
    onShowExtrusionChange: (Boolean) -> Unit,
    onShowTravelChange: (Boolean) -> Unit,
    onProgressChange: (Float) -> Unit,
    onResetCamera: () -> Unit,
    onSaveGcode: () -> Unit,
    onSendToPrinter: () -> Unit,
    isSendingToPrinter: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasModels = modelObjects.isNotEmpty() || modelFile != null
    val successfulReport = report?.takeIf { it.success }
    if (gcodeFile == null || successfulReport == null) {
        Box(modifier = modifier.fillMaxSize()) {
            if (!isWorking) {
                ModelViewer(
                    modelFile = modelFile,
                    gcodeFile = null,
                    transform = transform,
                    bedWidth = bedWidth,
                    bedDepth = bedDepth,
                    mode = ViewerMode.MODEL,
                    darkTheme = darkTheme,
                    cameraResetRequest = cameraResetRequest,
                    initialCameraState = initialCameraState,
                    modelObjects = modelObjects,
                    selectedObjectId = selectedObjectId,
                    onObjectSelected = onObjectSelected,
                    onSceneState = onSceneState,
                    onCameraStateChange = onCameraStateChange,
                    onError = onViewerError,
                    viewerHeight = null,
                    showStatus = false,
                    modifier = Modifier.fillMaxSize(),
                )

                Button(
                    onClick = onSlice,
                    enabled = hasModels,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 88.dp)
                        .fillMaxWidth()
                        .testTag(ModelSliceActionTestTag),
                ) {
                    Text("НАРЕЗАТЬ МОДЕЛЬ")
                }
            } else {
                // Orca's native pipeline is memory intensive. Releasing the WebView/WebGL
                // renderer while it runs avoids keeping a second copy of the model and its
                // GPU resources alive at the same time, which can otherwise make Android's
                // low-memory killer terminate Feresa on smaller devices.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 10.dp,
                        tonalElevation = 4.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text("Нарезка модели…", color = Muted, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        return
    }

    var colorMenuExpanded by remember { mutableStateOf(false) }
    var gcodeExpanded by remember(gcodeFile) { mutableStateOf(false) }
    var toolpathSelection by remember(gcodeFile) { mutableStateOf<ViewerToolpathSelection?>(null) }
    var renderedSegmentCount by remember(gcodeFile) { mutableStateOf(0L) }
    val lastLayer = (successfulReport.layers.toInt() - 1).coerceAtLeast(0)
    val sliderEnd = lastLayer.coerceAtLeast(1).toFloat()
    val displayedSegmentCount = toolpathSelection?.displayedSegmentCount ?: 0
    val eligibleSegmentCount = toolpathSelection?.eligibleSegmentCount ?: 0
    val maximumLayerZ = toolpathSelection?.maximumLayerZ ?: (maximumLayer + 1) * layerHeightMm
    val minimumLayerZ = toolpathSelection?.minimumLayerZ ?: (minimumLayer + 1) * layerHeightMm

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ModelSliceResultTestTag),
    ) {
        ModelViewer(
            modelFile = modelFile,
            gcodeFile = gcodeFile,
            transform = transform,
            bedWidth = bedWidth,
            bedDepth = bedDepth,
            mode = ViewerMode.TOOLPATH,
            darkTheme = darkTheme,
            toolpathMinimumLayer = minimumLayer,
            toolpathMaximumLayer = maximumLayer,
            toolpathColorMode = colorMode,
            toolpathProgress = progress,
            showExtrusion = showExtrusion,
            showTravel = showTravel,
            includeToolpathCommands = gcodeExpanded,
            cameraResetRequest = cameraResetRequest,
            initialCameraState = initialCameraState,
            modelObjects = modelObjects,
            selectedObjectId = selectedObjectId,
            onObjectSelected = onObjectSelected,
            onSceneState = onSceneState,
            onToolpathRendered = { segmentCount ->
                renderedSegmentCount = segmentCount.toLong()
            },
            onToolpathSelection = { toolpathSelection = it },
            onCameraStateChange = onCameraStateChange,
            onError = onViewerError,
            viewerHeight = null,
            showStatus = false,
            modifier = Modifier
                .fillMaxSize()
                .testTag(ModelToolpathViewerTestTag)
                .semantics {
                    renderedToolpathSegments = renderedSegmentCount
                },
        )

        androidx.compose.material3.Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 10.dp,
            tonalElevation = 4.dp,
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    Box(modifier = Modifier.width(148.dp)) {
                        OutlinedButton(
                            onClick = { colorMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(colorMode.label, maxLines = 1) }
                        DropdownMenu(
                            expanded = colorMenuExpanded,
                            onDismissRequest = { colorMenuExpanded = false },
                        ) {
                            ToolpathColorMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        onColorModeChange(mode)
                                        colorMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = onResetCamera) { Text("Вид") }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Печать", fontSize = 12.sp)
                        Switch(checked = showExtrusion, onCheckedChange = onShowExtrusionChange)
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Перемещения", fontSize = 12.sp)
                        Switch(checked = showTravel, onCheckedChange = onShowTravelChange)
                    }
                }
            }
        }

        androidx.compose.material3.Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 82.dp, end = 8.dp, bottom = 252.dp)
                .width(64.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 8.dp,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${maximumLayer + 1}\n${String.format(Locale.US, "%.2f", maximumLayerZ)}",
                    textAlign = TextAlign.Center,
                    color = Accent,
                    fontSize = 10.sp,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    RangeSlider(
                        value = minimumLayer.coerceIn(0, lastLayer).toFloat()..
                            maximumLayer.coerceIn(0, lastLayer).toFloat(),
                        onValueChange = { range ->
                            onMinimumLayerChange(range.start.roundToInt())
                            onMaximumLayerChange(range.endInclusive.roundToInt())
                        },
                        valueRange = 0f..sliderEnd,
                        steps = (lastLayer - 1).coerceAtLeast(0),
                        enabled = lastLayer > 0,
                        modifier = Modifier
                            .requiredWidth(230.dp)
                            .graphicsLayer { rotationZ = 270f },
                    )
                }
                Text(
                    "${minimumLayer + 1}\n${String.format(Locale.US, "%.2f", minimumLayerZ)}",
                    textAlign = TextAlign.Center,
                    color = Accent,
                    fontSize = 10.sp,
                )
            }
        }

        androidx.compose.material3.Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 8.dp, end = 8.dp, bottom = 82.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 10.dp,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    "Ход траектории $displayedSegmentCount / $eligibleSegmentCount",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = onProgressChange,
                    valueRange = 0f..1f,
                    enabled = eligibleSegmentCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                )

                toolpathSelection?.takeIf { it.selected }?.let { selection ->
                    val lineWidth = selection.lineWidthMm
                        ?.let { String.format(Locale.US, "%.3f", it) }
                        ?: "—"
                    val layerHeight = selection.layerHeightMm
                        ?.let { String.format(Locale.US, "%.3f", it) }
                        ?: "—"
                    Text(
                        String.format(
                            Locale.US,
                            "X %.2f   Y %.2f   Z %.2f мм\nСкорость %.0f мм/с · Тип: %s\nШирина %s мм · Высота слоя %s мм",
                            selection.x,
                            selection.y,
                            selection.z,
                            selection.speedMmSeconds,
                            selection.lineTypeLabel,
                            lineWidth,
                            layerHeight,
                        ),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                }

                OutlinedButton(
                    onClick = { gcodeExpanded = !gcodeExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (gcodeExpanded) "Скрыть команды G-code" else "Показать команды G-code")
                }
                if (gcodeExpanded) {
                    val commands = toolpathSelection?.commands.orEmpty()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF202220), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        if (commands.isEmpty()) {
                            Text(
                                "Нет видимых команд",
                                color = Color(0xFFCDD5D0),
                                fontFamily = FontFamily.Monospace,
                            )
                        } else {
                            commands.forEach { command ->
                                Text(
                                    "${command.lineNumber.toString().padStart(6)}  ${command.source}",
                                    color = if (command.active) Color(0xFFFFA15A) else Color(0xFFD6DDD8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Text(
                    "${successfulReport.layers} слоёв · ${successfulReport.extrusionSegments} сегментов · ${formatPrintDuration(successfulReport.estimatedPrintTimeSeconds)}",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Text(
                    "Прочитано ${toolpathSelection?.lineCount ?: 0} строк G-code",
                    color = Muted,
                    fontSize = 11.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onSaveGcode, modifier = Modifier.weight(1f)) {
                        Text("Сохранить")
                    }
                    Button(
                        onClick = onSendToPrinter,
                        enabled = !isSendingToPrinter,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (isSendingToPrinter) "Отправка…" else "На печать")
                    }
                }
            }
        }
    }
}

@Composable
private fun SlicePreviewControls(
    report: SliceReport,
    minimumLayer: Int,
    maximumLayer: Int,
    colorMode: ToolpathColorMode,
    showExtrusion: Boolean,
    showTravel: Boolean,
    layerHeightMm: Double,
    onMinimumLayerChange: (Int) -> Unit,
    onMaximumLayerChange: (Int) -> Unit,
    onColorModeChange: (ToolpathColorMode) -> Unit,
    onShowExtrusionChange: (Boolean) -> Unit,
    onShowTravelChange: (Boolean) -> Unit,
) {
    var colorMenuExpanded by remember { mutableStateOf(false) }
    val lastLayer = (report.layers.toInt() - 1).coerceAtLeast(0)
    val sliderEnd = lastLayer.coerceAtLeast(1).toFloat()

    Text("Просмотр нарезки", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    Text("Окраска траектории", color = Muted, fontSize = 12.sp)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { colorMenuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(colorMode.label)
        }
        DropdownMenu(
            expanded = colorMenuExpanded,
            onDismissRequest = { colorMenuExpanded = false },
        ) {
            ToolpathColorMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onColorModeChange(mode)
                        colorMenuExpanded = false
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    Text(
        "Слои ${minimumLayer + 1}–${maximumLayer + 1} из ${report.layers} · Z ${String.format(Locale.US, "%.2f", (maximumLayer + 1) * layerHeightMm)} мм",
        fontWeight = FontWeight.Medium,
    )
    Text("Нижний видимый слой", color = Muted, fontSize = 12.sp)
    Slider(
        value = minimumLayer.coerceIn(0, lastLayer).toFloat(),
        onValueChange = { onMinimumLayerChange(it.toInt()) },
        valueRange = 0f..sliderEnd,
        steps = (lastLayer - 1).coerceAtLeast(0),
        enabled = lastLayer > 0,
    )
    Text("Верхний видимый слой", color = Muted, fontSize = 12.sp)
    Slider(
        value = maximumLayer.coerceIn(0, lastLayer).toFloat(),
        onValueChange = { onMaximumLayerChange(it.toInt()) },
        valueRange = 0f..sliderEnd,
        steps = (lastLayer - 1).coerceAtLeast(0),
        enabled = lastLayer > 0,
    )

    PreviewVisibilityRow(
        label = "Экструзия",
        color = Color(0xFFF26B38),
        checked = showExtrusion,
        onCheckedChange = onShowExtrusionChange,
    )
    PreviewVisibilityRow(
        label = "Перемещения",
        color = Color(0xFF3C8FD8),
        checked = showTravel,
        onCheckedChange = onShowTravelChange,
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
    PreviewMetric("Расчётное время", formatPrintDuration(report.estimatedPrintTimeSeconds))
    PreviewMetric("Пруток", String.format(Locale.US, "%.2f м · %.2f г", report.filamentLengthMm / 1000.0, report.filamentWeightGrams))
    PreviewMetric("Траектория печати", String.format(Locale.US, "%.1f м", report.extrusionDistanceMm / 1000.0))
    PreviewMetric("Перемещения", String.format(Locale.US, "%.1f м · %d", report.travelDistanceMm / 1000.0, report.travelSegments))
    PreviewMetric("Сегменты", report.extrusionSegments.toString())

    Text(
        "Стены, оболочки, заполнение и поддержки построены движком OrcaSlicer согласно текущему профилю.",
        color = Muted,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PreviewVisibilityRow(
    label: String,
    color: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("●", color = color, fontSize = 18.sp)
        Text(label, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PreviewMetric(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

private fun formatPrintDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format(Locale.US, "%d ч %02d мин", hours, minutes)
        minutes > 0 -> String.format(Locale.US, "%d мин %02d с", minutes, seconds)
        else -> "$seconds с"
    }
}

private fun formatFileSize(sizeBytes: Long): String = when {
    sizeBytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f МБ", sizeBytes / (1024.0 * 1024.0))
    sizeBytes >= 1024L -> String.format(Locale.US, "%.1f КБ", sizeBytes / 1024.0)
    else -> "$sizeBytes Б"
}

@Composable
private fun ResultCard(report: SliceReport) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("G-code готов", fontWeight = FontWeight.SemiBold)
            Text("Слоёв: ${report.layers}")
            Text("Сегментов экструзии: ${report.extrusionSegments}")
            Text(
                "Расчётный расход филамента: ${String.format(Locale.US, "%.2f", report.filamentLengthMm / 1000.0)} м"
            )
            Spacer(Modifier.height(4.dp))
            Text("Нарезано движком OrcaSlicer", color = Muted, fontSize = 12.sp)
        }
    }
}

private fun parseSettings(
    layerHeight: String,
    nozzleDiameter: String,
    filamentDiameter: String,
    nozzleTemperature: String,
    bedTemperature: String,
    printSpeed: String,
    bedWidth: Double,
    bedDepth: Double,
    transform: ModelTransform,
): SlicerSettings? = runCatching {
    SlicerSettings(
        layerHeightMm = layerHeight.toDouble(),
        nozzleDiameterMm = nozzleDiameter.toDouble(),
        filamentDiameterMm = filamentDiameter.toDouble(),
        nozzleTemperatureC = nozzleTemperature.toInt(),
        bedTemperatureC = bedTemperature.toInt(),
        printSpeedMmS = printSpeed.toDouble(),
        bedWidthMm = bedWidth,
        bedDepthMm = bedDepth,
        modelPositionXmm = transform.positionX,
        modelPositionYmm = transform.positionY,
        modelRotationDegrees = transform.rotationDegrees,
        modelScale = transform.scale,
    )
}.getOrNull()

private val UiResolvedProfileKeys: Set<String> by lazy {
    PrintSettingsState().toOrcaProcessSettingsPayload().keys + setOf(
        "nozzle_diameter",
        "printable_area",
        "bed_shape",
        "printable_height",
        "max_print_height",
        "gcode_flavor",
        "filament_diameter",
        "nozzle_temperature",
        "nozzle_temperature_initial_layer",
        "hot_plate_temp",
        "textured_plate_temp",
        "cool_plate_temp",
    )
}

internal fun OrcaCloudProfile.toPersistedCloudRef(
    authState: OrcaAuthState,
    cachedOwnerAccountId: String?,
): PersistedProfileRef {
    val ownerAccountId = (authState as? OrcaAuthState.SignedIn)?.account?.id
        ?: cachedOwnerAccountId
    require(!ownerAccountId.isNullOrBlank()) {
        "Cannot select an OrcaCloud profile without its owning account"
    }
    return PersistedProfileRef(
        origin = PersistedProfileOrigin.CLOUD,
        type = type,
        id = id,
        name = name,
        accountId = ownerAccountId,
    )
}

internal fun resolvePersistedProfileRef(
    reference: PersistedProfileRef?,
    authState: OrcaAuthState,
    cloudProfiles: List<OrcaCloudProfile>,
    cloudProfileOwnerAccountId: String?,
    systemCatalog: OrcaSystemPresetCatalog?,
): OrcaCloudProfile? {
    reference ?: return null
    return when (reference.origin) {
        PersistedProfileOrigin.CLOUD -> {
            val accountMatches = when (authState) {
                is OrcaAuthState.SignedIn -> reference.accountId != null &&
                    reference.accountId == authState.account.id &&
                    reference.accountId == cloudProfileOwnerAccountId
                OrcaAuthState.Loading, is OrcaAuthState.Error -> reference.accountId != null &&
                    reference.accountId == cloudProfileOwnerAccountId
                OrcaAuthState.SignedOut, is OrcaAuthState.WaitingForBrowser -> false
            }
            if (!accountMatches) null else cloudProfiles.firstOrNull {
                it.type == reference.type && it.id == reference.id
            }
        }
        PersistedProfileOrigin.SYSTEM -> systemCatalog?.let { catalog ->
            catalog.bundledProfileById(reference.id, reference.type) ?: runCatching {
                catalog.bundledProfile(
                    type = reference.type,
                    name = reference.name,
                    contextHint = reference.contextHint ?: reference.name,
                )
            }.getOrNull()
        }
    }
}

internal fun resolveProfileSettingsForUi(
    catalog: OrcaSystemPresetCatalog,
    profile: OrcaCloudProfile,
    availableProfiles: List<OrcaCloudProfile>,
    printerContext: OrcaCloudProfile? = null,
): Map<String, String> {
    val selected = when (profile.type) {
        OrcaProfileType.PRINTER -> OrcaSelectedProfiles(
            printer = profile,
            availableCloudProfiles = availableProfiles,
        )
        OrcaProfileType.FILAMENT -> OrcaSelectedProfiles(
            printer = printerContext,
            filament = profile,
            availableCloudProfiles = availableProfiles,
        )
        OrcaProfileType.PROCESS -> OrcaSelectedProfiles(
            printer = printerContext,
            process = profile,
            availableCloudProfiles = availableProfiles,
        )
        OrcaProfileType.OTHER -> error("Unsupported Orca profile type: ${profile.type}")
    }
    val hydrated = catalog.augment(selected)
    return OrcaProfileSettingsResolver.resolve(
        profile = profile,
        availableProfiles = hydrated.availableCloudProfiles,
        supportedKeys = UiResolvedProfileKeys,
    )
}

private fun firstProfileSetting(settings: Map<String, String>, vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> settings[key] }

private fun printerDimensions(settings: Map<String, String>): Pair<Double, Double>? {
    val serialized = settings["printable_area"]
        ?: settings["bed_shape"]
        ?: return null
    val points = serialized.split(',', ';').mapNotNull { point ->
        val coordinates = point.trim().split('x', 'X')
        if (coordinates.size != 2) return@mapNotNull null
        val x = coordinates[0].trim().toDoubleOrNull() ?: return@mapNotNull null
        val y = coordinates[1].trim().toDoubleOrNull() ?: return@mapNotNull null
        x to y
    }
    if (points.size < 3) return null
    val width = points.maxOf { it.first } - points.minOf { it.first }
    val depth = points.maxOf { it.second } - points.minOf { it.second }
    return if (width > 0.0 && depth > 0.0) width to depth else null
}

private fun copyModelToCache(
    context: Context,
    uri: Uri,
    objectId: PlateObjectId,
): PlateModelSource {
    val metadata = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
        name to size
    }
    // Some document providers omit DISPLAY_NAME or report no extension. The importer validates
    // the actual bytes and only uses a present extension as a strict consistency check.
    val displayName = metadata?.first.orEmpty()
    val originalSizeBytes = metadata?.second?.takeIf { it >= 0L }

    val destination = File(context.cacheDir, "feresa-model-${objectId.value}.stl")
    val imported = context.contentResolver.openInputStream(uri)?.use { input ->
        ModelFileImporter.convertToBinaryStl(
            input = input,
            originalFileName = displayName,
            destination = destination,
            knownSizeBytes = originalSizeBytes,
        )
    } ?: error("Не удалось прочитать выбранный документ")
    return PlateModelSource(
        file = imported.binaryStlFile,
        displayName = imported.displayName,
        localBounds = PlateBounds(
            minimumX = imported.bounds.minimumX,
            minimumY = imported.bounds.minimumY,
            minimumZ = imported.bounds.minimumZ,
            maximumX = imported.bounds.maximumX,
            maximumY = imported.bounds.maximumY,
            maximumZ = imported.bounds.maximumZ,
        ),
        sourceFormat = imported.sourceFormat.name.replace("THREE_MF", "3MF"),
        triangleCount = imported.triangleCount,
        originalSizeBytes = imported.originalSizeBytes,
    )
}

private fun PlateObjectTransform.toViewerTransform(): ModelTransform = ModelTransform(
    positionX = positionXmm,
    positionY = positionYmm,
    positionZ = positionZmm,
    rotationDegrees = rotationZDegrees,
    rotationXDegrees = rotationXDegrees,
    rotationYDegrees = rotationYDegrees,
    scale = scale,
    scaleX = effectiveScaleX,
    scaleY = effectiveScaleY,
    scaleZ = effectiveScaleZ,
)

private fun PlateObject.toStlPlatePlacement(): StlPlatePlacement = StlPlatePlacement(
    file = source.file,
    positionXmm = transform.positionXmm,
    positionYmm = transform.positionYmm,
    positionZmm = transform.positionZmm,
    rotationDegrees = transform.rotationZDegrees,
    rotationXDegrees = transform.rotationXDegrees,
    rotationYDegrees = transform.rotationYDegrees,
    scale = transform.scale,
    scaleX = transform.scaleX,
    scaleY = transform.scaleY,
    scaleZ = transform.scaleZ,
)

/** File-backed exact correction; callers must run this away from the Compose main thread. */
private fun PlateWorkspace.moveObjectToExactBed(objectId: PlateObjectId): PlateWorkspace {
    val model = objectOrNull(objectId)
        ?: throw IllegalArgumentException("Неизвестная модель '$objectId'")
    val exactBounds = StlPlateComposer.exactPlacedBounds(model.toStlPlatePlacement())
    return updateTransform(objectId) { transform ->
        transform.copy(positionZmm = transform.positionZmm - exactBounds.minimumZ)
    }
}

private fun findInitialPlateTransform(
    workspace: PlateWorkspace,
    source: PlateModelSource,
    buildVolume: RectangularBuildVolume,
): PlateObjectTransform {
    val spacingMm = 6.0
    val stepX = (source.localBounds.width + spacingMm).coerceAtLeast(20.0)
    val stepY = (source.localBounds.depth + spacingMm).coerceAtLeast(20.0)
    val candidates = (-4..4).flatMap { row ->
        (-4..4).map { column -> column to row }
    }.sortedWith(
        compareBy<Pair<Int, Int>> { kotlin.math.abs(it.first) + kotlin.math.abs(it.second) }
            .thenBy { kotlin.math.abs(it.second) }
            .thenBy { kotlin.math.abs(it.first) },
    )
    val volumeBounds = PlateBounds(
        minimumX = 0.0,
        minimumY = 0.0,
        minimumZ = 0.0,
        maximumX = buildVolume.widthMm,
        maximumY = buildVolume.depthMm,
        maximumZ = buildVolume.heightMm,
    )
    return candidates.asSequence()
        .map { (column, row) ->
            PlateObjectTransform(
                positionXmm = buildVolume.centerX + column * stepX,
                positionYmm = buildVolume.centerY + row * stepY,
            )
        }
        .firstOrNull { transform ->
            val bounds = source.localBounds.transformedBy(transform)
            bounds.minimumX >= volumeBounds.minimumX &&
                bounds.maximumX <= volumeBounds.maximumX &&
                bounds.minimumY >= volumeBounds.minimumY &&
                bounds.maximumY <= volumeBounds.maximumY &&
                bounds.maximumZ <= volumeBounds.maximumZ &&
                workspace.objects.none { existing -> bounds.overlaps(existing.plateBounds, spacingMm) }
        }
        ?: PlateObjectTransform(positionXmm = buildVolume.centerX, positionYmm = buildVolume.centerY)
}

private fun PlateBounds.overlaps(other: PlateBounds, spacingMm: Double): Boolean =
    maximumX + spacingMm > other.minimumX &&
        minimumX - spacingMm < other.maximumX &&
        maximumY + spacingMm > other.minimumY &&
        minimumY - spacingMm < other.maximumY

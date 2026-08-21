// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.g24.feresaslicer.slicer.OrcaDynamicPrintConfigBuilder
import tech.g24.feresaslicer.slicer.OrcaMachineFilamentScalars
import tech.g24.feresaslicer.slicer.OrcaNativeEngine
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
fun FeresaSlicerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var plateWorkspace by remember { mutableStateOf(PlateWorkspace.empty()) }
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
    var bedWidth by remember { mutableStateOf(220.0) }
    var bedDepth by remember { mutableStateOf(220.0) }
    var printableHeight by remember { mutableStateOf(250.0) }
    var printerFirmware by remember { mutableStateOf("marlin") }
    val applicationContext = context.applicationContext
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
    var modelWorkspaceSection by remember { mutableStateOf(ModelWorkspaceSection.FILE) }
    var renameObjectId by remember { mutableStateOf<PlateObjectId?>(null) }
    var renameObjectValue by remember { mutableStateOf("") }
    var linkScaleAxes by remember { mutableStateOf(true) }
    var modelActionMessage by remember { mutableStateOf<String?>(null) }
    var cameraResetRequest by remember { mutableStateOf(0) }
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
            isWorking = true
            runCatching {
                withContext(Dispatchers.Default) { updated.moveObjectToExactBed(objectId) }
            }.onSuccess { exactWorkspace ->
                commitPlateWorkspace(exactWorkspace, message)
            }.onFailure { error ->
                errorMessage = error.message ?: "Не удалось разместить модель на столе"
            }
            isWorking = false
        }
    }

    fun applyViewerSelection(selection: ViewerObjectSelection) {
        val selectedId = selection.objectId?.let(::PlateObjectId)
        if (selectedId == null || plateWorkspace.objectOrNull(selectedId) != null) {
            plateWorkspace = plateWorkspace.select(selectedId)
            viewerSceneState = null
        }
    }

    var printSettings by remember { mutableStateOf(PrintSettingsState()) }
    var dirtyProcessSettingKeys by remember { mutableStateOf(emptySet<String>()) }
    var printDetailLevel by remember { mutableStateOf(PrintDetailLevel.ADVANCED) }
    var printSettingsCategory by remember { mutableStateOf(PrintSettingsCategory.QUALITY) }
    val layerHeight = printSettings.layerHeight
    val printSpeed = printSettings.printSpeed
    var nozzleDiameter by remember { mutableStateOf("0.40") }
    var filamentDiameter by remember { mutableStateOf("1.75") }
    var nozzleTemperature by remember { mutableStateOf("210") }
    var bedTemperature by remember { mutableStateOf("60") }
    var printerProfileName by remember { mutableStateOf("Generic 220") }
    var filamentProfileName by remember { mutableStateOf("Generic PLA") }
    var processProfileName by remember { mutableStateOf("Standard quality") }
    var activeCloudPrinterProfileId by remember { mutableStateOf<String?>(null) }
    var activeSystemPrinterProfile by remember { mutableStateOf<OrcaCloudProfile?>(null) }
    var activeSystemProcessProfile by remember { mutableStateOf<OrcaCloudProfile?>(null) }
    val manualPrinterConnectionStore = remember(applicationContext) {
        ManualPrinterConnectionStore(applicationContext)
    }
    var savedManualPrinterConnection by remember(applicationContext) {
        mutableStateOf(manualPrinterConnectionStore.read())
    }

    val activePrinterProfile = activeCloudPrinterProfileId?.let { selectedId ->
        cloudProfileState.profiles.firstOrNull {
            it.type == OrcaProfileType.PRINTER && it.id == selectedId
        }
    } ?: activeSystemPrinterProfile?.takeIf { it.name == printerProfileName }
        ?: cloudProfileState.profiles.firstOrNull {
            it.type == OrcaProfileType.PRINTER && it.name == printerProfileName
        }
    val activeFilamentProfile = cloudProfileState.profiles.firstOrNull {
        it.type == OrcaProfileType.FILAMENT && it.name == filamentProfileName
    }
    val activeProcessProfile = cloudProfileState.profiles.firstOrNull {
        it.type == OrcaProfileType.PROCESS && it.name == processProfileName
    } ?: activeSystemProcessProfile?.takeIf { it.name == processProfileName }
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
        if (activeCloudPrinterProfileId != null &&
            cloudProfileState.profiles.none { it.id == activeCloudPrinterProfileId }
        ) {
            activeCloudPrinterProfileId = null
        }
        val presetCatalog = systemPresetCatalog ?: return@LaunchedEffect
        if (printerProfileName == "Generic 220" && activeCloudPrinterProfileId == null) {
            connectedProfile?.let { profile ->
                activeCloudPrinterProfileId = profile.id
                runCatching {
                    resolveProfileSettingsForUi(
                        catalog = presetCatalog,
                        profile = profile,
                        availableProfiles = cloudProfileState.profiles,
                    )
                }.onSuccess { resolved ->
                    printerProfileName = profile.name
                    activeSystemPrinterProfile = null
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

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isWorking = true
                errorMessage = null
                runCatching {
                    withContext(Dispatchers.IO) {
                        uris.map { uri ->
                            val id = PlateObjectId.newId()
                            id to copyModelToCache(context, uri, id)
                        }
                    }
                }.onSuccess { importedModels ->
                    var updatedWorkspace = plateWorkspace
                    val buildVolume = RectangularBuildVolume(bedWidth, bedDepth, printableHeight)
                    importedModels.forEach { (id, source) ->
                        val transform = findInitialPlateTransform(updatedWorkspace, source, buildVolume)
                        updatedWorkspace = updatedWorkspace.add(source, id, transform, select = true)
                    }
                    plateWorkspace = updatedWorkspace
                    modelWorkspaceSection = ModelWorkspaceSection.VIEW
                    invalidatePlateSlice()
                    viewerMessage = null
                    modelActionMessage = "Добавлено моделей: ${importedModels.size}"
                }.onFailure { error ->
                    errorMessage = error.message ?: "Не удалось импортировать выбранный файл"
                }
                isWorking = false
            }
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
            isWorking = true
            errorMessage = null
            report = null
            generatedGcode = null
            generatedGcodeGeneration = null
            viewerMode = ViewerMode.MODEL
            val outputFile = File(context.cacheDir, "feresa-slicer-output-$runToken.gcode")
            val configFile = File(context.cacheDir, "feresa-orca-$runToken.ini")
            val plateFile = File(context.cacheDir, "feresa-slicer-plate-$runToken.stl")
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
                    val dynamicConfig = OrcaDynamicPrintConfigBuilder.build(
                        profiles = hydratedProfiles,
                        machineFilament = OrcaMachineFilamentScalars(
                            bedWidthMm = bedWidth,
                            bedDepthMm = bedDepth,
                            printableHeightMm = printableHeight,
                            nozzleDiameterMm = nozzleDiameter,
                            filamentDiameterMm = filamentDiameter,
                            nozzleTemperatureC = nozzleTemperature,
                            bedTemperatureC = bedTemperature,
                            gcodeFlavor = printerFirmware,
                        ),
                        liveProcessSettings = liveProcessPayload,
                    )
                    dynamicConfig.writeTo(configFile)
                    // Always compose the complete plate, including a one-object plate. This makes
                    // the geometry handed to Orca identical to the XYZ/non-uniform transform shown
                    // by the viewer instead of silently reducing it to legacy Z rotation + scale.
                    val composed = StlPlateComposer.compose(
                        placements = plateSnapshot.objects.map { it.toStlPlatePlacement() },
                        output = plateFile,
                    )
                    val sliceSource = composed.file
                    val sliceSettings = settings.copy(
                        modelPositionXmm = composed.bounds.centerX,
                        modelPositionYmm = composed.bounds.centerY,
                        modelRotationDegrees = 0.0,
                        modelScale = 1.0,
                        // The plate composer already applied the complete XYZ transform. Keeping
                        // its Z coordinates is essential for intentionally raised objects and
                        // support generation; Orca must not silently drop the plate back to Z=0.
                        ensureModelOnBed = false,
                    )
                    OrcaNativeEngine().sliceModel(
                        inputPath = sliceSource.path,
                        configPath = configFile.path,
                        outputPath = outputFile.path,
                        settings = sliceSettings,
                    )
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
                outputFile.delete()
                if (sliceGeneration != generationSnapshot) return@onFailure
                generatedGcode = null
                generatedGcodeGeneration = null
                errorMessage = error.message ?: "Ошибка слайсера"
            }
            configFile.delete()
            plateFile.delete()
            isWorking = false
        }
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
                    if (destination == AppDestination.MODEL && hasPlateModels) {
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
                        viewerMessage = viewerMessage,
                        minimumLayer = toolpathMinimumLayer,
                        maximumLayer = toolpathMaximumLayer,
                        colorMode = toolpathColorMode,
                        showExtrusion = showExtrusionMoves,
                        showTravel = showTravelMoves,
                        progress = toolpathProgress,
                        layerHeightMm = layerHeight.toDoubleOrNull() ?: 0.2,
                        cameraResetRequest = cameraResetRequest,
                        modelObjects = viewerModelObjects,
                        selectedObjectId = plateWorkspace.selectedObjectId?.value,
                        onObjectSelected = ::applyViewerSelection,
                        onSceneState = { viewerSceneState = it },
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
                    )
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
                } else if (modelWorkspaceSection == ModelWorkspaceSection.FILE) {
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
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (plateWorkspace.objects.isEmpty()) "Загрузить модель" else "Добавить модель")
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
                                onImportModel = { filePicker.launch(arrayOf("*/*")) },
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
                        activeCloudPrinterProfileId = activeCloudPrinterProfileId,
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
                                )
                            }.onSuccess { resolved ->
                                when (profile.type) {
                                    OrcaProfileType.PRINTER -> {
                                        printerProfileName = profile.name
                                        activeCloudPrinterProfileId = profile.id
                                        profile.printerConnection()?.let {
                                            savedManualPrinterConnection?.let { saved ->
                                                manualPrinterConnectionStore.write(saved.connection, isActive = false)
                                                savedManualPrinterConnection = saved.copy(isActive = false)
                                            }
                                        }
                                        activeSystemPrinterProfile = null
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
                                        activeSystemProcessProfile = null
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
                                printerProfileName = profile.name
                                activeCloudPrinterProfileId = null
                                activeSystemPrinterProfile = bundledPrinter
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

                                activeSystemProcessProfile = process.first
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
private fun ModelWorkspaceNavigation(
    selected: ModelWorkspaceSection,
    onSelect: (ModelWorkspaceSection) -> Unit,
) {
    val sections = listOf(
        ModelWorkspaceSection.VIEW,
        ModelWorkspaceSection.POSITION,
        ModelWorkspaceSection.SLICE,
    )
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
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
                        contentDescription = localizeUiText(section.label, currentUiLanguage()),
                        tint = if (isSelected) Accent else Muted,
                        modifier = Modifier.size(23.dp),
                    )
                    Text(
                        text = section.label,
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
    activeCloudPrinterProfileId: String?,
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
    val activeName = when (selected) {
        ProfileSection.PRINTER -> printerProfileName
        ProfileSection.FILAMENT -> filamentProfileName
        ProfileSection.PROCESS -> processProfileName
    }
    if (selected != ProfileSection.PROCESS) {
        OrcaCloudProfilesCard(
            state = cloudState,
            requestedType = requestedType,
            activeName = activeName,
            activeProfileId = activeCloudPrinterProfileId.takeIf {
                requestedType == OrcaProfileType.PRINTER
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
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val matches = catalog.printers.filter { profile ->
        normalizedQuery.isBlank() || listOf(
            profile.name,
            profile.model,
            profile.vendor,
            profile.family,
        ).any { it.lowercase(Locale.getDefault()).contains(normalizedQuery) }
    }
    val visibleProfiles = matches
        .sortedWith(
            compareByDescending<OrcaSystemPrinterProfile> { it.name == activeName }
                .thenBy { it.vendor.lowercase(Locale.getDefault()) }
                .thenBy { it.model.lowercase(Locale.getDefault()) }
                .thenBy { it.nozzleDiameter }
        )
        .take(12)

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

        if (matches.isEmpty()) {
            Text("Профили не найдены", color = Muted)
        } else {
            Text(
                if (matches.size > visibleProfiles.size) {
                    "Найдено: ${matches.size} · показаны первые ${visibleProfiles.size}"
                } else {
                    "Найдено: ${matches.size}"
                },
                color = Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            visibleProfiles.forEachIndexed { index, profile ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "${profile.vendor} · ${profile.bedWidth.toInt()} × ${profile.bedDepth.toInt()} × " +
                                "${profile.printableHeight.toInt()} мм · сопло ${formatProfileNumber(profile.nozzleDiameter)} мм",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                        if (profile.name == activeName) {
                            Text("Активен", color = Accent, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { onApply(profile) }) {
                        Text(if (profile.name == activeName) "Выбран" else "Выбрать")
                    }
                }
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
    activeName: String,
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
                            val isSelectedProfile = profile.name == activeName
                            val isActiveConnection = requestedType == OrcaProfileType.PRINTER &&
                                profile.id == activeProfileId
                            if (isSelectedProfile) {
                                Text("Активен", color = Accent, fontSize = 12.sp)
                            } else if (isActiveConnection) {
                                Text("Используется для подключения", color = Accent, fontSize = 12.sp)
                            }
                        }
                        TextButton(onClick = { onApply(profile) }) {
                            Text(
                                when {
                                    profile.name == activeName -> "Выбран"
                                    requestedType == OrcaProfileType.PRINTER && profile.id == activeProfileId -> "Подключён"
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
private fun TransformSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    decimals: Int = 1,
    onValueChange: (Double) -> Unit,
) {
    val formattedValue = String.format(Locale.US, "%.${decimals}f", value)
    Text("$label: $formattedValue $suffix", fontWeight = FontWeight.Medium)
    Slider(
        value = value.toFloat().coerceIn(range.start, range.endInclusive),
        onValueChange = { onValueChange(it.toDouble()) },
        valueRange = range,
        modifier = Modifier.fillMaxWidth(),
    )
}

private data class GcodeMotion(
    val lineNumber: Int,
    val source: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val speedMmSeconds: Double,
    val extrusion: Boolean,
    val layer: Int,
)

private data class GcodePreviewData(
    val lineCount: Int,
    val motions: List<GcodeMotion>,
)

private fun GcodePreviewData.layerZ(layer: Int): Double? =
    motions.firstOrNull { motion -> motion.layer == layer && motion.extrusion && motion.z > 0.0 }?.z
        ?: motions.firstOrNull { motion -> motion.layer == layer && motion.z > 0.0 }?.z

private val GcodePreviewWord = Regex(
    "([XYZEF])\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)",
    RegexOption.IGNORE_CASE,
)

private fun parseGcodePreview(file: File): GcodePreviewData {
    var x = 0.0
    var y = 0.0
    var z = 0.0
    var e = 0.0
    var feedRate = 0.0
    var layer = 0
    var layerChangeSeen = false
    var absoluteAxes = true
    var absoluteExtrusion = true
    var lineCount = 0
    val motions = ArrayList<GcodeMotion>()

    file.useLines { lines ->
        lines.forEach { sourceLine ->
            lineCount += 1
            val explicitLayer = Regex(";\\s*LAYER\\s*:\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(sourceLine)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            if (explicitLayer != null) {
                layer = explicitLayer
            } else if (Regex(";\\s*(?:LAYER_CHANGE|CHANGE_LAYER)\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn(sourceLine)
            ) {
                if (layerChangeSeen) layer += 1
                layerChangeSeen = true
            }

            val command = sourceLine.substringBefore(';').trim().uppercase(Locale.US)
            when {
                command.startsWith("G90") -> absoluteAxes = true
                command.startsWith("G91") -> absoluteAxes = false
                command.startsWith("M82") -> absoluteExtrusion = true
                command.startsWith("M83") -> absoluteExtrusion = false
                command.startsWith("G92") -> {
                    val words = GcodePreviewWord.findAll(command).associate {
                        it.groupValues[1].uppercase(Locale.US) to it.groupValues[2].toDouble()
                    }
                    words["X"]?.let { x = it }
                    words["Y"]?.let { y = it }
                    words["Z"]?.let { z = it }
                    words["E"]?.let { e = it }
                }
                command.matches(Regex("G0?[0-3](?:\\s.*)?")) -> {
                    val words = GcodePreviewWord.findAll(command).associate {
                        it.groupValues[1].uppercase(Locale.US) to it.groupValues[2].toDouble()
                    }
                    val nextX = words["X"]?.let { if (absoluteAxes) it else x + it } ?: x
                    val nextY = words["Y"]?.let { if (absoluteAxes) it else y + it } ?: y
                    val nextZ = words["Z"]?.let { if (absoluteAxes) it else z + it } ?: z
                    val nextE = words["E"]?.let { if (absoluteExtrusion) it else e + it } ?: e
                    words["F"]?.let { feedRate = it }
                    if (nextX != x || nextY != y) {
                        motions += GcodeMotion(
                            lineNumber = lineCount,
                            source = sourceLine.trim().take(180),
                            x = nextX,
                            y = nextY,
                            z = nextZ,
                            speedMmSeconds = feedRate / 60.0,
                            extrusion = !command.startsWith("G0 ") &&
                                !command.startsWith("G00 ") &&
                                nextE > e + 0.0000001,
                            layer = layer,
                        )
                    }
                    x = nextX
                    y = nextY
                    z = nextZ
                    e = nextE
                }
            }
        }
    }
    return GcodePreviewData(lineCount = lineCount, motions = motions)
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
    viewerMessage: String?,
    minimumLayer: Int,
    maximumLayer: Int,
    colorMode: ToolpathColorMode,
    showExtrusion: Boolean,
    showTravel: Boolean,
    progress: Float,
    layerHeightMm: Double,
    cameraResetRequest: Int,
    modelObjects: List<ViewerModelObject>,
    selectedObjectId: String?,
    onObjectSelected: (ViewerObjectSelection) -> Unit,
    onSceneState: (ViewerSceneState) -> Unit,
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
) {
    val hasModels = modelObjects.isNotEmpty() || modelFile != null
    val successfulReport = report?.takeIf { it.success }
    val previewData = remember(gcodeFile, gcodeFile?.lastModified()) {
        gcodeFile?.takeIf { it.isFile }?.let { file -> runCatching { parseGcodePreview(file) }.getOrNull() }
    }

    if (gcodeFile == null || successfulReport == null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ModelViewer(
                    modelFile = modelFile,
                    gcodeFile = null,
                    transform = transform,
                    bedWidth = bedWidth,
                    bedDepth = bedDepth,
                    mode = ViewerMode.MODEL,
                    darkTheme = darkTheme,
                    cameraResetRequest = cameraResetRequest,
                    modelObjects = modelObjects,
                    selectedObjectId = selectedObjectId,
                    onObjectSelected = onObjectSelected,
                    onSceneState = onSceneState,
                    onError = onViewerError,
                    viewerHeight = 430.dp,
                )
                Text(
                    if (!hasModels) {
                        "Выберите STL во вкладке «Файл», затем вернитесь сюда для нарезки."
                    } else {
                        "Модели готовы. После нарезки здесь появится послойная траектория G-code."
                    },
                    color = Muted,
                    fontSize = 13.sp,
                )
                Button(
                    onClick = onSlice,
                    enabled = hasModels && !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isWorking) "Нарезка…" else "НАРЕЗАТЬ МОДЕЛЬ")
                }
            }
        }
        viewerMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        return
    }

    var colorMenuExpanded by remember { mutableStateOf(false) }
    var gcodeExpanded by remember(gcodeFile) { mutableStateOf(false) }
    var toolpathSelection by remember(gcodeFile) { mutableStateOf<ViewerToolpathSelection?>(null) }
    val lastLayer = (successfulReport.layers.toInt() - 1).coerceAtLeast(0)
    val sliderEnd = lastLayer.coerceAtLeast(1).toFloat()
    val visibleMotions = previewData?.motions?.filter { motion ->
        motion.layer in minimumLayer..maximumLayer &&
            ((motion.extrusion && showExtrusion) || (!motion.extrusion && showTravel))
    }.orEmpty()
    val selectedMotionIndex = if (visibleMotions.isEmpty()) {
        0
    } else {
        (visibleMotions.lastIndex * progress.coerceIn(0f, 1f)).roundToInt().coerceIn(0, visibleMotions.lastIndex)
    }
    val displayedSegmentCount = toolpathSelection?.displayedSegmentCount
        ?: if (visibleMotions.isEmpty()) 0 else selectedMotionIndex + 1
    val eligibleSegmentCount = toolpathSelection?.eligibleSegmentCount ?: visibleMotions.size
    val maximumLayerZ = previewData?.layerZ(maximumLayer)
        ?: (maximumLayer + 1) * layerHeightMm
    val minimumLayerZ = previewData?.layerZ(minimumLayer)
        ?: (minimumLayer + 1) * layerHeightMm

    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { colorMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(colorMode.label) }
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
                OutlinedButton(onClick = onResetCamera) { Text("Вид") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Печать", fontSize = 12.sp)
                    Switch(checked = showExtrusion, onCheckedChange = onShowExtrusionChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Перемещения", fontSize = 12.sp)
                    Switch(checked = showTravel, onCheckedChange = onShowTravelChange)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(410.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    cameraResetRequest = cameraResetRequest,
                    modelObjects = modelObjects,
                    selectedObjectId = selectedObjectId,
                    onObjectSelected = onObjectSelected,
                    onSceneState = onSceneState,
                    onToolpathSelection = { toolpathSelection = it },
                    onError = onViewerError,
                    viewerHeight = 410.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier
                        .width(62.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${maximumLayer + 1}\n${String.format(Locale.US, "%.2f", maximumLayerZ)}",
                        textAlign = TextAlign.Center,
                        color = Accent,
                        fontSize = 11.sp,
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
                                .requiredWidth(315.dp)
                                .graphicsLayer { rotationZ = 270f },
                        )
                    }
                    Text(
                        "${minimumLayer + 1}\n${String.format(Locale.US, "%.2f", minimumLayerZ)}",
                        textAlign = TextAlign.Center,
                        color = Accent,
                        fontSize = 11.sp,
                    )
                }
            }

            Text(
                "Ход траектории $displayedSegmentCount / $eligibleSegmentCount",
                color = Muted,
                fontSize = 12.sp,
            )
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = onProgressChange,
                valueRange = 0f..1f,
                enabled = visibleMotions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )

            toolpathSelection?.let { selection ->
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
                    fontSize = 13.sp,
                )
            }

            OutlinedButton(
                onClick = { gcodeExpanded = !gcodeExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (gcodeExpanded) "Скрыть команды G-code" else "Показать команды G-code")
            }
            if (gcodeExpanded) {
                val codeStart = (selectedMotionIndex - 5).coerceAtLeast(0)
                val codeEnd = (selectedMotionIndex + 6).coerceAtMost(visibleMotions.size)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF202220), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                ) {
                    if (visibleMotions.isEmpty()) {
                        Text("Нет видимых команд", color = Color(0xFFCDD5D0), fontFamily = FontFamily.Monospace)
                    } else {
                        visibleMotions.subList(codeStart, codeEnd).forEachIndexed { index, motion ->
                            val active = codeStart + index == selectedMotionIndex
                            Text(
                                "${motion.lineNumber.toString().padStart(6)}  ${motion.source}",
                                color = if (active) Color(0xFFFFA15A) else Color(0xFFD6DDD8),
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
                "Прочитано ${previewData?.lineCount ?: 0} строк G-code",
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
    viewerMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
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

private fun resolveProfileSettingsForUi(
    catalog: OrcaSystemPresetCatalog,
    profile: OrcaCloudProfile,
    availableProfiles: List<OrcaCloudProfile>,
): Map<String, String> {
    val selected = when (profile.type) {
        OrcaProfileType.PRINTER -> OrcaSelectedProfiles(
            printer = profile,
            availableCloudProfiles = availableProfiles,
        )
        OrcaProfileType.FILAMENT -> OrcaSelectedProfiles(
            filament = profile,
            availableCloudProfiles = availableProfiles,
        )
        OrcaProfileType.PROCESS -> OrcaSelectedProfiles(
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

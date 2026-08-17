// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.g24.feresaslicer.auth.OrcaCloudProfile
import kotlin.math.roundToInt

enum class PrintDetailLevel(val label: String) {
    BASIC("Основные"),
    ADVANCED("Расширенные"),
    EXPERT("Экспертные"),
}

enum class PrintSettingsCategory(val label: String) {
    QUALITY("Вид"),
    STRENGTH("Прочность"),
    SUPPORT("Поддержки"),
    MULTIMATERIAL("Многоцвет"),
    OTHERS("Прочее"),
}

data class PrintSettingsState(
    val layerHeight: String = "0.20",
    val initialLayerHeight: String = "0.20",
    val lineWidth: String = "0.42",
    val initialLayerLineWidth: String = "0.50",
    val outerWallLineWidth: String = "0.42",
    val seamPosition: String = "nearest",
    val seamGap: String = "10",
    val staggeredInnerSeams: Boolean = false,
    val resolution: String = "0.012",
    val arcFitting: Boolean = false,
    val preciseOuterWall: Boolean = false,
    val xyHoleCompensation: String = "0",
    val elephantFootCompensation: String = "0.15",
    val wallGenerator: String = "arachne",
    val wallSequence: String = "inner wall/outer wall",
    val infillFirst: Boolean = false,
    val onlyOneWallTop: Boolean = true,
    val detectOverhangWall: Boolean = true,
    val bridgeFlow: String = "1.0",
    val wallLoops: String = "2",
    val alternateExtraWall: Boolean = false,
    val detectThinWall: Boolean = true,
    val topShellLayers: String = "4",
    val bottomShellLayers: String = "3",
    val topSurfacePattern: String = "monotonicline",
    val bottomSurfacePattern: String = "monotonic",
    val infillDensity: String = "20",
    val infillPattern: String = "gyroid",
    val infillDirection: String = "45",
    val infillWallOverlap: String = "15",
    val enableSupport: Boolean = false,
    val supportType: String = "normal(auto)",
    val supportThresholdAngle: String = "30",
    val supportOnBuildPlateOnly: Boolean = false,
    val raftLayers: String = "0",
    val supportTopDistance: String = "0.20",
    val supportBottomDistance: String = "0.20",
    val supportInterfaceTopLayers: String = "3",
    val supportInterfaceBottomLayers: String = "0",
    val supportInterfaceSpacing: String = "0.50",
    val treeTipDiameter: String = "0.8",
    val treeBranchDistance: String = "5",
    val treeBranchAngle: String = "45",
    val enablePrimeTower: Boolean = false,
    val primeTowerWidth: String = "35",
    val primeVolume: String = "45",
    val primeTowerBrimWidth: String = "3",
    val flushIntoInfill: Boolean = false,
    val flushIntoSupport: Boolean = false,
    val oozePrevention: Boolean = false,
    val standbyTemperatureDelta: String = "-5",
    val outerWallFilament: String = "1",
    val infillFilament: String = "1",
    val skirtLoops: String = "1",
    val skirtDistance: String = "3",
    val minSkirtLength: String = "4",
    val brimType: String = "auto_brim",
    val brimWidth: String = "5",
    val brimObjectGap: String = "0.1",
    val printSpeed: String = "45",
    val outerWallSpeed: String = "45",
    val innerWallSpeed: String = "60",
    val infillSpeed: String = "100",
    val travelSpeed: String = "150",
    val acceleration: String = "1000",
    val slicingMode: String = "regular",
    val printSequence: String = "by layer",
    val spiralMode: Boolean = false,
    val fuzzySkin: Boolean = false,
    val gcodeComments: Boolean = true,
    val labelObjects: Boolean = true,
    val excludeObjects: Boolean = true,
    val filenameFormat: String = "{input_filename_base}_{layer_height}mm.gcode",
)

fun PrintSettingsState.applyOrcaProfile(profile: OrcaCloudProfile): PrintSettingsState {
    fun value(key: String, current: String): String = profile.setting(key) ?: current
    fun percent(key: String, current: String): String = value(key, current).removeSuffix("%")
    fun flag(key: String, current: Boolean): Boolean = profile.setting(key)?.let {
        it == "1" || it.equals("true", ignoreCase = true)
    } ?: current
    return copy(
        layerHeight = value("layer_height", layerHeight),
        initialLayerHeight = value("initial_layer_print_height", initialLayerHeight),
        lineWidth = value("line_width", lineWidth),
        initialLayerLineWidth = value("initial_layer_line_width", initialLayerLineWidth),
        outerWallLineWidth = value("outer_wall_line_width", outerWallLineWidth),
        seamPosition = value("seam_position", seamPosition),
        seamGap = percent("seam_gap", seamGap),
        staggeredInnerSeams = flag("staggered_inner_seams", staggeredInnerSeams),
        resolution = value("resolution", resolution),
        arcFitting = flag("enable_arc_fitting", arcFitting),
        preciseOuterWall = flag("precise_outer_wall", preciseOuterWall),
        xyHoleCompensation = value("xy_hole_compensation", xyHoleCompensation),
        elephantFootCompensation = value("elefant_foot_compensation", elephantFootCompensation),
        wallGenerator = value("wall_generator", wallGenerator),
        wallSequence = value("wall_sequence", wallSequence),
        infillFirst = flag("is_infill_first", infillFirst),
        onlyOneWallTop = flag("only_one_wall_top", onlyOneWallTop),
        detectOverhangWall = flag("detect_overhang_wall", detectOverhangWall),
        bridgeFlow = value("bridge_flow", bridgeFlow),
        wallLoops = value("wall_loops", wallLoops),
        alternateExtraWall = flag("alternate_extra_wall", alternateExtraWall),
        detectThinWall = flag("detect_thin_wall", detectThinWall),
        topShellLayers = value("top_shell_layers", topShellLayers),
        bottomShellLayers = value("bottom_shell_layers", bottomShellLayers),
        topSurfacePattern = value("top_surface_pattern", topSurfacePattern),
        bottomSurfacePattern = value("bottom_surface_pattern", bottomSurfacePattern),
        infillDensity = percent("sparse_infill_density", infillDensity),
        infillPattern = value("sparse_infill_pattern", infillPattern),
        infillDirection = value("infill_direction", infillDirection),
        infillWallOverlap = percent("infill_wall_overlap", infillWallOverlap),
        enableSupport = flag("enable_support", enableSupport),
        supportType = value("support_type", supportType),
        supportThresholdAngle = value("support_threshold_angle", supportThresholdAngle),
        supportOnBuildPlateOnly = flag("support_on_build_plate_only", supportOnBuildPlateOnly),
        raftLayers = value("raft_layers", raftLayers),
        supportTopDistance = value("support_top_z_distance", supportTopDistance),
        supportBottomDistance = value("support_bottom_z_distance", supportBottomDistance),
        supportInterfaceTopLayers = value("support_interface_top_layers", supportInterfaceTopLayers),
        supportInterfaceBottomLayers = value("support_interface_bottom_layers", supportInterfaceBottomLayers),
        supportInterfaceSpacing = value("support_interface_spacing", supportInterfaceSpacing),
        treeTipDiameter = value("tree_support_tip_diameter", treeTipDiameter),
        treeBranchDistance = value("tree_support_branch_distance", treeBranchDistance),
        treeBranchAngle = value("tree_support_branch_angle", treeBranchAngle),
        enablePrimeTower = flag("enable_prime_tower", enablePrimeTower),
        primeTowerWidth = value("prime_tower_width", primeTowerWidth),
        primeVolume = value("prime_volume", primeVolume),
        primeTowerBrimWidth = value("prime_tower_brim_width", primeTowerBrimWidth),
        flushIntoInfill = flag("flush_into_infill", flushIntoInfill),
        flushIntoSupport = flag("flush_into_support", flushIntoSupport),
        oozePrevention = flag("ooze_prevention", oozePrevention),
        standbyTemperatureDelta = value("standby_temperature_delta", standbyTemperatureDelta),
        outerWallFilament = value("outer_wall_filament_id", outerWallFilament),
        infillFilament = value("sparse_infill_filament_id", infillFilament),
        skirtLoops = value("skirt_loops", skirtLoops),
        skirtDistance = value("skirt_distance", skirtDistance),
        minSkirtLength = value("min_skirt_length", minSkirtLength),
        brimType = value("brim_type", brimType),
        brimWidth = value("brim_width", brimWidth),
        brimObjectGap = value("brim_object_gap", brimObjectGap),
        printSpeed = value("outer_wall_speed", printSpeed),
        outerWallSpeed = value("outer_wall_speed", outerWallSpeed),
        innerWallSpeed = value("inner_wall_speed", innerWallSpeed),
        infillSpeed = value("sparse_infill_speed", infillSpeed),
        travelSpeed = value("travel_speed", travelSpeed),
        acceleration = value("default_acceleration", acceleration),
        slicingMode = value("slicing_mode", slicingMode),
        printSequence = value("print_sequence", printSequence),
        spiralMode = flag("spiral_mode", spiralMode),
        fuzzySkin = value("fuzzy_skin", if (fuzzySkin) "external" else "none") != "none",
        gcodeComments = flag("gcode_comments", gcodeComments),
        labelObjects = flag("gcode_label_objects", labelObjects),
        excludeObjects = flag("exclude_object", excludeObjects),
        filenameFormat = value("filename_format", filenameFormat),
    )
}

@Composable
fun PrintSettingsPanel(
    profileName: String,
    cloudProfiles: List<OrcaCloudProfile>,
    cloudProfilesLoading: Boolean,
    isSignedIn: Boolean,
    settings: PrintSettingsState,
    detailLevel: PrintDetailLevel,
    category: PrintSettingsCategory,
    onDetailLevelChange: (PrintDetailLevel) -> Unit,
    onCategoryChange: (PrintSettingsCategory) -> Unit,
    onSettingsChange: (PrintSettingsState) -> Unit,
    onProfileSelect: (OrcaCloudProfile) -> Unit,
    onRefreshProfiles: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    var profilesExpanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.weight(1.45f)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { profilesExpanded = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Профиль", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            Text(
                                profileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                        Text("⌄", fontSize = 18.sp)
                    }
                }
                DropdownMenu(
                    expanded = profilesExpanded,
                    onDismissRequest = { profilesExpanded = false },
                ) {
                    if (cloudProfiles.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (isSignedIn) "Профили пока не загружены" else "Войти в OrcaCloud") },
                            onClick = {
                                profilesExpanded = false
                                if (isSignedIn) onRefreshProfiles() else onOpenAccount()
                            },
                        )
                    } else {
                        cloudProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(profile.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (profile.name == profileName) {
                                            Text("Выбран", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                        }
                                    }
                                },
                                onClick = {
                                    profilesExpanded = false
                                    onProfileSelect(profile)
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (cloudProfilesLoading) "Обновление…" else "↻ Обновить из OrcaCloud") },
                        enabled = isSignedIn && !cloudProfilesLoading,
                        onClick = {
                            profilesExpanded = false
                            onRefreshProfiles()
                        },
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Настройки · ${detailLevel.label}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
                Slider(
                    value = detailLevel.ordinal.toFloat(),
                    onValueChange = {
                        onDetailLevelChange(PrintDetailLevel.entries[it.roundToInt().coerceIn(0, 2)])
                    },
                    valueRange = 0f..2f,
                    steps = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PrintSettingsCategory.entries.forEach { item ->
            Surface(
                color = if (category == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (category == item) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable { onCategoryChange(item) },
            ) {
                Text(item.label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.Medium)
            }
        }
    }

    key(category) {
        when (category) {
            PrintSettingsCategory.QUALITY -> QualitySettings(settings, detailLevel, onSettingsChange)
            PrintSettingsCategory.STRENGTH -> StrengthSettings(settings, detailLevel, onSettingsChange)
            PrintSettingsCategory.SUPPORT -> SupportSettings(settings, detailLevel, onSettingsChange)
            PrintSettingsCategory.MULTIMATERIAL -> MultimaterialSettings(settings, detailLevel, onSettingsChange)
            PrintSettingsCategory.OTHERS -> OtherSettings(settings, detailLevel, onSettingsChange)
        }
    }
}

@Composable
private fun QualitySettings(s: PrintSettingsState, level: PrintDetailLevel, set: (PrintSettingsState) -> Unit) {
    SettingsAccordion("Высота слоя", "${s.layerHeight} мм", initiallyExpanded = true) {
        NumericSetting("Высота слоя", s.layerHeight, "мм") { set(s.copy(layerHeight = it)) }
        NumericSetting("Высота первого слоя", s.initialLayerHeight, "мм") { set(s.copy(initialLayerHeight = it)) }
        CoreSupportHint()
    }
    if (level >= PrintDetailLevel.ADVANCED) SettingsAccordion("Ширина линии", s.lineWidth) {
        NumericSetting("Ширина линии по умолчанию", s.lineWidth, "мм") { set(s.copy(lineWidth = it)) }
        NumericSetting("Первый слой", s.initialLayerLineWidth, "мм") { set(s.copy(initialLayerLineWidth = it)) }
        if (level == PrintDetailLevel.EXPERT) NumericSetting("Внешняя стенка", s.outerWallLineWidth, "мм") { set(s.copy(outerWallLineWidth = it)) }
    }
    SettingsAccordion("Шов", dropdownLabel(s.seamPosition, seamOptions)) {
        ChoiceSetting("Положение шва", s.seamPosition, seamOptions) { set(s.copy(seamPosition = it)) }
        if (level >= PrintDetailLevel.ADVANCED) BooleanSetting("Смещать внутренние швы", s.staggeredInnerSeams) { set(s.copy(staggeredInnerSeams = it)) }
        if (level == PrintDetailLevel.EXPERT) NumericSetting("Зазор шва", s.seamGap, "%") { set(s.copy(seamGap = it)) }
    }
    if (level >= PrintDetailLevel.ADVANCED) SettingsAccordion("Точность") {
        NumericSetting("Разрешение", s.resolution, "мм") { set(s.copy(resolution = it)) }
        BooleanSetting("Точная внешняя стенка", s.preciseOuterWall) { set(s.copy(preciseOuterWall = it)) }
        BooleanSetting("Аппроксимация дугами", s.arcFitting) { set(s.copy(arcFitting = it)) }
        if (level == PrintDetailLevel.EXPERT) {
            NumericSetting("Компенсация отверстий X/Y", s.xyHoleCompensation, "мм") { set(s.copy(xyHoleCompensation = it)) }
            NumericSetting("Компенсация слоновьей ноги", s.elephantFootCompensation, "мм") { set(s.copy(elephantFootCompensation = it)) }
        }
    }
    if (level == PrintDetailLevel.EXPERT) SettingsAccordion("Генератор стенок") {
        ChoiceSetting("Генератор", s.wallGenerator, wallGeneratorOptions) { set(s.copy(wallGenerator = it)) }
        ChoiceSetting("Порядок стенок", s.wallSequence, wallSequenceOptions) { set(s.copy(wallSequence = it)) }
        BooleanSetting("Сначала заполнение", s.infillFirst) { set(s.copy(infillFirst = it)) }
        BooleanSetting("Одна стенка сверху", s.onlyOneWallTop) { set(s.copy(onlyOneWallTop = it)) }
    }
    if (level >= PrintDetailLevel.ADVANCED) SettingsAccordion("Мосты и нависания") {
        BooleanSetting("Определять нависающие стенки", s.detectOverhangWall) { set(s.copy(detectOverhangWall = it)) }
        if (level == PrintDetailLevel.EXPERT) NumericSetting("Коэффициент потока мостов", s.bridgeFlow, "×") { set(s.copy(bridgeFlow = it)) }
    }
}

@Composable
private fun StrengthSettings(s: PrintSettingsState, level: PrintDetailLevel, set: (PrintSettingsState) -> Unit) {
    SettingsAccordion("Стенки", "${s.wallLoops} контура", initiallyExpanded = true) {
        NumericSetting("Количество контуров", s.wallLoops) { set(s.copy(wallLoops = it)) }
        BooleanSetting("Определять тонкие стенки", s.detectThinWall) { set(s.copy(detectThinWall = it)) }
        if (level >= PrintDetailLevel.ADVANCED) BooleanSetting("Чередовать дополнительную стенку", s.alternateExtraWall) { set(s.copy(alternateExtraWall = it)) }
    }
    SettingsAccordion("Верхние и нижние оболочки") {
        NumericSetting("Верхних слоёв", s.topShellLayers) { set(s.copy(topShellLayers = it)) }
        NumericSetting("Нижних слоёв", s.bottomShellLayers) { set(s.copy(bottomShellLayers = it)) }
        if (level >= PrintDetailLevel.ADVANCED) {
            ChoiceSetting("Рисунок верхней поверхности", s.topSurfacePattern, surfacePatternOptions) { set(s.copy(topSurfacePattern = it)) }
            ChoiceSetting("Рисунок нижней поверхности", s.bottomSurfacePattern, surfacePatternOptions) { set(s.copy(bottomSurfacePattern = it)) }
        }
    }
    SettingsAccordion("Заполнение", s.infillDensity, initiallyExpanded = true) {
        NumericSetting("Плотность заполнения", s.infillDensity, "%") { set(s.copy(infillDensity = it)) }
        ChoiceSetting("Рисунок заполнения", s.infillPattern, infillPatternOptions) { set(s.copy(infillPattern = it)) }
        if (level >= PrintDetailLevel.ADVANCED) NumericSetting("Направление", s.infillDirection, "°") { set(s.copy(infillDirection = it)) }
        if (level == PrintDetailLevel.EXPERT) NumericSetting("Перекрытие со стенкой", s.infillWallOverlap, "%") { set(s.copy(infillWallOverlap = it)) }
    }
}

@Composable
private fun SupportSettings(s: PrintSettingsState, level: PrintDetailLevel, set: (PrintSettingsState) -> Unit) {
    SettingsAccordion("Поддержки", if (s.enableSupport) "Включены" else "Выключены", initiallyExpanded = true) {
        BooleanSetting("Включить поддержки", s.enableSupport) { set(s.copy(enableSupport = it)) }
        ChoiceSetting("Тип", s.supportType, supportTypeOptions) { set(s.copy(supportType = it)) }
        NumericSetting("Порог нависания", s.supportThresholdAngle, "°") { set(s.copy(supportThresholdAngle = it)) }
        BooleanSetting("Только от стола", s.supportOnBuildPlateOnly) { set(s.copy(supportOnBuildPlateOnly = it)) }
    }
    if (level >= PrintDetailLevel.ADVANCED) SettingsAccordion("Плот и интерфейс") {
        NumericSetting("Слоёв плота", s.raftLayers) { set(s.copy(raftLayers = it)) }
        NumericSetting("Верхний Z-зазор", s.supportTopDistance, "мм") { set(s.copy(supportTopDistance = it)) }
        NumericSetting("Нижний Z-зазор", s.supportBottomDistance, "мм") { set(s.copy(supportBottomDistance = it)) }
        NumericSetting("Верхних интерфейсных слоёв", s.supportInterfaceTopLayers) { set(s.copy(supportInterfaceTopLayers = it)) }
        if (level == PrintDetailLevel.EXPERT) {
            NumericSetting("Нижних интерфейсных слоёв", s.supportInterfaceBottomLayers) { set(s.copy(supportInterfaceBottomLayers = it)) }
            NumericSetting("Шаг интерфейса", s.supportInterfaceSpacing, "мм") { set(s.copy(supportInterfaceSpacing = it)) }
        }
    }
    if (level == PrintDetailLevel.EXPERT) SettingsAccordion("Древовидные поддержки") {
        NumericSetting("Диаметр вершины", s.treeTipDiameter, "мм") { set(s.copy(treeTipDiameter = it)) }
        NumericSetting("Расстояние между ветвями", s.treeBranchDistance, "мм") { set(s.copy(treeBranchDistance = it)) }
        NumericSetting("Угол ветвей", s.treeBranchAngle, "°") { set(s.copy(treeBranchAngle = it)) }
    }
}

@Composable
private fun MultimaterialSettings(s: PrintSettingsState, level: PrintDetailLevel, set: (PrintSettingsState) -> Unit) {
    SettingsAccordion("Башня очистки", if (s.enablePrimeTower) "Включена" else "Выключена", initiallyExpanded = true) {
        BooleanSetting("Включить башню очистки", s.enablePrimeTower) { set(s.copy(enablePrimeTower = it)) }
        NumericSetting("Ширина", s.primeTowerWidth, "мм") { set(s.copy(primeTowerWidth = it)) }
        if (level >= PrintDetailLevel.ADVANCED) {
            NumericSetting("Объём очистки", s.primeVolume, "мм³") { set(s.copy(primeVolume = it)) }
            NumericSetting("Ширина каймы", s.primeTowerBrimWidth, "мм") { set(s.copy(primeTowerBrimWidth = it)) }
        }
    }
    SettingsAccordion("Очистка в модель") {
        BooleanSetting("Очищать в заполнение", s.flushIntoInfill) { set(s.copy(flushIntoInfill = it)) }
        BooleanSetting("Очищать в поддержки", s.flushIntoSupport) { set(s.copy(flushIntoSupport = it)) }
    }
    if (level >= PrintDetailLevel.ADVANCED) SettingsAccordion("Филамент элементов") {
        NumericSetting("Внешние стенки", s.outerWallFilament) { set(s.copy(outerWallFilament = it)) }
        NumericSetting("Заполнение", s.infillFilament) { set(s.copy(infillFilament = it)) }
    }
    if (level == PrintDetailLevel.EXPERT) SettingsAccordion("Предотвращение подтекания") {
        BooleanSetting("Включить", s.oozePrevention) { set(s.copy(oozePrevention = it)) }
        NumericSetting("Изменение температуры ожидания", s.standbyTemperatureDelta, "°C") { set(s.copy(standbyTemperatureDelta = it)) }
    }
}

@Composable
private fun OtherSettings(s: PrintSettingsState, level: PrintDetailLevel, set: (PrintSettingsState) -> Unit) {
    SettingsAccordion("Юбка", "${s.skirtLoops} контур", initiallyExpanded = true) {
        NumericSetting("Контуров", s.skirtLoops) { set(s.copy(skirtLoops = it)) }
        NumericSetting("Расстояние от модели", s.skirtDistance, "мм") { set(s.copy(skirtDistance = it)) }
        if (level >= PrintDetailLevel.ADVANCED) NumericSetting("Минимальная длина экструзии", s.minSkirtLength, "мм") { set(s.copy(minSkirtLength = it)) }
    }
    SettingsAccordion("Кайма") {
        ChoiceSetting("Тип каймы", s.brimType, brimTypeOptions) { set(s.copy(brimType = it)) }
        NumericSetting("Ширина", s.brimWidth, "мм") { set(s.copy(brimWidth = it)) }
        if (level >= PrintDetailLevel.ADVANCED) NumericSetting("Зазор до модели", s.brimObjectGap, "мм") { set(s.copy(brimObjectGap = it)) }
    }
    SettingsAccordion("Скорость", "${s.printSpeed} мм/с", initiallyExpanded = true) {
        NumericSetting("Базовая скорость", s.printSpeed, "мм/с") { set(s.copy(printSpeed = it, outerWallSpeed = it)) }
        CoreSupportHint()
        if (level >= PrintDetailLevel.ADVANCED) {
            NumericSetting("Внешняя стенка", s.outerWallSpeed, "мм/с") { set(s.copy(outerWallSpeed = it, printSpeed = it)) }
            NumericSetting("Внутренняя стенка", s.innerWallSpeed, "мм/с") { set(s.copy(innerWallSpeed = it)) }
            NumericSetting("Заполнение", s.infillSpeed, "мм/с") { set(s.copy(infillSpeed = it)) }
            NumericSetting("Перемещения", s.travelSpeed, "мм/с") { set(s.copy(travelSpeed = it)) }
        }
        if (level == PrintDetailLevel.EXPERT) NumericSetting("Ускорение", s.acceleration, "мм/с²") { set(s.copy(acceleration = it)) }
    }
    if (level >= PrintDetailLevel.ADVANCED) SettingsAccordion("Специальный режим") {
        ChoiceSetting("Режим нарезки", s.slicingMode, slicingModeOptions) { set(s.copy(slicingMode = it)) }
        ChoiceSetting("Последовательность печати", s.printSequence, printSequenceOptions) { set(s.copy(printSequence = it)) }
        BooleanSetting("Спиральная ваза", s.spiralMode) { set(s.copy(spiralMode = it)) }
        if (level == PrintDetailLevel.EXPERT) BooleanSetting("Пушистая поверхность", s.fuzzySkin) { set(s.copy(fuzzySkin = it)) }
    }
    if (level == PrintDetailLevel.EXPERT) SettingsAccordion("Вывод G-code") {
        BooleanSetting("Подробные комментарии", s.gcodeComments) { set(s.copy(gcodeComments = it)) }
        BooleanSetting("Метки объектов", s.labelObjects) { set(s.copy(labelObjects = it)) }
        BooleanSetting("Поддержка исключения объектов", s.excludeObjects) { set(s.copy(excludeObjects = it)) }
        NumericSetting("Шаблон имени файла", s.filenameFormat) { set(s.copy(filenameFormat = it)) }
    }
}

@Composable
private fun SettingsAccordion(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                summary?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                Text(if (expanded) " ︿" else " ﹀", fontSize = 18.sp)
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { content() }
            }
        }
    }
}

@Composable
private fun NumericSetting(label: String, value: String, unit: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = unit?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BooleanSetting(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!value) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceSetting(label: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = dropdownLabel(value, options),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Text("⌄") },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (wireValue, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onChange(wireValue)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CoreSupportHint() {
    Text("✓ Используется текущим ядром нарезки", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
}

private fun dropdownLabel(value: String, options: List<Pair<String, String>>): String =
    options.firstOrNull { it.first == value }?.second ?: value

private val seamOptions = listOf("nearest" to "Ближайшая", "aligned" to "Выровненная", "back" to "Сзади", "random" to "Случайная")
private val wallGeneratorOptions = listOf("arachne" to "Arachne", "classic" to "Классический")
private val wallSequenceOptions = listOf("inner wall/outer wall" to "Внутренняя → внешняя", "outer wall/inner wall" to "Внешняя → внутренняя", "inner-outer-inner wall" to "Внутренняя → внешняя → внутренняя")
private val surfacePatternOptions = listOf("monotonicline" to "Монотонные линии", "monotonic" to "Монотонный", "rectilinear" to "Линии", "concentric" to "Концентрический")
private val infillPatternOptions = listOf(
    "gyroid" to "Гироид",
    "grid" to "Сетка",
    "rectilinear" to "Прямолинейный",
    "line" to "Линии",
)
private val supportTypeOptions = listOf("normal(auto)" to "Обычная (авто)", "normal(manual)" to "Обычная (ручная)", "tree(auto)" to "Дерево (авто)", "tree(manual)" to "Дерево (ручная)")
private val brimTypeOptions = listOf("no_brim" to "Без каймы", "auto_brim" to "Автоматическая", "outer_only" to "Только внешняя", "inner_only" to "Только внутренняя", "outer_and_inner" to "Внешняя и внутренняя")
private val slicingModeOptions = listOf("regular" to "Обычный", "even_odd" to "Чётно-нечётный", "close_holes" to "Закрывать отверстия")
private val printSequenceOptions = listOf("by layer" to "По слоям", "by object" to "По объектам")

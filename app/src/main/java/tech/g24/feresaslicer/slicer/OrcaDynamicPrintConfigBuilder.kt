// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import java.io.File
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Collections
import tech.g24.feresaslicer.auth.OrcaCloudProfile
import tech.g24.feresaslicer.auth.OrcaProfileType

/** The currently selected OrcaCloud presets, kept separate by their native preset role. */
data class OrcaSelectedProfiles(
    val printer: OrcaCloudProfile? = null,
    val filament: OrcaCloudProfile? = null,
    val process: OrcaCloudProfile? = null,
    /** Profiles from the same sync response, used to resolve Orca `inherits` chains by name. */
    val availableCloudProfiles: List<OrcaCloudProfile> = emptyList(),
)

/**
 * Machine and single-filament values edited outside the process-settings screen.
 *
 * These are live values: they intentionally override the corresponding selected preset values in
 * the same way that unsaved edits override a preset in OrcaSlicer.
 */
data class OrcaMachineFilamentScalars(
    val bedWidthMm: Double,
    val bedDepthMm: Double,
    val printableHeightMm: Double,
    val nozzleDiameterMm: String,
    val filamentDiameterMm: String,
    val nozzleTemperatureC: String,
    val bedTemperatureC: String,
    val gcodeFlavor: String,
) {
    fun toOrcaSettings(): Map<String, String> {
        val width = positiveDimension(bedWidthMm, "bed width")
        val depth = positiveDimension(bedDepthMm, "bed depth")
        val height = positiveDimension(printableHeightMm, "printable height")
        val nozzle = positiveDecimal(nozzleDiameterMm, "nozzle diameter")
        val filament = positiveDecimal(filamentDiameterMm, "filament diameter")
        val nozzleTemperature = nonNegativeDecimal(nozzleTemperatureC, "nozzle temperature")
        val bedTemperature = nonNegativeDecimal(bedTemperatureC, "bed temperature")
        val flavor = gcodeFlavor.trim().also {
            require(it.isNotEmpty()) { "G-code flavor must not be blank" }
        }

        return linkedMapOf(
            "printer_technology" to "FFF",
            "printable_area" to "0x0,${width}x0,${width}x${depth},0x${depth}",
            "printable_height" to height,
            "gcode_flavor" to flavor,
            "nozzle_diameter" to nozzle,
            "filament_diameter" to filament,
            "nozzle_temperature" to nozzleTemperature,
            "nozzle_temperature_initial_layer" to nozzleTemperature,
            // Use the same bed-temperature slot that the mobile UI edits. ConfigDef has a
            // concrete Cool Plate default, so relying on the native bridge's "missing value"
            // fallback would silently leave hot_plate_temp unused.
            "curr_bed_type" to "High Temp Plate",
            "hot_plate_temp" to bedTemperature,
            "hot_plate_temp_initial_layer" to bedTemperature,
        )
    }

    private fun positiveDimension(value: Double, label: String): String {
        require(value.isFinite() && value > 0.0) { "$label must be a finite positive number" }
        return canonicalDecimal(BigDecimal.valueOf(value))
    }

    private fun positiveDecimal(value: String, label: String): String {
        val parsed = parseDecimal(value, label)
        require(parsed > BigDecimal.ZERO) { "$label must be positive" }
        return canonicalDecimal(parsed)
    }

    private fun nonNegativeDecimal(value: String, label: String): String {
        val parsed = parseDecimal(value, label)
        require(parsed >= BigDecimal.ZERO) { "$label must not be negative" }
        return canonicalDecimal(parsed)
    }

    private fun parseDecimal(value: String, label: String): BigDecimal = runCatching {
        BigDecimal(value.trim())
    }.getOrElse { error -> throw IllegalArgumentException("$label must be a number", error) }

    private fun canonicalDecimal(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()
}

/** Fully overlaid, native-ready Orca `DynamicPrintConfig`. */
class OrcaDynamicPrintConfig internal constructor(
    settings: Map<String, String>,
    val ini: String,
) {
    val settings: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(settings))

    fun writeTo(file: File): File {
        file.parentFile?.let { parent ->
            require(parent.exists() || parent.mkdirs()) { "Cannot create config directory: $parent" }
        }
        file.outputStream().bufferedWriter(StandardCharsets.UTF_8).use { writer -> writer.write(ini) }
        return file
    }
}

/**
 * Builds the same single merged configuration shape consumed by OrcaSlicer Mobile's
 * `model_slice`: native defaults, selected presets, live machine/filament values, then live print
 * controls. Every overlay key is checked against the options reported by the same native engine.
 */
object OrcaDynamicPrintConfigBuilder {
    fun build(
        profiles: OrcaSelectedProfiles,
        machineFilament: OrcaMachineFilamentScalars,
        liveProcessSettings: OrcaProcessSettingsPayload,
    ): OrcaDynamicPrintConfig {
        OrcaProcessSettingsValidator.validateOrThrow(liveProcessSettings)
        return build(
            runtimeDefaults = OrcaDefaultConfigProvider.fffDefaults(),
            supportedKeys = OrcaDefaultConfigProvider.fffOptionKeys(),
            profiles = profiles,
            machineFilament = machineFilament,
            liveProcessSettings = liveProcessSettings,
        )
    }

    /** Visible for deterministic JVM tests; production callers should use the overload above. */
    fun build(
        runtimeDefaults: Map<String, String>,
        supportedKeys: Set<String>,
        profiles: OrcaSelectedProfiles,
        machineFilament: OrcaMachineFilamentScalars,
        liveProcessSettings: OrcaProcessSettingsPayload,
    ): OrcaDynamicPrintConfig {
        require(supportedKeys.isNotEmpty()) { "The Orca engine did not report any FFF options" }
        require(runtimeDefaults.keys.all(supportedKeys::contains)) {
            "Orca runtime defaults contain options outside the engine definition"
        }
        requireProfileType(profiles.printer, OrcaProfileType.PRINTER)
        requireProfileType(profiles.filament, OrcaProfileType.FILAMENT)
        requireProfileType(profiles.process, OrcaProfileType.PROCESS)

        val merged = LinkedHashMap<String, String>(runtimeDefaults.size + 96)
        runtimeDefaults.toSortedMap().forEach { (key, value) -> merged[key] = value }

        // Match OrcaSlicer Mobile buildCurrentConfigObject(): printer, print, then filament.
        overlayProfile(merged, profiles.printer, profiles.availableCloudProfiles, supportedKeys)
        overlayProfile(merged, profiles.process, profiles.availableCloudProfiles, supportedKeys)
        overlayProfile(merged, profiles.filament, profiles.availableCloudProfiles, supportedKeys)
        overlayChecked(merged, machineFilament.toOrcaSettings(), supportedKeys, "live machine/filament")

        liveProcessSettings.asMap().forEach { (key, value) ->
            require(key in supportedKeys) {
                "Print setting '$key' is exposed by Feresa but unsupported by this Orca engine"
            }
            if (value == null) merged.remove(key) else merged[key] = primitiveToIni(key, value)
        }

        // ConfigDef defaults are option-level defaults, not a complete generic printer preset.
        // Relative extrusion is valid only when the engine resets E before every layer. The error
        // text in this pinned Orca build still calls this legacy option `layer_gcode`, but the
        // actual PrintConfig key checked by Print.cpp is `before_layer_change_gcode`. Apply this
        // after all overlays to make no-profile and incomplete cloud presets safe without ever
        // emitting an invalid relative-E G-code.
        if (merged["use_relative_e_distances"].isOrcaTrue()) {
            if ("before_layer_change_gcode" in supportedKeys) {
                val beforeLayerGcode = merged["before_layer_change_gcode"].orEmpty()
                if (!beforeLayerGcode.containsG92ExtruderReset()) {
                    merged["before_layer_change_gcode"] = if (beforeLayerGcode.isBlank()) {
                        "G92 E0"
                    } else {
                        "${beforeLayerGcode.trimEnd()}\\nG92 E0"
                    }
                }
            } else {
                merged["use_relative_e_distances"] = "0"
            }
        }

        val sorted = merged.toSortedMap()
        // Values in `sorted` are already in ConfigOption::serialize() form. In particular, native
        // defaults may contain `\n` and `\\`; `encodeSerializedValues` deliberately preserves
        // them rather than applying the raw-value C-style escaper a second time.
        val ini = OrcaProcessSettingsIni.encodeSerializedValues(sorted)
        return OrcaDynamicPrintConfig(sorted, ini)
    }

    private fun overlayProfile(
        target: MutableMap<String, String>,
        profile: OrcaCloudProfile?,
        availableProfiles: List<OrcaCloudProfile>,
        supportedKeys: Set<String>,
    ) {
        if (profile == null) return
        target.putAll(
            OrcaProfileSettingsResolver.resolve(
                profile = profile,
                availableProfiles = availableProfiles,
                supportedKeys = supportedKeys,
            ),
        )
    }

    private fun overlayChecked(
        target: MutableMap<String, String>,
        overlay: Map<String, String>,
        supportedKeys: Set<String>,
        source: String,
    ) {
        val unsupported = overlay.keys.filterNot(supportedKeys::contains)
        require(unsupported.isEmpty()) {
            "$source contains options unsupported by this Orca engine: ${unsupported.sorted().joinToString()}"
        }
        overlay.forEach { (key, value) -> target[key] = value }
    }

    private fun requireProfileType(profile: OrcaCloudProfile?, expected: OrcaProfileType) {
        require(profile == null || profile.type == expected) {
            "Profile '${profile?.name}' is ${profile?.type}, expected $expected"
        }
    }

    private fun primitiveToIni(key: String, value: Any): String = when (value) {
        is String -> escapeRawIniValue(value)
        is Boolean -> if (value) "1" else "0"
        is Number -> value.toString()
        else -> throw IllegalArgumentException(
            "Live Orca setting '$key' must be a primitive, got ${value::class.java.name}",
        )
    }

    private fun escapeRawIniValue(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\r' -> append("\\r")
                '\n' -> append("\\n")
                '\\', '"' -> append('\\').append(char)
                else -> append(char)
            }
        }
    }

    private fun String?.isOrcaTrue(): Boolean =
        this?.trim()?.let { it == "1" || it.equals("true", ignoreCase = true) } == true

    private fun String.containsG92ExtruderReset(): Boolean = Regex(
        "G92\\s+E\\s*0(?:\\.0*)?",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(this)

}

// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef

/** Validates editable process values against the schema exported by the same native Orca build. */
object OrcaProcessSettingsValidator {
    fun validateOrThrow(payload: OrcaProcessSettingsPayload) {
        validateOrThrow(payload, PrintConfigDef.getInstance().options)
    }

    internal fun validateOrThrow(
        payload: OrcaProcessSettingsPayload,
        definitions: Map<String, ConfigOptionDef>,
    ) {
        payload.asMap().forEach { (key, rawValue) ->
            if (rawValue == null) return@forEach
            val definition = definitions[key]
                ?: throw IllegalArgumentException("Orca does not support print setting '$key'")
            val value = rawValue.toString().trim()
            require(value.isNotEmpty() || definition.acceptsFreeText()) {
                "Print setting '$key' must not be empty"
            }
            validateValue(key, value, definition)
        }
    }

    private fun validateValue(key: String, value: String, definition: ConfigOptionDef) {
        when (definition.type) {
            ConfigOptionDef.ConfigOptionType.BOOL -> require(value.isOrcaBoolean()) {
                "Print setting '$key' must be true or false"
            }
            ConfigOptionDef.ConfigOptionType.BOOLS -> value.split(',').forEach { item ->
                require(item.trim().isOrcaBoolean()) { "Print setting '$key' contains an invalid boolean" }
            }
            ConfigOptionDef.ConfigOptionType.INT,
            ConfigOptionDef.ConfigOptionType.FLOAT,
            ConfigOptionDef.ConfigOptionType.PERCENT,
            ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT,
            -> validateNumbers(key, listOf(value), definition)
            ConfigOptionDef.ConfigOptionType.INTS,
            ConfigOptionDef.ConfigOptionType.FLOATS,
            ConfigOptionDef.ConfigOptionType.PERCENTS,
            ConfigOptionDef.ConfigOptionType.FLOATS_OR_PERCENTS,
            -> validateNumbers(key, value.split(','), definition)
            ConfigOptionDef.ConfigOptionType.ENUM -> validateEnum(key, value, definition)
            ConfigOptionDef.ConfigOptionType.ENUMS -> value.split(',').forEach { item ->
                validateEnum(key, item.trim(), definition)
            }
            ConfigOptionDef.ConfigOptionType.POINT -> require(value.matches(PointPattern)) {
                "Print setting '$key' must be an XxY point"
            }
            ConfigOptionDef.ConfigOptionType.POINT3 -> require(value.matches(Point3Pattern)) {
                "Print setting '$key' must be an XxYxZ point"
            }
            else -> Unit
        }
    }

    private fun validateNumbers(
        key: String,
        values: List<String>,
        definition: ConfigOptionDef,
    ) {
        values.forEach { item ->
            val normalized = item.trim().removeSuffix("%").trim()
            val number = normalized.toDoubleOrNull()
            require(number != null && number.isFinite()) { "Print setting '$key' must be a number" }
            if (definition.type == ConfigOptionDef.ConfigOptionType.INT ||
                definition.type == ConfigOptionDef.ConfigOptionType.INTS
            ) {
                require(number % 1.0 == 0.0) { "Print setting '$key' must be an integer" }
            }
            if (definition.min != Float.MIN_VALUE) {
                require(number >= definition.min.toDouble()) {
                    "Print setting '$key' must be at least ${definition.min}"
                }
            }
            if (definition.max != Float.MAX_VALUE) {
                require(number <= definition.max.toDouble()) {
                    "Print setting '$key' must be at most ${definition.max}"
                }
            }
        }
    }

    private fun validateEnum(key: String, value: String, definition: ConfigOptionDef) {
        val allowed = definition.enumValues?.filterNotNull().orEmpty()
        require(allowed.isEmpty() || value in allowed) {
            "Print setting '$key' has unsupported value '$value'"
        }
    }

    private fun ConfigOptionDef.acceptsFreeText(): Boolean =
        type == ConfigOptionDef.ConfigOptionType.STRING ||
            type == ConfigOptionDef.ConfigOptionType.STRINGS

    private fun String.isOrcaBoolean(): Boolean =
        this == "0" || this == "1" || equals("true", ignoreCase = true) ||
            equals("false", ignoreCase = true)

    private val PointPattern = Regex("-?\\d+(?:\\.\\d+)?x-?\\d+(?:\\.\\d+)?")
    private val Point3Pattern = Regex("-?\\d+(?:\\.\\d+)?x-?\\d+(?:\\.\\d+)?x-?\\d+(?:\\.\\d+)?")
}

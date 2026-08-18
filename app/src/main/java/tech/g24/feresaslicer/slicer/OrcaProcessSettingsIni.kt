// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.slicer

import org.json.JSONObject

/**
 * Converts process settings to the deterministic `key = value` input consumed by
 * `DynamicPrintConfig::load_from_ini_string` in OrcaSlicer.
 *
 * Base profile values are copied first and process values replace them by exact key. A null value
 * explicitly removes an inherited key. No option whitelist or key translation is applied.
 */
object OrcaProcessSettingsIni {
    /**
     * Writes values that are already in Orca's serialized option format.
     *
     * `get_print_config_def` returns `ConfigOption::serialize()` verbatim. Escaping those strings
     * again would turn a native `\\n` into `\\\\n` and change G-code/template settings. This path
     * therefore mirrors OrcaSlicer Mobile's `ConfigObject.serialize()`: stored backslashes and
     * quotes remain untouched and only an actual LF is represented as `\\n` in the INI line.
     */
    fun encodeSerializedValues(settings: Map<String, String>): String {
        settings.forEach { (key, value) ->
            validateIniKey(key)
            validateIniValue(key, value)
        }
        return settings.keys.sorted().joinToString(separator = "", postfix = "") { key ->
            "$key = ${settings.getValue(key).replace("\n", "\\n")}\n"
        }
    }

    fun encodeSerializedValuesUtf8(settings: Map<String, String>): ByteArray =
        encodeSerializedValues(settings).encodeToByteArray()

    fun encode(
        process: OrcaProcessSettingsPayload,
        baseProfileSettings: Map<String, *> = emptyMap<String, Any?>(),
    ): String {
        val merged = LinkedHashMap<String, Any>()
        baseProfileSettings.forEach { (key, value) ->
            validateIniKey(key)
            if (value != null) merged[key] = validateIniValue(key, value)
        }
        process.asMap().forEach { (key, value) ->
            validateIniKey(key)
            if (value == null) merged.remove(key) else merged[key] = validateIniValue(key, value)
        }

        return merged.keys.sorted().joinToString(separator = "", postfix = "") { key ->
            "$key = ${encodeValue(merged.getValue(key))}\n"
        }
    }

    fun encodeUtf8(
        process: OrcaProcessSettingsPayload,
        baseProfileSettings: Map<String, *> = emptyMap<String, Any?>(),
    ): ByteArray = encode(process, baseProfileSettings).encodeToByteArray()

    private fun validateIniKey(key: String) {
        require(IniKey.matches(key)) {
            "Invalid Orca INI setting key '$key'; expected [A-Za-z_][A-Za-z0-9_]*"
        }
    }

    private fun validateIniValue(key: String, value: Any): Any {
        require(value is String || value is Boolean || value is Number) {
            "Orca INI setting '$key' must be a primitive value, got ${value::class.java.name}"
        }
        if (value is Float) require(value.isFinite()) { "Orca INI setting '$key' must be finite" }
        if (value is Double) require(value.isFinite()) { "Orca INI setting '$key' must be finite" }
        if (value is String) {
            require(value.none { it == '\u0000' || it.isISOControl() && it !in AllowedControls }) {
                "Orca INI setting '$key' contains an unsupported control character"
            }
        }
        return value
    }

    private fun encodeValue(value: Any): String = when (value) {
        is String -> escapeCStyle(value)
        is Boolean -> if (value) "1" else "0"
        is Number -> JSONObject.numberToString(value)
        else -> error("Unvalidated Orca INI value: ${value::class.java.name}")
    }

    /** Matches Orca's `escape_string_cstyle`: escape quotes, CR, LF and backslash. */
    private fun escapeCStyle(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\r' -> append("\\r")
                '\n' -> append("\\n")
                '\\', '"' -> append('\\').append(char)
                else -> append(char)
            }
        }
    }

    private val IniKey = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val AllowedControls = setOf('\t', '\r', '\n')
}

// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import org.json.JSONArray
import org.json.JSONObject

enum class OrcaProfileType(val wireValue: String) {
    PRINTER("machine"),
    FILAMENT("filament"),
    PROCESS("process"),
    OTHER("other");

    companion object {
        fun fromWire(value: String): OrcaProfileType = when (value.lowercase()) {
            "machine", "printer" -> PRINTER
            "filament" -> FILAMENT
            "process", "print" -> PROCESS
            else -> OTHER
        }
    }
}

data class OrcaCloudProfile(
    val id: String,
    val name: String,
    val type: OrcaProfileType,
    val contentJson: String,
    val updatedTime: Long,
) {
    fun setting(key: String): String? = runCatching {
        val value = JSONObject(contentJson).opt(key) ?: return@runCatching null
        when (value) {
            is JSONArray -> value.optString(0).takeIf(String::isNotBlank)
            is String -> firstArrayValue(value) ?: value.takeIf(String::isNotBlank)
            JSONObject.NULL -> null
            else -> value.toString()
        }
    }.getOrNull()

    /** Full serialized profile value for vector options such as `printable_area`. */
    fun serializedSetting(key: String): String? = runCatching {
        val value = JSONObject(contentJson).opt(key) ?: return@runCatching null
        when (value) {
            is JSONArray -> value.joinPrimitiveValues()
            is String -> value.parseStringifiedArray()?.joinPrimitiveValues()
                ?: value.takeIf(String::isNotBlank)
            JSONObject.NULL -> null
            else -> value.toString()
        }
    }.getOrNull()

    /**
     * Returns the part of this profile that can be consumed by the loaded Orca engine.
     *
     * OrcaCloud stores option vectors as JSON arrays while `DynamicPrintConfig::load()` expects
     * their normal comma-separated INI representation. The conversion intentionally mirrors
     * OrcaSlicer Mobile's `configJsonToString`: every array item is retained, in order. Profile
     * metadata (`name`, `type`, `inherits`, connection data, and so on) is excluded by accepting
     * only keys reported by the native engine's `PrintConfigDef`; there is no hand-maintained
     * settings whitelist that could silently discard a newly supported Orca option.
     *
     * A supported option with an object or nested-array value is rejected. Passing malformed
     * profile data through to the native parser would otherwise make the selected profile appear
     * to work while slicing with unrelated defaults.
     */
    fun settingsMap(supportedKeys: Set<String>): Map<String, String> {
        require(supportedKeys.isNotEmpty()) { "The Orca engine did not report any supported options" }
        val root = runCatching { JSONObject(contentJson) }
            .getOrElse { error -> throw IllegalArgumentException("Invalid OrcaCloud profile '$name'", error) }
        val settings = linkedMapOf<String, String>()
        root.keys().asSequence().sorted().forEach { key ->
            if (key !in supportedKeys) return@forEach
            profileValueToIni(key, root.opt(key))?.let { settings[key] = it }
        }
        return settings.toMap()
    }

    /** Profile inheritance metadata is resolved by the config builder, not sent to libslic3r. */
    fun inheritedProfileName(): String? = runCatching {
        JSONObject(contentJson).optString("inherits").trim().takeIf(String::isNotEmpty)
    }.getOrNull()

    private fun firstArrayValue(value: String): String? {
        if (!value.trimStart().startsWith("[")) return null
        return runCatching { JSONArray(value).optString(0).takeIf(String::isNotBlank) }.getOrNull()
    }

    private fun String.parseStringifiedArray(): JSONArray? {
        if (!trimStart().startsWith("[")) return null
        return runCatching { JSONArray(this) }.getOrNull()
    }

    private fun JSONArray.joinPrimitiveValues(): String = buildList<String>(length()) {
        for (index in 0 until this@joinPrimitiveValues.length()) {
            val item = this@joinPrimitiveValues.opt(index)
            if (item != null && item !== JSONObject.NULL && item !is JSONObject && item !is JSONArray) {
                add(item.toString())
            }
        }
    }.joinToString(",").takeIf(String::isNotBlank).orEmpty()

    private fun profileValueToIni(key: String, value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        is JSONArray -> value.toIniVector(key)
        is String -> value.stringifiedArrayToIni(key) ?: value.escapeRawIniValue()
        is Boolean, is Number -> value.toString()
        else -> throw IllegalArgumentException(
            "OrcaCloud profile '$name' option '$key' must be a primitive or flat array",
        )
    }

    private fun String.stringifiedArrayToIni(key: String): String? {
        if (!trimStart().startsWith("[")) return null
        return runCatching { JSONArray(this).toIniVector(key) }.getOrElse { error ->
            throw IllegalArgumentException(
                "OrcaCloud profile '$name' option '$key' contains an invalid JSON array",
                error,
            )
        }
    }

    private fun JSONArray.toIniVector(key: String): String = buildList<String>(length()) {
        for (index in 0 until this@toIniVector.length()) {
            val item = this@toIniVector.get(index)
            require(item !== JSONObject.NULL && item !is JSONObject && item !is JSONArray) {
                "OrcaCloud profile '$name' option '$key' must contain only primitive array items"
            }
            add(item.toString())
        }
    }.joinToString(",")

    /** Raw JSON strings become one native INI value; already-serialized arrays stay verbatim. */
    private fun String.escapeRawIniValue(): String = buildString(length) {
        this@escapeRawIniValue.forEach { char ->
            when (char) {
                '\r' -> append("\\r")
                '\n' -> append("\\n")
                '\\', '"' -> append('\\').append(char)
                else -> append(char)
            }
        }
    }
}

enum class OrcaProfileOrigin {
    NONE,
    CLOUD,
    CACHE,
    REVIEW_DEMO,
}

data class OrcaProfileSyncState(
    val profiles: List<OrcaCloudProfile> = emptyList(),
    val isLoading: Boolean = false,
    val isCached: Boolean = false,
    val lastSyncedAt: Long? = null,
    val error: String? = null,
    val origin: OrcaProfileOrigin = OrcaProfileOrigin.NONE,
)

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
            "machine", "machine_model", "printer" -> PRINTER
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

    private fun firstArrayValue(value: String): String? {
        if (!value.trimStart().startsWith("[")) return null
        return runCatching { JSONArray(value).optString(0).takeIf(String::isNotBlank) }.getOrNull()
    }
}

data class OrcaProfileSyncState(
    val profiles: List<OrcaCloudProfile> = emptyList(),
    val isLoading: Boolean = false,
    val isCached: Boolean = false,
    val lastSyncedAt: Long? = null,
    val error: String? = null,
)

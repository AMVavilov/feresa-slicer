// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class CachedOrcaProfiles(
    val userId: String,
    val profiles: List<OrcaCloudProfile>,
    val syncedAt: Long,
)

internal class OrcaProfileCache(context: Context) {
    private val cacheFile = File(context.filesDir, "orca_profiles_cache.json")

    fun read(): CachedOrcaProfiles? = runCatching {
        if (!cacheFile.exists()) return null
        val root = JSONObject(cacheFile.readText())
        val profiles = root.optJSONArray("profiles") ?: JSONArray()
        CachedOrcaProfiles(
            userId = root.getString("user_id"),
            syncedAt = root.optLong("synced_at"),
            profiles = buildList {
                for (index in 0 until profiles.length()) {
                    val item = profiles.getJSONObject(index)
                    add(
                        OrcaCloudProfile(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            type = OrcaProfileType.fromWire(item.optString("type")),
                            contentJson = item.getJSONObject("content").toString(),
                            updatedTime = item.optLong("updated_time"),
                        ),
                    )
                }
            },
        )
    }.getOrNull()

    fun write(userId: String, profiles: List<OrcaCloudProfile>, syncedAt: Long) {
        val items = JSONArray()
        profiles.forEach { profile ->
            items.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("type", profile.type.wireValue)
                    .put("content", JSONObject(profile.contentJson))
                    .put("updated_time", profile.updatedTime),
            )
        }
        val root = JSONObject()
            .put("user_id", userId)
            .put("synced_at", syncedAt)
            .put("profiles", items)
        cacheFile.writeText(root.toString())
    }

    fun clear() {
        if (cacheFile.exists()) cacheFile.delete()
    }
}

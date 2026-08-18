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

internal class OrcaProfileCache(
    context: Context,
    cacheFileName: String = "orca_profiles_cache.enc",
    legacyCacheFileName: String = "orca_profiles_cache.json",
    keyAlias: String = KEY_ALIAS,
) {
    private val cacheFile = File(context.filesDir, cacheFileName)
    private val legacyCacheFile = File(context.filesDir, legacyCacheFileName)
    private val cipher = AndroidKeystoreAesGcm(keyAlias)

    fun read(): CachedOrcaProfiles? = runCatching {
        val json = when {
            cacheFile.exists() -> String(cipher.decrypt(cacheFile.readText()), Charsets.UTF_8).also {
                legacyCacheFile.delete()
            }
            legacyCacheFile.exists() -> legacyCacheFile.readText().also { legacy ->
                writeEncrypted(legacy)
                legacyCacheFile.delete()
            }
            else -> return null
        }
        val root = JSONObject(json)
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
        writeEncrypted(root.toString())
        legacyCacheFile.delete()
    }

    fun clear() {
        if (cacheFile.exists()) cacheFile.delete()
        if (legacyCacheFile.exists()) legacyCacheFile.delete()
    }

    private fun writeEncrypted(json: String) {
        val temporary = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        temporary.writeText(cipher.encrypt(json.toByteArray(Charsets.UTF_8)))
        if (cacheFile.exists() && !cacheFile.delete()) {
            temporary.delete()
            error("Could not replace encrypted Orca profile cache")
        }
        if (!temporary.renameTo(cacheFile)) {
            temporary.delete()
            error("Could not save encrypted Orca profile cache")
        }
    }

    private companion object {
        const val KEY_ALIAS = "feresa_slicer_orca_profiles"
    }
}

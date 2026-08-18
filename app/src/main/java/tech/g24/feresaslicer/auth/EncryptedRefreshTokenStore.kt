// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import android.content.Context
internal class EncryptedRefreshTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val cipher = AndroidKeystoreAesGcm(KEY_ALIAS)

    fun read(): String? {
        val encoded = preferences.getString(KEY_TOKEN, null) ?: return null
        return runCatching {
            String(cipher.decrypt(encoded), Charsets.UTF_8)
        }.getOrElse {
            clear()
            null
        }
    }

    fun write(token: String) {
        val value = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
        preferences.edit().putString(KEY_TOKEN, value).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val PREFERENCES = "orca_cloud_session"
        const val KEY_TOKEN = "encrypted_refresh_token"
        const val KEY_ALIAS = "feresa_slicer_orca_cloud"
    }
}

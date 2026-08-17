// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import tech.g24.feresaslicer.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

internal data class OrcaPkceBundle(
    val verifier: String,
    val challenge: String,
    val state: String,
)

internal data class OrcaSession(
    val accessToken: String,
    val refreshToken: String,
    val account: OrcaAccount,
)

internal class OrcaCloudAuthClient {
    fun createPkce(): OrcaPkceBundle {
        val verifier = randomBase64Url(32)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return OrcaPkceBundle(verifier, challenge, randomBase64Url(32))
    }

    fun authorizationUri(
        provider: OrcaAuthProvider,
        redirectUri: String,
        pkce: OrcaPkceBundle,
    ): Uri = Uri.parse(BuildConfig.ORCA_AUTH_URL).buildUpon()
        .appendEncodedPath("auth/v1/authorize")
        .appendQueryParameter("provider", provider.wireValue)
        .appendQueryParameter("code_challenge", pkce.challenge)
        .appendQueryParameter("code_challenge_method", "S256")
        .appendQueryParameter("redirect_to", "$redirectUri?orca_state=${Uri.encode(pkce.state)}")
        .build()

    fun exchangeCode(code: String, verifier: String): OrcaSession = postToken(
        endpoint = "auth/v1/token?grant_type=pkce",
        body = JSONObject().put("auth_code", code).put("code_verifier", verifier),
    )

    fun refresh(refreshToken: String): OrcaSession = postToken(
        endpoint = "auth/v1/token?grant_type=refresh_token",
        body = JSONObject().put("refresh_token", refreshToken),
    )

    fun logout(session: OrcaSession) {
        post(
            endpoint = "auth/v1/logout?scope=local",
            body = JSONObject().put("refresh_token", session.refreshToken),
            accessToken = session.accessToken,
        )
    }

    fun pullProfiles(accessToken: String): List<OrcaCloudProfile> {
        val connection = (URL("${BuildConfig.ORCA_API_URL}/api/v1/sync/pull").openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("apikey", BuildConfig.ORCA_PUBLIC_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw OrcaAuthHttpException(status, apiError(response, status))
            }
            val upserts = JSONObject(response).optJSONArray("upserts") ?: JSONArray()
            buildList {
                for (index in 0 until upserts.length()) {
                    val item = upserts.getJSONObject(index)
                    val content = item.optJSONObject("content") ?: JSONObject()
                    val name = content.optString("name").ifBlank {
                        item.optString("name").ifBlank { item.optString("id") }
                    }
                    add(
                        OrcaCloudProfile(
                            id = item.optString("id"),
                            name = name,
                            type = OrcaProfileType.fromWire(content.optString("type")),
                            contentJson = content.toString(),
                            updatedTime = item.optLong("updated_time"),
                        ),
                    )
                }
            }.sortedWith(compareBy({ it.type.ordinal }, { it.name.lowercase() }))
        } finally {
            connection.disconnect()
        }
    }

    private fun postToken(endpoint: String, body: JSONObject): OrcaSession {
        val json = post(endpoint, body)
        val accessToken = json.optString("access_token")
        val refreshToken = json.optString("refresh_token")
        val user = json.optJSONObject("user") ?: JSONObject()
        require(accessToken.isNotBlank() && refreshToken.isNotBlank()) {
            "OrcaCloud returned an incomplete session"
        }

        val metadata = user.optJSONObject("user_metadata") ?: JSONObject()
        val email = user.optString("email")
        val displayName = listOf("display_name", "full_name", "name", "user_name")
            .firstNotNullOfOrNull { key -> metadata.optString(key).takeIf(String::isNotBlank) }
            ?: email.substringBefore('@').ifBlank { "OrcaCloud user" }
        return OrcaSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            account = OrcaAccount(user.optString("id"), email, displayName),
        )
    }

    private fun post(
        endpoint: String,
        body: JSONObject,
        accessToken: String? = null,
    ): JSONObject {
        val url = URL("${BuildConfig.ORCA_AUTH_URL.trimEnd('/')}/$endpoint")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("apikey", BuildConfig.ORCA_PUBLIC_KEY)
            if (accessToken != null) setRequestProperty("Authorization", "Bearer $accessToken")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw OrcaAuthHttpException(status, apiError(response, status))
            }
            if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun randomBase64Url(bytes: Int): String {
        val data = ByteArray(bytes).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun apiError(response: String, status: Int): String = runCatching {
        val error = JSONObject(response)
        error.optString("msg").ifBlank {
            error.optString("message").ifBlank {
                error.optString("error_description").ifBlank { error.optString("error") }
            }
        }
    }.getOrNull().orEmpty().ifBlank { "OrcaCloud request failed ($status)" }
}

internal class OrcaAuthHttpException(val status: Int, message: String) : Exception(message)

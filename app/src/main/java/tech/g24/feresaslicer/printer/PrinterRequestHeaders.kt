// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.printer

import java.nio.charset.StandardCharsets
import java.util.Base64

internal const val ApiKeyHeader: String = "X-Api-Key"
internal const val AuthorizationHeader: String = "Authorization"

/**
 * Builds authentication headers without performing I/O or exposing credentials to logs.
 *
 * API-key and HTTP Basic authentication are independent: when both are complete, both headers are
 * sent. An incomplete Basic credential pair is ignored rather than producing a request that can
 * never authenticate. Values are intentionally not trimmed because spaces may be significant in a
 * username, password, or API key; [String.isNotBlank] is used only to determine configuration.
 */
internal fun printerAuthenticationHeaders(
    apiKey: String,
    username: String,
    password: String,
): Map<String, String> = buildMap {
    if (apiKey.isNotBlank()) {
        put(ApiKeyHeader, apiKey)
    }
    if (username.isNotBlank() && password.isNotBlank()) {
        val credentials = "$username:$password".toByteArray(StandardCharsets.UTF_8)
        try {
            val encoded = Base64.getEncoder().encodeToString(credentials)
            put(AuthorizationHeader, "Basic $encoded")
        } finally {
            credentials.fill(0)
        }
    }
}.toMap()

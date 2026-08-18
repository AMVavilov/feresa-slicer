// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.printer

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterRequestHeadersTest {
    @Test
    fun apiKeyIsPreservedWhenBasicAuthenticationIsAlsoConfigured() {
        val headers = printerAuthenticationHeaders(
            apiKey = "moonraker-secret",
            username = "daria",
            password = "p@ss:word",
        )

        assertEquals("moonraker-secret", headers[ApiKeyHeader])
        assertEquals("Basic ${base64("daria:p@ss:word")}", headers[AuthorizationHeader])
        assertEquals(setOf(ApiKeyHeader, AuthorizationHeader), headers.keys)
    }

    @Test
    fun completeBasicCredentialsUseUtf8AndNoWrappedBase64() {
        val headers = printerAuthenticationHeaders(
            apiKey = "",
            username = "Дарья",
            password = "пароль",
        )

        val authorization = requireNotNull(headers[AuthorizationHeader])
        assertEquals("Basic ${base64("Дарья:пароль")}", authorization)
        assertFalse(authorization.contains('\n'))
        assertFalse(headers.containsKey(ApiKeyHeader))
    }

    @Test
    fun incompleteOrBlankBasicCredentialsAreNotSent() {
        val incompletePairs = listOf(
            "user" to "",
            "" to "password",
            "   " to "password",
            "user" to "   ",
        )

        incompletePairs.forEach { (username, password) ->
            val headers = printerAuthenticationHeaders("api-key", username, password)
            assertEquals("api-key", headers[ApiKeyHeader])
            assertFalse(headers.containsKey(AuthorizationHeader))
        }
    }

    @Test
    fun noConfiguredAuthenticationProducesNoHeaders() {
        assertTrue(printerAuthenticationHeaders("", "", "").isEmpty())
    }

    private fun base64(value: String): String = Base64.getEncoder().encodeToString(
        value.toByteArray(StandardCharsets.UTF_8),
    )
}

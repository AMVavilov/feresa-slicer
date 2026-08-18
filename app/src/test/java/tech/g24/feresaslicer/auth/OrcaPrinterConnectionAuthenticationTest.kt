// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaPrinterConnectionAuthenticationTest {
    @Test
    fun authenticationStatusRequiresApiKeyOrCompleteBasicPair() {
        assertFalse(connection().hasAuthentication)
        assertFalse(connection(username = "user").hasAuthentication)
        assertFalse(connection(password = "password").hasAuthentication)
        assertFalse(connection(username = "user", password = "   ").hasAuthentication)
        assertTrue(connection(apiKey = "api-key").hasAuthentication)
        assertTrue(connection(username = "user", password = "password").hasAuthentication)
        assertTrue(
            connection(apiKey = "api-key", username = "user", password = "password").hasAuthentication,
        )
    }

    private fun connection(
        apiKey: String = "",
        username: String = "",
        password: String = "",
    ) = OrcaPrinterConnection(
        profileId = "profile",
        printerName = "Printer",
        host = "printer.local",
        hostType = PrinterHostType.MOONRAKER,
        apiKey = apiKey,
        username = username,
        password = password,
    )
}

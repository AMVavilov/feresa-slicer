// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.g24.feresaslicer.auth.PrinterHostType

class ManualPrinterConnectionStoreTest {
    @Test
    fun draftNormalizesAndValidatesConnection() {
        val connection = ManualPrinterConnectionDraft(
            printerName = "  KP3S  ",
            host = "192.168.1.42/",
            hostType = PrinterHostType.MOONRAKER,
            port = "7125",
            apiKey = " secret ",
        ).validatedConnection()

        assertEquals("KP3S", connection.printerName)
        assertEquals("http://192.168.1.42", connection.host)
        assertEquals("7125", connection.port)
        assertEquals("secret", connection.apiKey)
    }

    @Test
    fun draftRejectsUnsafeOrIncompleteValues() {
        assertThrows(IllegalArgumentException::class.java) {
            ManualPrinterConnectionDraft(host = "https://user:pass@printer.local").validatedConnection()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManualPrinterConnectionDraft(host = "printer.local", username = "user").validatedConnection()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManualPrinterConnectionDraft(host = "printer.local", port = "70000").validatedConnection()
        }
    }

    @Test
    fun blankPortUsesProtocolDefaultUnlessHostAlreadyContainsOne() {
        val moonraker = ManualPrinterConnectionDraft(
            host = "printer.local",
            hostType = PrinterHostType.MOONRAKER,
        ).validatedConnection()
        val octoPrint = ManualPrinterConnectionDraft(
            host = "octo.local",
            hostType = PrinterHostType.OCTOPRINT,
        ).validatedConnection()
        val explicit = ManualPrinterConnectionDraft(
            host = "http://printer.local:9999",
            hostType = PrinterHostType.MOONRAKER,
        ).validatedConnection()

        assertEquals("7125", moonraker.port)
        assertEquals("5000", octoPrint.port)
        assertEquals("", explicit.port)
        assertEquals("http://printer.local:9999", explicit.host)
    }

    @Test
    fun codecRoundTripsSecretsWithoutLoggingOrRenaming() {
        val saved = SavedManualPrinterConnection(
            connection = ManualPrinterConnectionDraft(
                printerName = "Octo",
                host = "https://octo.local",
                hostType = PrinterHostType.OCTOPRINT,
                apiKey = "key-value",
                username = "operator",
                password = "p@ss:word",
            ).validatedConnection(),
            isActive = false,
        )

        val encoded = encodeSavedPrinterConnection(saved)
        val decoded = decodeSavedPrinterConnection(encoded)

        assertFalse(decoded.isActive)
        assertEquals(saved.connection, decoded.connection)
        assertTrue(encoded.contains("key-value"))
    }
}

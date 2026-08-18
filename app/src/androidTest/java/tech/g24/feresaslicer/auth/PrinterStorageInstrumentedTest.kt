// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.g24.feresaslicer.printer.ManualPrinterConnectionDraft
import tech.g24.feresaslicer.printer.ManualPrinterConnectionStore

@RunWith(AndroidJUnit4::class)
class PrinterStorageInstrumentedTest {
    @Test
    fun manualConnectionSecretsAreEncryptedAtRest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "feresa_printer_connection_test"
        val connectionKey = "encrypted_manual_connection_test"
        val store = ManualPrinterConnectionStore(
            context = context,
            preferencesName = preferencesName,
            connectionKey = connectionKey,
            keyAlias = "feresa_slicer_printer_connection_test",
        )
        store.clear()
        val secret = "instrumented-secret-api-key"
        val connection = ManualPrinterConnectionDraft(
            printerName = "Test printer",
            host = "192.0.2.10",
            hostType = PrinterHostType.MOONRAKER,
            apiKey = secret,
        ).validatedConnection()

        try {
            store.write(connection)
            assertEquals(connection, store.read()?.connection)
            val envelope = context
                .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .getString(connectionKey, null)
            assertNotNull(envelope)
            assertFalse(envelope.orEmpty().contains(secret))
        } finally {
            store.clear()
        }
    }

    @Test
    fun cloudProfileCacheIsEncryptedAndLegacyPlaintextIsRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheFileName = "orca_profiles_cache_test.enc"
        val legacyFileName = "orca_profiles_cache_test.json"
        val cache = OrcaProfileCache(
            context = context,
            cacheFileName = cacheFileName,
            legacyCacheFileName = legacyFileName,
            keyAlias = "feresa_slicer_orca_profiles_test",
        )
        val secret = "instrumented-cloud-printer-key"
        cache.clear()
        val profile = OrcaCloudProfile(
            id = "printer-1",
            name = "Encrypted printer",
            type = OrcaProfileType.PRINTER,
            contentJson = """{"print_host":"192.0.2.20","host_type":"moonraker","printhost_apikey":"$secret"}""",
            updatedTime = 1L,
        )

        try {
            cache.write("user-1", listOf(profile), 2L)
            val encryptedFile = File(context.filesDir, cacheFileName)
            assertTrue(encryptedFile.isFile)
            assertFalse(encryptedFile.readText().contains(secret))
            assertFalse(File(context.filesDir, legacyFileName).exists())
            assertEquals(secret, cache.read()?.profiles?.single()?.setting("printhost_apikey"))
        } finally {
            cache.clear()
        }
    }
}

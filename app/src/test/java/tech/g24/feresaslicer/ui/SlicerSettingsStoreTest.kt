// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.g24.feresaslicer.auth.OrcaAccount
import tech.g24.feresaslicer.auth.OrcaAuthState
import tech.g24.feresaslicer.auth.OrcaCloudProfile
import tech.g24.feresaslicer.auth.OrcaProfileType
import tech.g24.feresaslicer.slicer.OrcaSystemPresetCatalog
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SlicerSettingsStoreTest {
    @Test
    fun serializedWriterCannotLetOlderSnapshotOverwriteLifecycleFlush() {
        val executor = Executors.newSingleThreadExecutor()
        val firstWriteStarted = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val failures = CopyOnWriteArrayList<Throwable>()
        val writes = CopyOnWriteArrayList<String>()
        val persistedValue = AtomicReference<String>()
        val callerThread = Thread.currentThread()
        val writerThread = AtomicReference<Thread>()
        val oldSettings = PersistedSlicerSettings(processProfileName = "old")
        val latestSettings = PersistedSlicerSettings(processProfileName = "latest")
        val queue = SlicerSettingsWriteQueue(
            writeSettings = { settings ->
                writerThread.compareAndSet(null, Thread.currentThread())
                if (settings.processProfileName == "old") {
                    firstWriteStarted.countDown()
                    check(releaseFirstWrite.await(5, TimeUnit.SECONDS))
                }
                writes += settings.processProfileName
                persistedValue.set(settings.processProfileName)
            },
            onFailure = failures::add,
            executor = executor,
        )

        queue.enqueue(oldSettings)
        assertTrue(firstWriteStarted.await(5, TimeUnit.SECONDS))
        queue.close(latestSettings)
        releaseFirstWrite.countDown()

        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(emptyList<Throwable>(), failures)
        assertEquals(listOf("old", "latest"), writes)
        assertEquals("latest", persistedValue.get())
        assertFalse(callerThread === writerThread.get())
    }

    @Test
    fun serializedWriterContinuesAfterFailedWrite() {
        val executor = Executors.newSingleThreadExecutor()
        val firstWriteStarted = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val failures = CopyOnWriteArrayList<Throwable>()
        val persistedValue = AtomicReference<String>()
        val queue = SlicerSettingsWriteQueue(
            writeSettings = { settings ->
                if (settings.processProfileName == "broken") {
                    firstWriteStarted.countDown()
                    check(releaseFirstWrite.await(5, TimeUnit.SECONDS))
                    error("simulated failure")
                }
                persistedValue.set(settings.processProfileName)
            },
            onFailure = failures::add,
            executor = executor,
        )

        queue.enqueue(PersistedSlicerSettings(processProfileName = "broken"))
        assertTrue(firstWriteStarted.await(5, TimeUnit.SECONDS))
        queue.close(PersistedSlicerSettings(processProfileName = "latest"))
        releaseFirstWrite.countDown()

        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, failures.size)
        assertEquals("latest", persistedValue.get())
    }

    @Test
    fun settingsAndSelectedProfilesSurviveCodecRoundTrip() {
        val filamentRef = PersistedProfileRef(
            origin = PersistedProfileOrigin.CLOUD,
            type = OrcaProfileType.FILAMENT,
            id = "filament-pla",
            name = "My PLA",
            accountId = "account-1",
        )
        val processRef = PersistedProfileRef(
            origin = PersistedProfileOrigin.SYSTEM,
            type = OrcaProfileType.PROCESS,
            id = "process-detail",
            name = "Detailed",
            contextHint = "Vendor A Printer",
        )
        val original = PersistedSlicerSettings(
            printSettings = PrintSettingsState(
                layerHeight = "0.12",
                wallLoops = "4",
                infillDensity = "37",
                enableSupport = true,
                filenameFormat = "{input_filename_base}_saved.gcode",
            ),
            dirtyProcessSettingKeys = setOf("layer_height", "wall_loops", "enable_support"),
            printDetailLevel = PrintDetailLevel.EXPERT,
            printSettingsCategory = PrintSettingsCategory.SUPPORT,
            nozzleDiameter = "0.6",
            filamentDiameter = "1.75",
            nozzleTemperature = "215",
            bedTemperature = "65",
            bedWidth = 235.0,
            bedDepth = 235.0,
            printableHeight = 280.0,
            printerFirmware = "klipper",
            printerProfileName = "Saved printer",
            filamentProfileName = filamentRef.name,
            processProfileName = processRef.name,
            printerProfileRef = PersistedProfileRef(
                origin = PersistedProfileOrigin.CLOUD,
                type = OrcaProfileType.PRINTER,
                id = "printer-1",
                name = "Saved printer",
                accountId = "account-1",
            ),
            filamentProfileRef = filamentRef,
            processProfileRef = processRef,
        )

        val encoded = encodeSlicerSettings(original)
        val restored = decodeSlicerSettings(encoded)

        assertEquals(original, restored)
        assertEquals("0.12", restored.printSettings.layerHeight)
        assertEquals("37", restored.printSettings.infillDensity)
        assertEquals(true, restored.printSettings.enableSupport)
        assertEquals(filamentRef, restored.filamentProfileRef)
        assertFalse(encoded.contains("contentJson"))
        assertFalse(encoded.contains("content_json"))
        assertFalse(encoded.contains("print_host"))
    }

    @Test
    fun filamentParentUsesSelectedPrinterToDisambiguateSystemPreset() {
        val catalog = ambiguousFilamentCatalog()
        val printer = profile(
            id = "printer-a",
            name = "Vendor A Printer",
            type = OrcaProfileType.PRINTER,
            content = """{"nozzle_diameter":"0.4"}""",
        )
        val filament = profile(
            id = "custom-pla",
            name = "My PLA",
            type = OrcaProfileType.FILAMENT,
            content = """{"inherits":"Generic PLA","nozzle_temperature":"212"}""",
        )

        val resolved = resolveProfileSettingsForUi(
            catalog = catalog,
            profile = filament,
            availableProfiles = listOf(printer, filament),
            printerContext = printer,
        )

        assertEquals("1.75", resolved["filament_diameter"])
        assertEquals("212", resolved["nozzle_temperature"])
        assertNotNull(resolved["filament_diameter"])
    }

    @Test
    fun filamentCompatibilityMetadataDisambiguatesWithoutLoadedPrinter() {
        val filament = profile(
            id = "custom-pla",
            name = "My PLA",
            type = OrcaProfileType.FILAMENT,
            content = """
                {
                  "inherits":"Generic PLA",
                  "compatible_printers":["Vendor A Printer"],
                  "nozzle_temperature":"214"
                }
            """.trimIndent(),
        )

        val resolved = resolveProfileSettingsForUi(
            catalog = ambiguousFilamentCatalog(),
            profile = filament,
            availableProfiles = listOf(filament),
        )

        assertEquals("1.75", resolved["filament_diameter"])
        assertEquals("214", resolved["nozzle_temperature"])
    }

    @Test
    fun duplicateProfileNamesUseExactIdForSelection() {
        val first = profile(
            id = "filament-a",
            name = "Generic PLA",
            type = OrcaProfileType.FILAMENT,
            content = "{}",
        )
        val second = profile(
            id = "filament-b",
            name = "Generic PLA",
            type = OrcaProfileType.FILAMENT,
            content = "{}",
        )

        assertFalse(profileMatchesSelection(first, second.id))
        assertTrue(profileMatchesSelection(second, second.id))
        assertFalse(profileMatchesSelection(first, null))
    }

    @Test
    fun cloudProfileReferenceIsScopedToSignedInAccount() {
        val selected = profile(
            id = "filament-pla",
            name = "My PLA",
            type = OrcaProfileType.FILAMENT,
            content = """{"filament_diameter":"1.75"}""",
        )
        val reference = PersistedProfileRef(
            origin = PersistedProfileOrigin.CLOUD,
            type = selected.type,
            id = selected.id,
            name = selected.name,
            accountId = "account-a",
        )

        assertSame(
            selected,
            resolvePersistedProfileRef(
                reference = reference,
                authState = signedIn("account-a"),
                cloudProfiles = listOf(selected),
                cloudProfileOwnerAccountId = "account-a",
                systemCatalog = null,
            ),
        )
        assertNull(
            resolvePersistedProfileRef(
                reference = reference,
                authState = signedIn("account-b"),
                cloudProfiles = listOf(selected),
                cloudProfileOwnerAccountId = "account-a",
                systemCatalog = null,
            ),
        )
        assertNull(
            resolvePersistedProfileRef(
                reference = reference,
                authState = OrcaAuthState.SignedOut,
                cloudProfiles = listOf(selected),
                cloudProfileOwnerAccountId = "account-a",
                systemCatalog = null,
            ),
        )
    }

    @Test
    fun cloudReferenceCapturesCurrentAccount() {
        val selected = profile(
            id = "filament-pla",
            name = "My PLA",
            type = OrcaProfileType.FILAMENT,
            content = "{}",
        )

        assertEquals(
            "account-a",
            selected.toPersistedCloudRef(
                authState = signedIn("account-a"),
                cachedOwnerAccountId = null,
            ).accountId,
        )
    }

    @Test
    fun encryptedCacheOwnerScopesOfflineProfileSelection() {
        val selected = profile(
            id = "filament-pla",
            name = "My PLA",
            type = OrcaProfileType.FILAMENT,
            content = "{}",
        )
        val reference = selected.toPersistedCloudRef(
            authState = OrcaAuthState.Error("offline"),
            cachedOwnerAccountId = "account-a",
        )

        assertSame(
            selected,
            resolvePersistedProfileRef(
                reference = reference,
                authState = OrcaAuthState.Error("offline"),
                cloudProfiles = listOf(selected),
                cloudProfileOwnerAccountId = "account-a",
                systemCatalog = null,
            ),
        )
        assertNull(
            resolvePersistedProfileRef(
                reference = reference,
                authState = OrcaAuthState.Error("offline"),
                cloudProfiles = listOf(selected),
                cloudProfileOwnerAccountId = "account-b",
                systemCatalog = null,
            ),
        )
        assertNull(
            resolvePersistedProfileRef(
                reference = reference.copy(accountId = null),
                authState = signedIn("account-a"),
                cloudProfiles = listOf(selected),
                cloudProfileOwnerAccountId = "account-a",
                systemCatalog = null,
            ),
        )
    }

    @Test
    fun activePrinterWinsOverStaleFilamentCompatibilityHint() {
        val printer = profile(
            id = "printer-a",
            name = "Vendor A Printer",
            type = OrcaProfileType.PRINTER,
            content = "{}",
        )
        val filament = profile(
            id = "custom-pla",
            name = "My PLA",
            type = OrcaProfileType.FILAMENT,
            content = """
                {
                  "inherits":"Generic PLA",
                  "compatible_printers":["Vendor B Printer"]
                }
            """.trimIndent(),
        )

        val resolved = resolveProfileSettingsForUi(
            catalog = ambiguousFilamentCatalog(),
            profile = filament,
            availableProfiles = listOf(printer, filament),
            printerContext = printer,
        )

        assertEquals("1.75", resolved["filament_diameter"])
        assertEquals("205", resolved["nozzle_temperature"])
    }

    @Test
    fun systemProfileReferenceRestoresByExactCatalogId() {
        val catalog = ambiguousFilamentCatalog()
        val selected = catalog.bundledProfile(
            type = OrcaProfileType.FILAMENT,
            name = "Generic PLA",
            contextHint = "Vendor A",
        )
        val reference = PersistedProfileRef(
            origin = PersistedProfileOrigin.SYSTEM,
            type = selected.type,
            id = selected.id,
            name = selected.name,
            contextHint = "Vendor B",
        )

        val restored = resolvePersistedProfileRef(
            reference = reference,
            authState = OrcaAuthState.SignedOut,
            cloudProfiles = emptyList(),
            cloudProfileOwnerAccountId = null,
            systemCatalog = catalog,
        )

        assertEquals(selected.id, restored?.id)
        assertEquals("1.75", restored?.setting("filament_diameter"))
    }

    private fun ambiguousFilamentCatalog() = OrcaSystemPresetCatalog.fromIniBundles(
        mapOf(
            "vendor-a.ini" to """
                [vendor]
                name = Vendor A
                [printer:Vendor A Printer]
                nozzle_diameter = 0.4
                [filament:Generic PLA]
                filament_diameter = 1.75
                nozzle_temperature = 205
            """.trimIndent(),
            "vendor-b.ini" to """
                [vendor]
                name = Vendor B
                [printer:Vendor B Printer]
                nozzle_diameter = 0.4
                [filament:Generic PLA]
                filament_diameter = 2.85
                nozzle_temperature = 235
            """.trimIndent(),
        ),
    )

    private fun profile(
        id: String,
        name: String,
        type: OrcaProfileType,
        content: String,
    ) = OrcaCloudProfile(
        id = id,
        name = name,
        type = type,
        contentJson = content,
        updatedTime = 1L,
    )

    private fun signedIn(accountId: String) = OrcaAuthState.SignedIn(
        OrcaAccount(
            id = accountId,
            email = "$accountId@example.com",
            displayName = accountId,
        ),
    )
}

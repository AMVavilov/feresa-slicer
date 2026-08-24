// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import tech.g24.feresaslicer.auth.AndroidKeystoreAesGcm
import tech.g24.feresaslicer.auth.OrcaProfileType
import tech.g24.feresaslicer.slicer.OrcaProcessSettingsPayload
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal enum class PersistedProfileOrigin {
    CLOUD,
    SYSTEM,
}

/** A non-secret reference; cloud profile content remains solely in the encrypted Orca cache. */
internal data class PersistedProfileRef(
    val origin: PersistedProfileOrigin,
    val type: OrcaProfileType,
    val id: String,
    val name: String,
    val accountId: String? = null,
    val contextHint: String? = null,
)

internal data class PersistedSlicerSettings(
    val printSettings: PrintSettingsState = PrintSettingsState(),
    val dirtyProcessSettingKeys: Set<String> = emptySet(),
    val printDetailLevel: PrintDetailLevel = PrintDetailLevel.ADVANCED,
    val printSettingsCategory: PrintSettingsCategory = PrintSettingsCategory.QUALITY,
    val nozzleDiameter: String = "0.40",
    val filamentDiameter: String = "1.75",
    val nozzleTemperature: String = "210",
    val bedTemperature: String = "60",
    val bedWidth: Double = 220.0,
    val bedDepth: Double = 220.0,
    val printableHeight: Double = 250.0,
    val printerFirmware: String = "marlin",
    val printerProfileName: String = "Generic 220",
    val filamentProfileName: String = "Generic PLA",
    val processProfileName: String = "Standard quality",
    val printerProfileRef: PersistedProfileRef? = null,
    val filamentProfileRef: PersistedProfileRef? = null,
    val processProfileRef: PersistedProfileRef? = null,
)

/** Stores all visible slicer settings plus non-secret profile references across app restarts. */
internal class SlicerSettingsStore(
    context: Context,
    preferencesName: String = PREFERENCES,
    private val settingsKey: String = KEY_SETTINGS,
    keyAlias: String = KEY_ALIAS,
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val cipher = AndroidKeystoreAesGcm(keyAlias)

    fun read(): PersistedSlicerSettings {
        val envelope = preferences.getString(settingsKey, null) ?: return PersistedSlicerSettings()
        return runCatching {
            decodeSlicerSettings(String(cipher.decrypt(envelope), Charsets.UTF_8))
        }.getOrElse { PersistedSlicerSettings() }
    }

    fun write(settings: PersistedSlicerSettings) {
        val encoded = encodeSlicerSettings(settings).toByteArray(Charsets.UTF_8)
        check(preferences.edit().putString(settingsKey, cipher.encrypt(encoded)).commit()) {
            "Could not persist slicer settings"
        }
    }

    private companion object {
        const val PREFERENCES = "feresa_slicer_settings"
        const val KEY_SETTINGS = "encrypted_slicer_settings"
        const val KEY_ALIAS = "feresa_slicer_settings"
    }
}

/**
 * Serializes encrypted settings writes away from the caller thread.
 *
 * While a write is running, intermediate snapshots are coalesced to the newest one. This keeps
 * an older background write from completing after a newer lifecycle flush and restoring stale
 * settings. Closing the queue accepts one final snapshot and lets the already scheduled worker
 * drain it before the executor shuts down.
 */
internal class SlicerSettingsWriteQueue(
    private val writeSettings: (PersistedSlicerSettings) -> Unit,
    private val onFailure: (Throwable) -> Unit = {},
    private val executor: ExecutorService = newSlicerSettingsExecutor(),
) : AutoCloseable {
    private val monitor = Any()
    private var pendingSettings: PersistedSlicerSettings? = null
    private var workerScheduled = false
    private var closed = false

    fun enqueue(settings: PersistedSlicerSettings): Boolean = synchronized(monitor) {
        if (closed) return@synchronized false
        pendingSettings = settings
        scheduleWorkerLocked()
        true
    }

    fun close(finalSettings: PersistedSlicerSettings) {
        synchronized(monitor) {
            if (closed) return
            pendingSettings = finalSettings
            closed = true
            scheduleWorkerLocked()
            executor.shutdown()
        }
    }

    override fun close() {
        synchronized(monitor) {
            if (closed) return
            closed = true
            executor.shutdown()
        }
    }

    private fun scheduleWorkerLocked() {
        if (workerScheduled) return
        workerScheduled = true
        executor.execute(::drainWrites)
    }

    private fun drainWrites() {
        while (true) {
            val settings = synchronized(monitor) {
                pendingSettings.also { pendingSettings = null }
                    ?: run {
                        workerScheduled = false
                        null
                    }
            } ?: return
            runCatching { writeSettings(settings) }.onFailure(onFailure)
        }
    }
}

private fun newSlicerSettingsExecutor(): ExecutorService = Executors.newSingleThreadExecutor { task ->
    Thread(task, "Feresa-settings-writer").apply { isDaemon = true }
}

internal fun encodeSlicerSettings(settings: PersistedSlicerSettings): String = JSONObject()
    .put("schema", 1)
    .put("print_settings", JSONObject(settings.printSettings.toOrcaProcessSettingsPayload().toCanonicalJson()))
    .put("dirty_process_keys", JSONArray(settings.dirtyProcessSettingKeys.sorted()))
    .put("detail_level", settings.printDetailLevel.name)
    .put("settings_category", settings.printSettingsCategory.name)
    .put("nozzle_diameter", settings.nozzleDiameter)
    .put("filament_diameter", settings.filamentDiameter)
    .put("nozzle_temperature", settings.nozzleTemperature)
    .put("bed_temperature", settings.bedTemperature)
    .put("bed_width", settings.bedWidth)
    .put("bed_depth", settings.bedDepth)
    .put("printable_height", settings.printableHeight)
    .put("printer_firmware", settings.printerFirmware)
    .put("printer_profile_name", settings.printerProfileName)
    .put("filament_profile_name", settings.filamentProfileName)
    .put("process_profile_name", settings.processProfileName)
    .putProfileRef("printer_profile", settings.printerProfileRef)
    .putProfileRef("filament_profile", settings.filamentProfileRef)
    .putProfileRef("process_profile", settings.processProfileRef)
    .toString()

internal fun decodeSlicerSettings(json: String): PersistedSlicerSettings {
    val root = JSONObject(json)
    require(root.optInt("schema", 1) == 1) { "Unsupported slicer settings schema" }
    val processValues = root.optJSONObject("print_settings")
        ?.let { OrcaProcessSettingsPayload.parse(it.toString()).asMap() }
        .orEmpty()
        .mapNotNull { (key, value) -> value?.toString()?.let { key to it } }
        .toMap()
    val defaults = PersistedSlicerSettings()
    return PersistedSlicerSettings(
        printSettings = defaults.printSettings.applyOrcaSettings(processValues),
        dirtyProcessSettingKeys = root.optJSONArray("dirty_process_keys").toStringSet(),
        printDetailLevel = root.enumValue("detail_level", defaults.printDetailLevel),
        printSettingsCategory = root.enumValue("settings_category", defaults.printSettingsCategory),
        nozzleDiameter = root.stringValue("nozzle_diameter", defaults.nozzleDiameter),
        filamentDiameter = root.stringValue("filament_diameter", defaults.filamentDiameter),
        nozzleTemperature = root.stringValue("nozzle_temperature", defaults.nozzleTemperature),
        bedTemperature = root.stringValue("bed_temperature", defaults.bedTemperature),
        bedWidth = root.positiveDouble("bed_width", defaults.bedWidth),
        bedDepth = root.positiveDouble("bed_depth", defaults.bedDepth),
        printableHeight = root.positiveDouble("printable_height", defaults.printableHeight),
        printerFirmware = root.stringValue("printer_firmware", defaults.printerFirmware),
        printerProfileName = root.stringValue("printer_profile_name", defaults.printerProfileName),
        filamentProfileName = root.stringValue("filament_profile_name", defaults.filamentProfileName),
        processProfileName = root.stringValue("process_profile_name", defaults.processProfileName),
        printerProfileRef = root.optProfileRef("printer_profile"),
        filamentProfileRef = root.optProfileRef("filament_profile"),
        processProfileRef = root.optProfileRef("process_profile"),
    )
}

private fun JSONObject.putProfileRef(key: String, profile: PersistedProfileRef?): JSONObject = apply {
    put(
        key,
        profile?.let {
            JSONObject()
                .put("origin", it.origin.name)
                .put("id", it.id)
                .put("name", it.name)
                .put("type", it.type.wireValue)
                .put("account_id", it.accountId)
                .put("context_hint", it.contextHint)
        },
    )
}

private fun JSONObject.optProfileRef(key: String): PersistedProfileRef? = optJSONObject(key)?.let { profile ->
    PersistedProfileRef(
        origin = runCatching {
            PersistedProfileOrigin.valueOf(profile.getString("origin"))
        }.getOrElse { error("Unknown persisted profile origin") },
        type = OrcaProfileType.fromWire(profile.getString("type")),
        id = profile.getString("id"),
        name = profile.getString("name"),
        accountId = profile.nullableString("account_id"),
        contextHint = profile.nullableString("context_hint"),
    )
}

private fun JSONArray?.toStringSet(): Set<String> = if (this == null) {
    emptySet()
} else {
    buildSet {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}

private inline fun <reified T : Enum<T>> JSONObject.enumValue(key: String, fallback: T): T =
    runCatching { enumValueOf<T>(optString(key, fallback.name)) }.getOrDefault(fallback)

private fun JSONObject.stringValue(key: String, fallback: String): String =
    if (has(key) && !isNull(key)) getString(key) else fallback

private fun JSONObject.nullableString(key: String): String? =
    if (has(key) && !isNull(key)) getString(key).takeIf(String::isNotBlank) else null

private fun JSONObject.positiveDouble(key: String, fallback: Double): Double =
    optDouble(key, fallback).takeIf { it.isFinite() && it > 0.0 } ?: fallback

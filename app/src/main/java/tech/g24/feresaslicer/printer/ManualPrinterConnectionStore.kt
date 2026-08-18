// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.printer

import android.content.Context
import org.json.JSONObject
import tech.g24.feresaslicer.auth.AndroidKeystoreAesGcm
import tech.g24.feresaslicer.auth.OrcaPrinterConnection
import tech.g24.feresaslicer.auth.PrinterHostType
import java.net.URI

data class ManualPrinterConnectionDraft(
    val printerName: String = "Мой принтер",
    val host: String = "",
    val hostType: PrinterHostType = PrinterHostType.MOONRAKER,
    val port: String = "",
    val apiKey: String = "",
    val username: String = "",
    val password: String = "",
) {
    fun validatedConnection(): OrcaPrinterConnection {
        val normalizedName = printerName.trim().ifBlank { "Мой принтер" }
        val normalizedHost = normalizePrinterHost(host)
        require(hostType.canSendGcode) { "Выберите Moonraker или OctoPrint" }
        require(port.isBlank() || port.toIntOrNull() in 1..65535) { "Порт должен быть от 1 до 65535" }
        val normalizedPort = port.trim().ifBlank {
            if (URI(normalizedHost).port >= 0) "" else hostType.defaultPrinterPort()
        }
        require((username.isBlank() && password.isBlank()) || (username.isNotBlank() && password.isNotBlank())) {
            "Для Basic Auth нужны и логин, и пароль"
        }
        listOf(normalizedName, normalizedHost, normalizedPort, apiKey, username, password).forEach { value ->
            require(value.none { it == '\r' || it == '\n' }) { "Поля подключения не должны содержать переносы строк" }
        }
        return OrcaPrinterConnection(
            profileId = MANUAL_PROFILE_ID,
            printerName = normalizedName,
            host = normalizedHost,
            hostType = hostType,
            port = normalizedPort,
            apiKey = apiKey.trim(),
            username = username.trim(),
            password = password,
        )
    }

    companion object {
        fun from(connection: OrcaPrinterConnection?): ManualPrinterConnectionDraft = ManualPrinterConnectionDraft(
            printerName = connection?.printerName.orEmpty().ifBlank { "Мой принтер" },
            host = connection?.host.orEmpty(),
            hostType = connection?.hostType?.takeIf(PrinterHostType::canSendGcode) ?: PrinterHostType.MOONRAKER,
            port = connection?.port.orEmpty(),
            apiKey = connection?.apiKey.orEmpty(),
            username = connection?.username.orEmpty(),
            password = connection?.password.orEmpty(),
        )
    }
}

data class SavedManualPrinterConnection(
    val connection: OrcaPrinterConnection,
    val isActive: Boolean,
)

internal class ManualPrinterConnectionStore(
    context: Context,
    preferencesName: String = PREFERENCES,
    private val connectionKey: String = KEY_CONNECTION,
    keyAlias: String = KEY_ALIAS,
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val cipher = AndroidKeystoreAesGcm(keyAlias)

    fun read(): SavedManualPrinterConnection? {
        val envelope = preferences.getString(connectionKey, null) ?: return null
        return runCatching {
            decodeSavedPrinterConnection(String(cipher.decrypt(envelope), Charsets.UTF_8))
        }.getOrElse {
            clear()
            null
        }
    }

    fun write(connection: OrcaPrinterConnection, isActive: Boolean = true) {
        val json = encodeSavedPrinterConnection(SavedManualPrinterConnection(connection, isActive))
        preferences.edit()
            .putString(connectionKey, cipher.encrypt(json.toByteArray(Charsets.UTF_8)))
            .apply()
    }

    fun setActive(isActive: Boolean) {
        val saved = read() ?: return
        write(saved.connection, isActive)
    }

    fun clear() {
        preferences.edit().remove(connectionKey).apply()
    }

    private companion object {
        const val PREFERENCES = "feresa_printer_connection"
        const val KEY_CONNECTION = "encrypted_manual_connection"
        const val KEY_ALIAS = "feresa_slicer_printer_connection"
    }
}

internal fun encodeSavedPrinterConnection(saved: SavedManualPrinterConnection): String = JSONObject()
    .put("active", saved.isActive)
    .put("profile_id", saved.connection.profileId)
    .put("printer_name", saved.connection.printerName)
    .put("host", saved.connection.host)
    .put("host_type", saved.connection.hostType.name)
    .put("port", saved.connection.port)
    .put("api_key", saved.connection.apiKey)
    .put("username", saved.connection.username)
    .put("password", saved.connection.password)
    .toString()

internal fun decodeSavedPrinterConnection(json: String): SavedManualPrinterConnection {
    val root = JSONObject(json)
    val type = runCatching { PrinterHostType.valueOf(root.getString("host_type")) }
        .getOrElse { error("Unknown printer protocol") }
    val connection = OrcaPrinterConnection(
        profileId = root.optString("profile_id").ifBlank { MANUAL_PROFILE_ID },
        printerName = root.optString("printer_name").ifBlank { "Мой принтер" },
        host = root.getString("host"),
        hostType = type,
        port = root.optString("port"),
        apiKey = root.optString("api_key"),
        username = root.optString("username"),
        password = root.optString("password"),
    )
    return SavedManualPrinterConnection(connection, root.optBoolean("active", true))
}

internal fun normalizePrinterHost(value: String): String {
    val raw = value.trim().trimEnd('/')
    require(raw.isNotBlank()) { "Укажите IP-адрес или имя принтера" }
    val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
        raw
    } else {
        "http://$raw"
    }
    val uri = runCatching { URI(withScheme) }.getOrElse { error("Некорректный адрес принтера") }
    require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
        "Поддерживаются только HTTP и HTTPS"
    }
    require(!uri.host.isNullOrBlank()) { "Некорректный адрес принтера" }
    require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "В адресе принтера не должно быть логина, параметров или фрагмента"
    }
    require(uri.path.isNullOrBlank() || uri.path == "/") { "Укажите адрес сервера без пути" }
    return URI(uri.scheme.lowercase(), null, uri.host, uri.port, null, null, null).toString().trimEnd('/')
}

private const val MANUAL_PROFILE_ID = "manual-local-printer"

private fun PrinterHostType.defaultPrinterPort(): String = when (this) {
    PrinterHostType.MOONRAKER -> "7125"
    PrinterHostType.OCTOPRINT -> "5000"
    else -> ""
}

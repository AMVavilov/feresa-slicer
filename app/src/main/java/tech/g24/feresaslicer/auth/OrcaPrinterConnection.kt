// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

enum class PrinterHostType(
    val label: String,
    val canSendGcode: Boolean,
) {
    MOONRAKER("Moonraker / Klipper", true),
    OCTOPRINT("OctoPrint", true),
    PRUSALINK("PrusaLink", false),
    UNKNOWN("Неизвестный протокол", false),
}

data class OrcaPrinterConnection(
    val profileId: String,
    val printerName: String,
    val host: String,
    val hostType: PrinterHostType,
    val apiKey: String = "",
    val port: String = "",
    val webUi: String = "",
    val username: String = "",
    val password: String = "",
) {
    val hasAuthentication: Boolean
        get() = apiKey.isNotBlank() || (username.isNotBlank() && password.isNotBlank())
}

fun OrcaCloudProfile.printerConnection(): OrcaPrinterConnection? {
    if (type != OrcaProfileType.PRINTER) return null
    val host = setting("print_host")?.trim().orEmpty()
    if (host.isBlank()) return null

    val rawHostType = setting("host_type")?.trim()?.lowercase().orEmpty()
    val hostType = when (rawHostType) {
        "moonraker", "klipper", "15" -> PrinterHostType.MOONRAKER
        "octoprint", "octo_print", "2" -> PrinterHostType.OCTOPRINT
        "prusalink", "prusa_link", "0" -> PrinterHostType.PRUSALINK
        else -> PrinterHostType.UNKNOWN
    }
    return OrcaPrinterConnection(
        profileId = id,
        printerName = name,
        host = host,
        hostType = hostType,
        apiKey = setting("printhost_apikey").orEmpty(),
        port = setting("printhost_port").orEmpty(),
        webUi = setting("print_host_webui").orEmpty(),
        username = setting("printhost_user").orEmpty(),
        password = setting("printhost_password").orEmpty(),
    )
}

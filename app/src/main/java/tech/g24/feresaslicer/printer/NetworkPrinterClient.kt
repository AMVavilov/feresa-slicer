// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.printer

import org.json.JSONObject
import tech.g24.feresaslicer.auth.OrcaPrinterConnection
import tech.g24.feresaslicer.auth.PrinterHostType
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

data class PrinterSendReceipt(
    val remotePath: String,
    val printStarted: Boolean,
)

object NetworkPrinterClient {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000

    fun test(connection: OrcaPrinterConnection): String = when (connection.hostType) {
        PrinterHostType.MOONRAKER -> {
            val response = request(connection, "GET", "server/info")
            val state = JSONObject(response).optJSONObject("result")?.optString("klippy_state")
                ?.takeIf(String::isNotBlank)
                ?: error("Сервер ответил, но это не похоже на Moonraker")
            "Moonraker доступен · Klipper: $state"
        }
        PrinterHostType.OCTOPRINT -> {
            val response = request(connection, "GET", "api/version")
            val server = JSONObject(response).optString("server").ifBlank { "OctoPrint" }
            "$server доступен"
        }
        PrinterHostType.PRUSALINK -> error("Отправка в PrusaLink пока не поддерживается")
        PrinterHostType.UNKNOWN -> error("Неизвестный тип сервера печати")
    }

    fun uploadAndStart(
        connection: OrcaPrinterConnection,
        gcodeFile: File,
        requestedName: String,
    ): PrinterSendReceipt {
        require(gcodeFile.isFile && gcodeFile.length() > 0L) { "G-code файл не найден" }
        require(connection.hostType.canSendGcode) { "Этот протокол отправки пока не поддерживается" }
        val remoteName = sanitizeFilename(requestedName)
        return when (connection.hostType) {
            PrinterHostType.MOONRAKER -> uploadToMoonraker(connection, gcodeFile, remoteName)
            PrinterHostType.OCTOPRINT -> uploadToOctoPrint(connection, gcodeFile, remoteName)
            else -> error("Этот протокол отправки пока не поддерживается")
        }
    }

    private fun uploadToMoonraker(
        connection: OrcaPrinterConnection,
        source: File,
        remoteName: String,
    ): PrinterSendReceipt {
        test(connection)
        val response = multipart(
            connection = connection,
            path = "server/files/upload",
            fields = mapOf("root" to "gcodes"),
            source = source,
            remoteName = remoteName,
        )
        val uploadedPath = JSONObject(response)
            .optJSONObject("result")
            ?.optJSONObject("item")
            ?.optString("path")
            ?.takeIf(String::isNotBlank)
            ?: remoteName
        request(
            connection = connection,
            method = "POST",
            path = "printer/print/start",
            body = JSONObject().put("filename", uploadedPath).toString(),
        )
        return PrinterSendReceipt(uploadedPath, printStarted = true)
    }

    private fun uploadToOctoPrint(
        connection: OrcaPrinterConnection,
        source: File,
        remoteName: String,
    ): PrinterSendReceipt {
        test(connection)
        multipart(
            connection = connection,
            path = "api/files/local",
            fields = mapOf("select" to "true", "print" to "true"),
            source = source,
            remoteName = remoteName,
        )
        return PrinterSendReceipt(remoteName, printStarted = true)
    }

    private fun request(
        connection: OrcaPrinterConnection,
        method: String,
        path: String,
        body: String? = null,
    ): String {
        val http = openConnection(connection, path).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) {
                http.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            readResponse(http)
        } finally {
            http.disconnect()
        }
    }

    private fun multipart(
        connection: OrcaPrinterConnection,
        path: String,
        fields: Map<String, String>,
        source: File,
        remoteName: String,
    ): String {
        val boundary = "FeresaSlicer-${UUID.randomUUID()}"
        val http = openConnection(connection, path).apply {
            requestMethod = "POST"
            doOutput = true
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        return try {
            BufferedOutputStream(http.outputStream).use { output ->
                fields.forEach { (name, value) ->
                    output.write("--$boundary\r\n".toByteArray())
                    output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    output.write(value.toByteArray(Charsets.UTF_8))
                    output.write("\r\n".toByteArray())
                }
                output.write("--$boundary\r\n".toByteArray())
                output.write(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"$remoteName\"\r\n".toByteArray(),
                )
                output.write("Content-Type: text/x.gcode\r\n\r\n".toByteArray())
                source.inputStream().use { it.copyTo(output) }
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            readResponse(http)
        } finally {
            http.disconnect()
        }
    }

    private fun openConnection(connection: OrcaPrinterConnection, path: String): HttpURLConnection {
        val http = URL("${baseUrl(connection)}/${path.trimStart('/')}").openConnection() as HttpURLConnection
        http.connectTimeout = CONNECT_TIMEOUT_MS
        http.readTimeout = READ_TIMEOUT_MS
        http.useCaches = false
        if (connection.apiKey.isNotBlank()) {
            val header = if (connection.hostType == PrinterHostType.MOONRAKER) "X-Api-Key" else "X-Api-Key"
            http.setRequestProperty(header, connection.apiKey)
        }
        return http
    }

    private fun baseUrl(connection: OrcaPrinterConnection): String {
        val raw = connection.host.trim().trimEnd('/')
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        val uri = URI(withScheme)
        require(uri.scheme == "http" || uri.scheme == "https") { "Поддерживаются только HTTP и HTTPS" }
        require(!uri.host.isNullOrBlank()) { "Некорректный адрес принтера" }
        if (connection.port.isBlank() || uri.port != -1) return withScheme
        val port = connection.port.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: error("Некорректный порт принтера")
        return URI(uri.scheme, uri.userInfo, uri.host, port, uri.path, uri.query, uri.fragment).toString().trimEnd('/')
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val message = runCatching {
                val json = JSONObject(response)
                json.optJSONObject("error")?.optString("message")
                    ?.takeIf(String::isNotBlank)
                    ?: json.optString("message").takeIf(String::isNotBlank)
            }.getOrNull() ?: response.take(300).ifBlank { "HTTP $status" }
            error("Принтер отклонил запрос: $message")
        }
        return response
    }

    private fun sanitizeFilename(value: String): String {
        val cleaned = value
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.')
            .take(120)
            .ifBlank { "feresa-slicer.gcode" }
        return if (cleaned.lowercase().endsWith(".gcode")) cleaned else "$cleaned.gcode"
    }
}

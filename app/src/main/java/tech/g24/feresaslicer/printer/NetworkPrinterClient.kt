// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.printer

import org.json.JSONObject
import tech.g24.feresaslicer.auth.OrcaPrinterConnection
import tech.g24.feresaslicer.auth.PrinterHostType
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

data class PrinterUploadReceipt(
    val remotePath: String,
)

data class PrinterSendReceipt(
    val remotePath: String,
    val printStarted: Boolean,
)

object NetworkPrinterClient {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_RESPONSE_BYTES = 1024 * 1024

    fun test(connection: OrcaPrinterConnection): String = when (
        val result = PrinterConnectionService.test(connection)
    ) {
        is PrinterConnectionTestResult.Connected -> when (val status = result.status) {
            is PrinterStatus.Moonraker -> "Moonraker доступен · Klipper: ${status.klippyState}"
            is PrinterStatus.OctoPrint -> "OctoPrint ${status.serverVersion} доступен"
        }
        is PrinterConnectionTestResult.Failed -> error(result.failure.userMessage)
    }

    fun uploadAndStart(
        connection: OrcaPrinterConnection,
        gcodeFile: File,
        requestedName: String,
    ): PrinterSendReceipt {
        val upload = upload(connection, gcodeFile, requestedName)
        return start(connection, upload.remotePath)
    }

    fun upload(
        connection: OrcaPrinterConnection,
        gcodeFile: File,
        requestedName: String,
    ): PrinterUploadReceipt {
        require(gcodeFile.isFile && gcodeFile.length() > 0L) { "G-code файл не найден" }
        require(connection.hostType.canSendGcode) { "Этот протокол отправки пока не поддерживается" }
        val remoteName = sanitizeFilename(requestedName)
        return when (connection.hostType) {
            PrinterHostType.MOONRAKER -> uploadToMoonraker(connection, gcodeFile, remoteName)
            PrinterHostType.OCTOPRINT -> uploadToOctoPrint(connection, gcodeFile, remoteName)
            else -> error("Этот протокол отправки пока не поддерживается")
        }
    }

    fun start(
        connection: OrcaPrinterConnection,
        remotePath: String,
    ): PrinterSendReceipt {
        require(connection.hostType.canSendGcode) { "Этот протокол отправки пока не поддерживается" }
        val safeRemotePath = validateRemotePath(remotePath)
        when (connection.hostType) {
            PrinterHostType.MOONRAKER -> request(
                connection = connection,
                method = "POST",
                path = "printer/print/start",
                body = JSONObject().put("filename", safeRemotePath).toString(),
            )
            PrinterHostType.OCTOPRINT -> request(
                connection = connection,
                method = "POST",
                path = "api/files/local/${encodeRemotePath(safeRemotePath)}",
                body = JSONObject()
                    .put("command", "select")
                    .put("print", true)
                    .toString(),
            )
            else -> error("Этот протокол отправки пока не поддерживается")
        }
        return PrinterSendReceipt(safeRemotePath, printStarted = true)
    }

    private fun uploadToMoonraker(
        connection: OrcaPrinterConnection,
        source: File,
        remoteName: String,
    ): PrinterUploadReceipt {
        val response = multipart(
            connection = connection,
            path = "server/files/upload",
            fields = mapOf("root" to "gcodes"),
            source = source,
            remoteName = remoteName,
        )
        val uploadedPath = runCatching {
            val json = JSONObject(response)
            val payload = json.optJSONObject("result") ?: json
            payload.optJSONObject("item")
                ?.optString("path")
                ?.takeIf(String::isNotBlank)
        }.getOrNull() ?: error("Сервер принтера не вернул путь загруженного файла")
        return PrinterUploadReceipt(validateRemotePath(uploadedPath))
    }

    private fun uploadToOctoPrint(
        connection: OrcaPrinterConnection,
        source: File,
        remoteName: String,
    ): PrinterUploadReceipt {
        val response = multipart(
            connection = connection,
            path = "api/files/local",
            fields = mapOf("select" to "false", "print" to "false"),
            source = source,
            remoteName = remoteName,
        )
        val uploadedPath = runCatching {
            JSONObject(response)
                .optJSONObject("files")
                ?.optJSONObject("local")
                ?.optString("path")
                ?.takeIf(String::isNotBlank)
        }.getOrNull() ?: error("Сервер принтера не вернул путь загруженного файла")
        return PrinterUploadReceipt(validateRemotePath(uploadedPath))
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
                setFixedLengthStreamingMode(body.toByteArray(Charsets.UTF_8).size)
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
        val prefix = multipartPrefix(boundary, fields, remoteName)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val contentLength = prefix.size.toLong() + source.length() + suffix.size.toLong()
        val http = openConnection(connection, path).apply {
            requestMethod = "POST"
            doOutput = true
            setFixedLengthStreamingMode(contentLength)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        return try {
            BufferedOutputStream(http.outputStream).use { output ->
                output.write(prefix)
                source.inputStream().use { it.copyTo(output) }
                output.write(suffix)
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
        http.instanceFollowRedirects = false
        http.useCaches = false
        printerAuthenticationHeaders(
            apiKey = connection.apiKey,
            username = connection.username,
            password = connection.password,
        ).forEach { (name, value) ->
            http.setRequestProperty(name, value)
        }
        return http
    }

    private fun baseUrl(connection: OrcaPrinterConnection): String {
        return printerBaseUrl(connection)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.use(::readBoundedUtf8).orEmpty()
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

    private fun multipartPrefix(
        boundary: String,
        fields: Map<String, String>,
        remoteName: String,
    ): ByteArray = ByteArrayOutputStream().use { output ->
        fields.forEach { (name, value) ->
            output.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
            output.write(
                "Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray(Charsets.UTF_8),
            )
            output.write(value.toByteArray(Charsets.UTF_8))
            output.write("\r\n".toByteArray(Charsets.UTF_8))
        }
        output.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
        output.write(
            "Content-Disposition: form-data; name=\"file\"; filename=\"$remoteName\"\r\n"
                .toByteArray(Charsets.UTF_8),
        )
        output.write("Content-Type: text/x.gcode\r\n\r\n".toByteArray(Charsets.UTF_8))
        output.toByteArray()
    }

    private fun readBoundedUtf8(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > MAX_RESPONSE_BYTES) {
                throw IOException("Ответ принтера слишком большой")
            }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun validateRemotePath(value: String): String {
        val path = value.trim().trimStart('/')
        require(path.isNotBlank() && path.length <= 512) { "Некорректный путь файла на принтере" }
        require(path.none { it == '\u0000' || it == '\r' || it == '\n' || it == '\\' }) {
            "Некорректный путь файла на принтере"
        }
        require(path.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Некорректный путь файла на принтере"
        }
        return path
    }

    private fun encodeRemotePath(path: String): String = path.split('/').joinToString("/") { segment ->
        URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
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

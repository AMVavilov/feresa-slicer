// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.auth

import android.net.Uri
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

internal data class OrcaAuthCallback(
    val code: String,
    val state: String,
)

internal class OrcaLoopbackServer private constructor(
    private val socket: ServerSocket,
) : Closeable {
    val redirectUri: String = "http://localhost:${socket.localPort}/callback"

    fun awaitCallback(): OrcaAuthCallback {
        socket.soTimeout = CALLBACK_TIMEOUT_MILLIS
        val client = try {
            socket.accept()
        } catch (_: SocketTimeoutException) {
            error("OrcaCloud sign-in timed out")
        }

        return client.use { connection ->
            connection.soTimeout = REQUEST_TIMEOUT_MILLIS
            val requestLine = connection.getInputStream().bufferedReader().readLine().orEmpty()
            val target = requestLine.split(' ').getOrNull(1).orEmpty()
            val uri = Uri.parse(if (target.startsWith("http")) target else "http://localhost$target")
            val errorDescription = uri.getQueryParameter("error_description")
                ?: uri.getQueryParameter("error")
            val code = uri.getQueryParameter("code").orEmpty()
            val state = uri.getQueryParameter("orca_state")
                ?: uri.getQueryParameter("state").orEmpty()

            val success = uri.path == "/callback" && errorDescription == null && code.isNotBlank()
            val title = if (success) "Authorization received" else "Authorization failed"
            val message = if (success) {
                "Return to Feresa Slicer to finish signing in."
            } else {
                "Return to Feresa Slicer and try again."
            }
            val html = """
                <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
                <title>$title</title></head><body style="font-family:sans-serif;padding:32px;background:#f6f7f2;color:#18332b">
                <h2>$title</h2><p>$message</p></body></html>
            """.trimIndent()
            val response = "HTTP/1.1 ${if (success) "200 OK" else "400 Bad Request"}\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${html.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n$html"
            connection.getOutputStream().write(response.toByteArray())
            connection.getOutputStream().flush()

            require(errorDescription == null) { errorDescription ?: "OrcaCloud rejected sign-in" }
            require(uri.path == "/callback" && code.isNotBlank()) { "Invalid OrcaCloud callback" }
            OrcaAuthCallback(code, state)
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        private const val CALLBACK_TIMEOUT_MILLIS = 600_000
        private const val REQUEST_TIMEOUT_MILLIS = 10_000

        fun open(): OrcaLoopbackServer {
            for (port in 41172..41174) {
                val socket = ServerSocket()
                val result = runCatching {
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(InetAddress.getByName("localhost"), port), 1)
                    OrcaLoopbackServer(socket)
                }
                result.getOrNull()?.let { return it }
                runCatching { socket.close() }
            }
            error("Cannot open the OrcaCloud callback port")
        }
    }
}

// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.printer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/** Minimal HTTP/1.1 server for JVM tests without relying on non-Java-SE JDK modules. */
internal class HttpServer private constructor(
    private val serverSocket: ServerSocket,
) {
    private val contexts = linkedMapOf<String, (HttpExchange) -> Unit>()
    private val running = AtomicBoolean(false)
    private var serverThread: Thread? = null

    val address = InetSocketAddress(serverSocket.inetAddress, serverSocket.localPort)

    fun createContext(path: String, handler: (HttpExchange) -> Unit) {
        check(!running.get()) { "Contexts must be registered before the server starts" }
        contexts[path] = handler
    }

    fun start() {
        check(running.compareAndSet(false, true)) { "Server already started" }
        serverThread = Thread({ acceptLoop() }, "feresa-test-http").apply {
            isDaemon = true
            start()
        }
    }

    fun stop(delaySeconds: Int) {
        @Suppress("UNUSED_VARIABLE")
        val ignoredDelay = delaySeconds
        running.set(false)
        runCatching { serverSocket.close() }
        serverThread?.join(2_000)
    }

    private fun acceptLoop() {
        while (running.get()) {
            val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
            socket.use(::handle)
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val input = socket.getInputStream()
        val requestLine = input.readHttpLine() ?: return
        val requestParts = requestLine.split(' ', limit = 3)
        if (requestParts.size < 2) return

        val requestHeaders = HttpHeaders()
        while (true) {
            val line = input.readHttpLine() ?: return
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                requestHeaders.add(
                    line.substring(0, separator).trim(),
                    line.substring(separator + 1).trim(),
                )
            }
        }
        val contentLength = requestHeaders.getFirst("Content-Length")?.toIntOrNull() ?: 0
        val requestBody = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(requestBody, offset, contentLength - offset)
            if (read < 0) break
            offset += read
        }

        val uri = URI.create(requestParts[1])
        val exchange = HttpExchange(
            requestURI = uri,
            requestHeaders = requestHeaders,
            requestBody = ByteArrayInputStream(requestBody, 0, offset),
            socket = socket,
        )
        val handler = contexts.entries
            .filter { (path) -> uri.path == path || uri.path.startsWith("$path/") }
            .maxByOrNull { (path) -> path.length }
            ?.value
        if (handler == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }
        runCatching { handler(exchange) }
            .onFailure {
                if (!exchange.committed) exchange.sendResponseHeaders(500, -1)
            }
        exchange.close()
    }

    companion object {
        fun create(address: InetSocketAddress, backlog: Int): HttpServer {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(address, backlog)
            return HttpServer(socket)
        }
    }
}

internal class HttpExchange(
    val requestURI: URI,
    val requestHeaders: HttpHeaders,
    val requestBody: InputStream,
    private val socket: Socket,
) {
    val responseHeaders = HttpHeaders()
    val responseBody: OutputStream = ByteArrayOutputStream()
    private var responseStatus = 200
    private var responseLength = -1L
    private var closed = false
    internal var committed = false
        private set

    fun sendResponseHeaders(status: Int, length: Long) {
        check(!committed) { "Response already committed" }
        responseStatus = status
        responseLength = length
        committed = true
    }

    fun close() {
        if (closed) return
        closed = true
        if (!committed) sendResponseHeaders(200, (responseBody as ByteArrayOutputStream).size().toLong())
        val body = (responseBody as ByteArrayOutputStream).toByteArray()
        val declaredLength = if (responseLength >= 0L) responseLength else 0L
        val output = socket.getOutputStream()
        output.write("HTTP/1.1 $responseStatus ${reason(responseStatus)}\r\n".toByteArray(StandardCharsets.US_ASCII))
        responseHeaders.entries().forEach { (name, values) ->
            values.forEach { value ->
                output.write("$name: $value\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            }
        }
        output.write("Content-Length: $declaredLength\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        if (declaredLength > 0L) output.write(body)
        output.flush()
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        302 -> "Found"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        500 -> "Internal Server Error"
        503 -> "Service Unavailable"
        else -> "Status"
    }
}

internal class HttpHeaders {
    private val values = linkedMapOf<String, Pair<String, MutableList<String>>>()

    fun set(name: String, value: String) {
        values[name.lowercase()] = name to mutableListOf(value)
    }

    fun add(name: String, value: String) {
        values.getOrPut(name.lowercase()) { name to mutableListOf() }.second += value
    }

    fun getFirst(name: String): String? = values[name.lowercase()]?.second?.firstOrNull()

    internal fun entries(): List<Pair<String, List<String>>> = values.values.map { (name, items) ->
        name to items.toList()
    }
}

private fun InputStream.readHttpLine(): String? {
    val bytes = ByteArrayOutputStream()
    while (true) {
        val value = read()
        if (value < 0) return if (bytes.size() == 0) null else bytes.toString(StandardCharsets.ISO_8859_1.name())
        if (value == '\n'.code) break
        if (value != '\r'.code) bytes.write(value)
        check(bytes.size() <= 16 * 1024) { "HTTP test line is too large" }
    }
    return bytes.toString(StandardCharsets.ISO_8859_1.name())
}

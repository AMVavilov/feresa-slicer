// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.printer

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import tech.g24.feresaslicer.auth.OrcaPrinterConnection
import tech.g24.feresaslicer.auth.PrinterHostType
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL

/** Normalized state exposed by supported printer hosts. */
enum class PrinterOperationalState {
    ONLINE,
    READY,
    PRINTING,
    PAUSED,
    STARTING,
    ERROR,
    OFFLINE,
    UNKNOWN,
}

enum class PrinterJobState {
    IDLE,
    PRINTING,
    PAUSED,
    COMPLETE,
    CANCELLED,
    ERROR,
    UNKNOWN,
}

data class PrinterJobStatus(
    val fileName: String?,
    val state: PrinterJobState,
    /** Normalized fraction in the inclusive 0..1 range, or null when the host has no estimate. */
    val progress: Double?,
    val elapsedSeconds: Double?,
    val remainingSeconds: Double?,
)

data class PrinterTemperature(
    val actualCelsius: Double?,
    val targetCelsius: Double?,
)

data class PrinterTemperatures(
    val tool: PrinterTemperature?,
    val bed: PrinterTemperature?,
)

/** Protocol-specific status returned after a successful, schema-validated connection test. */
sealed interface PrinterStatus {
    val hostType: PrinterHostType
    val operationalState: PrinterOperationalState
    val job: PrinterJobStatus
    val temperatures: PrinterTemperatures
    val canStart: Boolean

    data class Moonraker(
        val klippyState: String,
        val klippyConnected: Boolean,
        val moonrakerVersion: String?,
        val apiVersion: String?,
        val warnings: List<String>,
        override val operationalState: PrinterOperationalState,
        override val job: PrinterJobStatus,
        override val temperatures: PrinterTemperatures,
        override val canStart: Boolean,
    ) : PrinterStatus {
        override val hostType: PrinterHostType = PrinterHostType.MOONRAKER
    }

    data class OctoPrint(
        val serverVersion: String,
        val apiVersion: String,
        val text: String?,
        val printerStateText: String,
        override val operationalState: PrinterOperationalState,
        override val job: PrinterJobStatus,
        override val temperatures: PrinterTemperatures,
        override val canStart: Boolean,
    ) : PrinterStatus {
        override val hostType: PrinterHostType = PrinterHostType.OCTOPRINT
    }
}

enum class PrinterConnectionFailureKind {
    INVALID_CONFIGURATION,
    UNSUPPORTED,
    AUTHENTICATION,
    HTTP,
    NETWORK,
    MALFORMED_RESPONSE,
    WRONG_SERVER,
}

data class PrinterConnectionFailure(
    val kind: PrinterConnectionFailureKind,
    val userMessage: String,
    val httpStatus: Int? = null,
)

sealed interface PrinterConnectionTestResult {
    data class Connected(val status: PrinterStatus) : PrinterConnectionTestResult
    data class Failed(val failure: PrinterConnectionFailure) : PrinterConnectionTestResult
}

/**
 * Performs a side-effect-free connection probe: it only reads the printer server's identity/status
 * endpoint and never uploads, selects, starts, pauses, or deletes a print.
 */
object PrinterConnectionService {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_RESPONSE_BYTES = 1024 * 1024

    fun test(connection: OrcaPrinterConnection): PrinterConnectionTestResult {
        when (connection.hostType) {
            PrinterHostType.PRUSALINK -> return failure(
                PrinterConnectionFailureKind.UNSUPPORTED,
                "Подключение к PrusaLink пока не поддерживается",
            )
            PrinterHostType.UNKNOWN -> return failure(
                PrinterConnectionFailureKind.UNSUPPORTED,
                "Неизвестный тип сервера печати",
            )
            else -> Unit
        }

        val baseUrl = try {
            printerBaseUrl(connection)
        } catch (_: IllegalArgumentException) {
            return failure(
                PrinterConnectionFailureKind.INVALID_CONFIGURATION,
                "Некорректный адрес или порт принтера",
            )
        } catch (_: Exception) {
            return failure(
                PrinterConnectionFailureKind.INVALID_CONFIGURATION,
                "Некорректный адрес или порт принтера",
            )
        }

        return when (connection.hostType) {
            PrinterHostType.MOONRAKER -> {
                val serverInfo = when (val response = get(connection, baseUrl, "server/info")) {
                    is ProbeResponse.Body -> response.value
                    is ProbeResponse.Error -> return response.result
                }
                val identityResult = parseMoonrakerServerInfo(serverInfo)
                if (identityResult is PrinterConnectionTestResult.Failed) return identityResult
                val identity = (identityResult as PrinterConnectionTestResult.Connected).status
                    as PrinterStatus.Moonraker
                if (identity.operationalState != PrinterOperationalState.READY) {
                    return identityResult
                }
                val objects = when (
                    val response = get(
                        connection,
                        baseUrl,
                        "printer/objects/query?print_stats&virtual_sdcard&extruder&heater_bed",
                    )
                ) {
                    is ProbeResponse.Body -> response.value
                    is ProbeResponse.Error -> {
                        if (response.result.failure.httpStatus == HttpURLConnection.HTTP_UNAVAILABLE) {
                            return connected(moonrakerUnavailable(identity))
                        }
                        return response.result
                    }
                }
                parseMoonrakerObjectsStatus(identity, objects)
            }
            PrinterHostType.OCTOPRINT -> {
                val version = when (val response = get(connection, baseUrl, "api/version")) {
                    is ProbeResponse.Body -> response.value
                    is ProbeResponse.Error -> return response.result
                }
                val identityResult = parseOctoPrintVersion(version)
                if (identityResult is PrinterConnectionTestResult.Failed) return identityResult
                val identity = (identityResult as PrinterConnectionTestResult.Connected).status
                    as PrinterStatus.OctoPrint
                val printer = when (val response = get(connection, baseUrl, "api/printer")) {
                    is ProbeResponse.Body -> response.value
                    is ProbeResponse.Error -> {
                        if (response.result.failure.httpStatus == HttpURLConnection.HTTP_CONFLICT) {
                            return connected(octoPrintUnavailable(identity))
                        }
                        return response.result
                    }
                }
                val job = when (val response = get(connection, baseUrl, "api/job")) {
                    is ProbeResponse.Body -> response.value
                    is ProbeResponse.Error -> {
                        if (response.result.failure.httpStatus == HttpURLConnection.HTTP_CONFLICT) {
                            return connected(octoPrintUnavailable(identity))
                        }
                        return response.result
                    }
                }
                parseOctoPrintPrinterAndJob(identity, printer, job)
            }
            else -> error("Unsupported host type was handled before probing")
        }
    }

    private fun get(
        connection: OrcaPrinterConnection,
        baseUrl: String,
        path: String,
    ): ProbeResponse {
        val endpoint = URL("$baseUrl/${path.trimStart('/')}")
        val http = try {
            (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", "application/json")
                printerAuthenticationHeaders(
                    apiKey = connection.apiKey,
                    username = connection.username,
                    password = connection.password,
                ).forEach { (name, value) -> setRequestProperty(name, value) }
            }
        } catch (_: IOException) {
            return ProbeResponse.Error(
                failure(
                    PrinterConnectionFailureKind.NETWORK,
                    "Не удалось подключиться к принтеру",
                ),
            )
        }
        return try {
            val statusCode = http.responseCode
            val stream = if (statusCode in 200..299) http.inputStream else http.errorStream
            val body = stream?.use(::readBoundedUtf8).orEmpty()
            when {
                statusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                    statusCode == HttpURLConnection.HTTP_FORBIDDEN -> ProbeResponse.Error(
                    failure(
                        PrinterConnectionFailureKind.AUTHENTICATION,
                        "Принтер отклонил авторизацию",
                        statusCode,
                    ),
                )
                statusCode !in 200..299 -> ProbeResponse.Error(
                    failure(
                        PrinterConnectionFailureKind.HTTP,
                        "Сервер принтера вернул HTTP $statusCode",
                        statusCode,
                    ),
                )
                else -> ProbeResponse.Body(body)
            }
        } catch (_: SocketTimeoutException) {
            ProbeResponse.Error(
                failure(
                    PrinterConnectionFailureKind.NETWORK,
                    "Принтер не ответил вовремя",
                ),
            )
        } catch (_: ResponseTooLargeException) {
            ProbeResponse.Error(
                failure(
                    PrinterConnectionFailureKind.MALFORMED_RESPONSE,
                    "Ответ сервера принтера слишком большой",
                ),
            )
        } catch (_: IOException) {
            ProbeResponse.Error(
                failure(
                    PrinterConnectionFailureKind.NETWORK,
                    "Не удалось подключиться к принтеру",
                ),
            )
        } finally {
            http.disconnect()
        }
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
                throw ResponseTooLargeException()
            }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private sealed interface ProbeResponse {
        data class Body(val value: String) : ProbeResponse
        data class Error(val result: PrinterConnectionTestResult.Failed) : ProbeResponse
    }

    private class ResponseTooLargeException : IOException()
}

internal fun printerBaseUrl(connection: OrcaPrinterConnection): String {
    val raw = connection.host.trim().trimEnd('/')
    require(raw.isNotBlank()) { "Printer address is blank" }
    val hasSupportedScheme = raw.startsWith("http://", ignoreCase = true) ||
        raw.startsWith("https://", ignoreCase = true)
    val withScheme = if (hasSupportedScheme) raw else "http://$raw"
    val uri = URI(withScheme)
    require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
        "Only HTTP and HTTPS are supported"
    }
    require(!uri.host.isNullOrBlank()) { "Invalid printer address" }
    require(uri.userInfo == null) { "Credentials must not be embedded in the printer URL" }
    require(uri.query == null) { "Printer URL queries are not supported" }
    require(uri.fragment == null) { "Printer URL fragments are not supported" }
    if (connection.port.isBlank() || uri.port != -1) return uri.toString().trimEnd('/')
    val port = connection.port.toIntOrNull()?.takeIf { it in 1..65535 }
        ?: error("Invalid printer port")
    return URI(uri.scheme, null, uri.host, port, uri.path, uri.query, null).toString().trimEnd('/')
}

internal fun parseMoonrakerServerInfo(response: String): PrinterConnectionTestResult {
    val json = parseJsonObject(response) ?: return malformedResponse()
    // Current Moonraker HTTP responses contain the fields at the root. Older installations and
    // JSON-RPC bridges may wrap the same payload in `result`, so accept both layouts while still
    // requiring Moonraker's identifying state fields below.
    val result = json.optJSONObject("result") ?: json
    val state = result.strictNonBlankString("klippy_state") ?: return wrongServer("Moonraker")
    val connected = result.strictBooleanOrNull("klippy_connected") ?: return wrongServer("Moonraker")
    val normalized = when (state.trim().lowercase()) {
        "startup", "starting" -> PrinterOperationalState.STARTING
        "shutdown", "error" -> PrinterOperationalState.ERROR
        "disconnected", "offline" -> PrinterOperationalState.OFFLINE
        "ready" -> if (connected) PrinterOperationalState.READY else PrinterOperationalState.OFFLINE
        else -> if (connected) PrinterOperationalState.UNKNOWN else PrinterOperationalState.OFFLINE
    }
    return PrinterConnectionTestResult.Connected(
        PrinterStatus.Moonraker(
            klippyState = state,
            klippyConnected = connected,
            moonrakerVersion = result.strictOptionalString("moonraker_version"),
            apiVersion = result.strictOptionalString("api_version_string"),
            warnings = result.optJSONArray("warnings").safeStrings(limit = 50, maxLength = 500),
            operationalState = normalized,
            job = unknownJob(),
            temperatures = emptyTemperatures(),
            canStart = false,
        ),
    )
}

internal fun parseMoonrakerObjectsStatus(
    identity: PrinterStatus.Moonraker,
    response: String,
): PrinterConnectionTestResult {
    val json = parseJsonObject(response) ?: return malformedResponse()
    if (json.has("error")) return connected(moonrakerUnavailable(identity))
    val result = json.optJSONObject("result") ?: json
    if (result.has("error")) return connected(moonrakerUnavailable(identity))
    val status = result.optJSONObject("status") ?: return malformedResponse()
    val temperatures = PrinterTemperatures(
        tool = status.optJSONObject("extruder").printerTemperature(),
        bed = status.optJSONObject("heater_bed").printerTemperature(),
    )
    val printStats = status.optJSONObject("print_stats") ?: return connected(
        identity.copy(temperatures = temperatures, canStart = false),
    )
    val rawJobState = printStats.strictNonBlankString("state") ?: return malformedResponse()
    val jobState = moonrakerJobState(rawJobState)
    val progress = status.optJSONObject("virtual_sdcard")
        .strictFiniteDouble("progress")
        ?.coerceIn(0.0, 1.0)
    val job = PrinterJobStatus(
        fileName = printStats.strictOptionalString("filename")?.take(512),
        state = jobState,
        progress = progress,
        elapsedSeconds = printStats.strictFiniteDouble("print_duration")?.takeIf { it >= 0.0 },
        remainingSeconds = null,
    )
    val operationalState = when (jobState) {
        PrinterJobState.PRINTING -> PrinterOperationalState.PRINTING
        PrinterJobState.PAUSED -> PrinterOperationalState.PAUSED
        else -> identity.operationalState
    }
    val canStart = identity.klippyConnected &&
        identity.klippyState.equals("ready", ignoreCase = true) &&
        jobState in setOf(
            PrinterJobState.IDLE,
            PrinterJobState.COMPLETE,
            PrinterJobState.CANCELLED,
            PrinterJobState.ERROR,
        )
    return connected(
        identity.copy(
            operationalState = operationalState,
            job = job,
            temperatures = temperatures,
            canStart = canStart,
        ),
    )
}

internal fun parseOctoPrintVersion(response: String): PrinterConnectionTestResult {
    val json = parseJsonObject(response) ?: return malformedResponse()
    // OctoPrint documents `text` (including its product prefix) as the field clients should use to
    // determine that this is a genuine OctoPrint instance.
    val serverVersion = json.strictNonBlankString("server") ?: return wrongServer("OctoPrint")
    val apiVersion = json.strictNonBlankString("api") ?: return wrongServer("OctoPrint")
    val text = json.strictNonBlankString("text")
        ?.takeIf { it.startsWith("OctoPrint ", ignoreCase = true) }
        ?: return wrongServer("OctoPrint")
    return PrinterConnectionTestResult.Connected(
        PrinterStatus.OctoPrint(
            serverVersion = serverVersion,
            apiVersion = apiVersion,
            text = text,
            printerStateText = "Unknown",
            operationalState = PrinterOperationalState.ONLINE,
            job = unknownJob(),
            temperatures = emptyTemperatures(),
            canStart = false,
        ),
    )
}

internal fun parseOctoPrintPrinterAndJob(
    identity: PrinterStatus.OctoPrint,
    printerResponse: String,
    jobResponse: String?,
): PrinterConnectionTestResult {
    val printerJson = parseJsonObject(printerResponse) ?: return malformedResponse()
    if (printerJson.has("error")) return connected(octoPrintUnavailable(identity))
    val state = printerJson.optJSONObject("state") ?: return malformedResponse()
    val stateText = state.strictNonBlankString("text") ?: return malformedResponse()
    val flags = state.optJSONObject("flags") ?: return malformedResponse()
    val recognizedFlag = listOf(
        "operational",
        "ready",
        "printing",
        "paused",
        "pausing",
        "cancelling",
        "error",
        "closedOrError",
    ).any { flags.strictBooleanOrNull(it) != null }
    if (!recognizedFlag) return malformedResponse()

    val operational = flags.strictBooleanOrNull("operational") == true
    val ready = flags.strictBooleanOrNull("ready") == true
    val printing = flags.strictBooleanOrNull("printing") == true ||
        flags.strictBooleanOrNull("cancelling") == true
    val paused = flags.strictBooleanOrNull("paused") == true ||
        flags.strictBooleanOrNull("pausing") == true
    val hasError = flags.strictBooleanOrNull("error") == true ||
        flags.strictBooleanOrNull("closedOrError") == true

    val jobJson = jobResponse?.let(::parseJsonObject)
    if (jobResponse != null && jobJson == null) return malformedResponse()
    val jobUnavailable = jobJson == null || jobJson.has("error")
    val rawJobState = jobJson?.strictOptionalString("state")
    val jobState = when {
        hasError -> PrinterJobState.ERROR
        printing -> PrinterJobState.PRINTING
        paused -> PrinterJobState.PAUSED
        jobUnavailable -> PrinterJobState.UNKNOWN
        rawJobState != null -> octoPrintJobState(rawJobState)
        else -> PrinterJobState.UNKNOWN
    }
    val jobDetails = jobJson?.optJSONObject("job")
    val file = jobDetails?.optJSONObject("file")
    val progress = jobJson?.optJSONObject("progress")
    val completion = progress.strictFiniteDouble("completion")
        ?.div(100.0)
        ?.coerceIn(0.0, 1.0)
    val job = PrinterJobStatus(
        fileName = listOf("display", "name", "path")
            .firstNotNullOfOrNull { file?.strictOptionalString(it) }
            ?.take(512),
        state = jobState,
        progress = completion,
        elapsedSeconds = progress.strictFiniteDouble("printTime")?.takeIf { it >= 0.0 },
        remainingSeconds = progress.strictFiniteDouble("printTimeLeft")?.takeIf { it >= 0.0 },
    )
    val operationalState = when {
        hasError -> PrinterOperationalState.ERROR
        printing -> PrinterOperationalState.PRINTING
        paused -> PrinterOperationalState.PAUSED
        ready || operational -> PrinterOperationalState.READY
        else -> PrinterOperationalState.OFFLINE
    }
    val canStart = !jobUnavailable &&
        ready &&
        !printing &&
        !paused &&
        !hasError &&
        jobState !in setOf(PrinterJobState.PRINTING, PrinterJobState.PAUSED, PrinterJobState.ERROR)
    val temperatures = printerJson.optJSONObject("temperature")
    return connected(
        identity.copy(
            printerStateText = stateText,
            operationalState = operationalState,
            job = job,
            temperatures = PrinterTemperatures(
                tool = temperatures?.optJSONObject("tool0").printerTemperature(),
                bed = temperatures?.optJSONObject("bed").printerTemperature(),
            ),
            canStart = canStart,
        ),
    )
}

private fun moonrakerUnavailable(identity: PrinterStatus.Moonraker): PrinterStatus.Moonraker =
    identity.copy(
        klippyConnected = false,
        operationalState = when (identity.operationalState) {
            PrinterOperationalState.STARTING -> PrinterOperationalState.STARTING
            PrinterOperationalState.ERROR -> PrinterOperationalState.ERROR
            else -> PrinterOperationalState.OFFLINE
        },
        job = unknownJob(),
        temperatures = emptyTemperatures(),
        canStart = false,
    )

private fun octoPrintUnavailable(identity: PrinterStatus.OctoPrint): PrinterStatus.OctoPrint =
    identity.copy(
        printerStateText = "Offline",
        operationalState = PrinterOperationalState.OFFLINE,
        job = unknownJob(),
        temperatures = emptyTemperatures(),
        canStart = false,
    )

private fun moonrakerJobState(value: String): PrinterJobState = when (value.trim().lowercase()) {
    "standby" -> PrinterJobState.IDLE
    "printing" -> PrinterJobState.PRINTING
    "paused" -> PrinterJobState.PAUSED
    "complete" -> PrinterJobState.COMPLETE
    "cancelled", "canceled" -> PrinterJobState.CANCELLED
    "error" -> PrinterJobState.ERROR
    else -> PrinterJobState.UNKNOWN
}

private fun octoPrintJobState(value: String): PrinterJobState = when (value.trim().lowercase()) {
    "operational", "offline", "closed" -> PrinterJobState.IDLE
    "printing", "cancelling", "starting", "finishing" -> PrinterJobState.PRINTING
    "paused", "pausing" -> PrinterJobState.PAUSED
    "finished" -> PrinterJobState.COMPLETE
    "cancelled", "canceled" -> PrinterJobState.CANCELLED
    "error", "closed with error" -> PrinterJobState.ERROR
    else -> PrinterJobState.UNKNOWN
}

private fun parseJsonObject(response: String): JSONObject? {
    if (response.isBlank()) return null
    return try {
        JSONObject(response)
    } catch (_: JSONException) {
        null
    }
}

private fun JSONObject?.printerTemperature(): PrinterTemperature? {
    if (this == null) return null
    val actual = strictFiniteDouble("temperature") ?: strictFiniteDouble("actual")
    val target = strictFiniteDouble("target")
    if (actual == null && target == null) return null
    return PrinterTemperature(actualCelsius = actual, targetCelsius = target)
}

private fun JSONObject?.strictFiniteDouble(name: String): Double? {
    if (this == null || !has(name) || isNull(name)) return null
    return (opt(name) as? Number)?.toDouble()?.takeIf(Double::isFinite)
}

private fun unknownJob(): PrinterJobStatus = PrinterJobStatus(
    fileName = null,
    state = PrinterJobState.UNKNOWN,
    progress = null,
    elapsedSeconds = null,
    remainingSeconds = null,
)

private fun emptyTemperatures(): PrinterTemperatures = PrinterTemperatures(tool = null, bed = null)

private fun connected(status: PrinterStatus): PrinterConnectionTestResult.Connected =
    PrinterConnectionTestResult.Connected(status)

private fun malformedResponse(): PrinterConnectionTestResult.Failed = failure(
    PrinterConnectionFailureKind.MALFORMED_RESPONSE,
    "Сервер принтера вернул некорректный ответ",
)

private fun JSONObject.strictNonBlankString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    val value = opt(name)
    return (value as? String)?.trim()?.takeIf(String::isNotEmpty)
}

private fun JSONObject.strictOptionalString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return (opt(name) as? String)?.trim()?.takeIf(String::isNotEmpty)
}

private fun JSONObject.strictBooleanOrNull(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return opt(name) as? Boolean
}

private fun JSONArray?.safeStrings(limit: Int, maxLength: Int): List<String> {
    if (this == null) return emptyList()
    val count = length().coerceAtMost(limit)
    return buildList(count) {
        for (index in 0 until count) {
            (opt(index) as? String)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.take(maxLength)
                ?.let(::add)
        }
    }
}

private fun wrongServer(expected: String): PrinterConnectionTestResult = failure(
    PrinterConnectionFailureKind.WRONG_SERVER,
    "Сервер ответил, но это не похоже на $expected",
)

private fun failure(
    kind: PrinterConnectionFailureKind,
    message: String,
    status: Int? = null,
): PrinterConnectionTestResult.Failed = PrinterConnectionTestResult.Failed(
    PrinterConnectionFailure(kind, message, status),
)

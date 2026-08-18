// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer.printer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.g24.feresaslicer.auth.OrcaPrinterConnection
import tech.g24.feresaslicer.auth.PrinterHostType
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class PrinterConnectionServiceTest {
    @Test
    fun parsesMoonrakerIdentityAndCurrentPrintStatus() {
        val identity = connectedMoonraker(
            parseMoonrakerServerInfo(
                """
                    {"result": {
                      "klippy_connected": true,
                      "klippy_state": "ready",
                      "moonraker_version": "v0.9.3-12-g1234567",
                      "api_version_string": "1.5.0",
                      "warnings": ["Update available", 42, "  Config warning  "]
                    }}
                """.trimIndent(),
            ),
        )
        val parsed = parseMoonrakerObjectsStatus(
            identity,
            """
                {"result":{"eventtime":123.4,"status":{
                  "print_stats":{
                    "filename":"jobs/keychain.gcode",
                    "state":"printing",
                    "print_duration":321.5,
                    "info":{"current_layer":5,"total_layer":20}
                  },
                  "virtual_sdcard":{"progress":0.25},
                  "extruder":{"temperature":207.3,"target":210.0},
                  "heater_bed":{"temperature":59.7,"target":60.0}
                }}}
            """.trimIndent(),
        )

        val status = connectedMoonraker(parsed)
        assertEquals("ready", status.klippyState)
        assertTrue(status.klippyConnected)
        assertEquals("v0.9.3-12-g1234567", status.moonrakerVersion)
        assertEquals(listOf("Update available", "Config warning"), status.warnings)
        assertEquals(PrinterOperationalState.PRINTING, status.operationalState)
        assertEquals(PrinterJobState.PRINTING, status.job.state)
        assertEquals("jobs/keychain.gcode", status.job.fileName)
        assertEquals(0.25, status.job.progress!!, 0.0001)
        assertEquals(321.5, status.job.elapsedSeconds!!, 0.0001)
        assertEquals(207.3, status.temperatures.tool!!.actualCelsius!!, 0.0001)
        assertEquals(60.0, status.temperatures.bed!!.targetCelsius!!, 0.0001)
        assertFalse(status.canStart)
    }

    @Test
    fun moonrakerReadyStandbyCanStartAndConfiguredObjectsMayBeMissing() {
        val identity = connectedMoonraker(
            parseMoonrakerServerInfo(
                """{"klippy_connected":true,"klippy_state":"ready"}""",
            ),
        )
        val standby = connectedMoonraker(
            parseMoonrakerObjectsStatus(
                identity,
                """{"status":{"print_stats":{"state":"standby","filename":""}}}""",
            ),
        )
        val missingPrintStats = connectedMoonraker(
            parseMoonrakerObjectsStatus(
                identity,
                """{"result":{"status":{"extruder":{"temperature":22.0,"target":0}}}}""",
            ),
        )

        assertTrue(standby.canStart)
        assertEquals(PrinterJobState.IDLE, standby.job.state)
        assertFalse(missingPrintStats.canStart)
        assertEquals(22.0, missingPrintStats.temperatures.tool!!.actualCelsius!!, 0.0001)
    }

    @Test
    fun moonrakerStartupIsConnectedStateEvenBeforeKlippyObjectsAreAvailable() {
        val status = connectedMoonraker(
            parseMoonrakerServerInfo(
                """{"result":{"klippy_connected":false,"klippy_state":"startup"}}""",
            ),
        )

        assertEquals(PrinterOperationalState.STARTING, status.operationalState)
        assertFalse(status.canStart)
        assertEquals(PrinterJobState.UNKNOWN, status.job.state)
    }

    @Test
    fun parsesOctoPrintPrinterJobProgressAndTemperatures() {
        val identity = connectedOctoPrint(
            parseOctoPrintVersion(
                """{"api":"0.1","server":"1.10.3","text":"OctoPrint 1.10.3"}""",
            ),
        )
        val parsed = parseOctoPrintPrinterAndJob(
            identity = identity,
            printerResponse = """
                {
                  "temperature":{
                    "tool0":{"actual":206.4,"target":210.0,"offset":0},
                    "bed":{"actual":59.8,"target":60.0,"offset":0}
                  },
                  "state":{"text":"Printing","flags":{
                    "operational":true,"ready":false,"printing":true,"paused":false,
                    "pausing":false,"cancelling":false,"error":false,"closedOrError":false
                  }}
                }
            """.trimIndent(),
            jobResponse = """
                {
                  "job":{"file":{"name":"cube.gcode","display":"Cube.gcode","path":"models/cube.gcode"}},
                  "progress":{"completion":22.9,"printTime":120,"printTimeLeft":400},
                  "state":"Printing"
                }
            """.trimIndent(),
        )

        val status = connectedOctoPrint(parsed)
        assertEquals(PrinterOperationalState.PRINTING, status.operationalState)
        assertEquals(PrinterJobState.PRINTING, status.job.state)
        assertEquals("Cube.gcode", status.job.fileName)
        assertEquals(0.229, status.job.progress!!, 0.0001)
        assertEquals(120.0, status.job.elapsedSeconds!!, 0.0001)
        assertEquals(400.0, status.job.remainingSeconds!!, 0.0001)
        assertEquals(206.4, status.temperatures.tool!!.actualCelsius!!, 0.0001)
        assertFalse(status.canStart)
    }

    @Test
    fun octoPrintReadyFlagControlsCanStart() {
        val identity = connectedOctoPrint(
            parseOctoPrintVersion(
                """{"api":"0.1","server":"1.11.0","text":"OctoPrint 1.11.0"}""",
            ),
        )
        val status = connectedOctoPrint(
            parseOctoPrintPrinterAndJob(
                identity,
                """{"state":{"text":"Operational","flags":{"operational":true,"ready":true,"printing":false,"paused":false,"error":false,"closedOrError":false}}}""",
                """{"job":{"file":{}},"progress":{"completion":null},"state":"Operational"}""",
            ),
        )

        assertEquals(PrinterOperationalState.READY, status.operationalState)
        assertEquals(PrinterJobState.IDLE, status.job.state)
        assertTrue(status.canStart)
    }

    @Test
    fun validJsonWithWrongIdentitySchemaIsNotAccepted() {
        val moonraker = parseMoonrakerServerInfo(
            """{"result":{"klippy_state":"ready"}}""",
        ) as PrinterConnectionTestResult.Failed
        val octoPrint = parseOctoPrintVersion(
            """{"api":"0.1","server":"9.9","text":"Other 9.9"}""",
        ) as PrinterConnectionTestResult.Failed

        assertEquals(PrinterConnectionFailureKind.WRONG_SERVER, moonraker.failure.kind)
        assertEquals(PrinterConnectionFailureKind.WRONG_SERVER, octoPrint.failure.kind)
    }

    @Test
    fun emptyAndInvalidJsonAreReportedAsMalformed() {
        listOf("", "not-json", "[]").forEach { response ->
            val failed = parseMoonrakerServerInfo(response) as PrinterConnectionTestResult.Failed
            assertEquals(PrinterConnectionFailureKind.MALFORMED_RESPONSE, failed.failure.kind)
        }
    }

    @Test
    fun unsupportedProtocolAndInvalidAddressAreTypedWithoutIoOrSecretLeak() {
        val unsupported = PrinterConnectionService.test(
            connection(hostType = PrinterHostType.PRUSALINK, host = "does-not-exist.invalid"),
        ) as PrinterConnectionTestResult.Failed
        val invalid = PrinterConnectionService.test(
            connection(
                hostType = PrinterHostType.MOONRAKER,
                host = "http://user:secret@printer.invalid",
            ),
        ) as PrinterConnectionTestResult.Failed

        assertEquals(PrinterConnectionFailureKind.UNSUPPORTED, unsupported.failure.kind)
        assertEquals(PrinterConnectionFailureKind.INVALID_CONFIGURATION, invalid.failure.kind)
        assertFalse(invalid.failure.userMessage.contains("secret"))
    }

    @Test
    fun serviceUsesMoonrakerEndpointsAndAuthenticationHeader() {
        val requestedPaths = mutableListOf<String>()
        var receivedApiKey: String? = null
        withServer { server ->
            server.createContext("/server/info") { exchange ->
                requestedPaths += exchange.requestURI.toString()
                receivedApiKey = exchange.requestHeaders.getFirst(ApiKeyHeader)
                exchange.respond(
                    200,
                    """{"result":{"klippy_connected":true,"klippy_state":"ready"}}""",
                )
            }
            server.createContext("/printer/objects/query") { exchange ->
                requestedPaths += exchange.requestURI.toString()
                exchange.respond(
                    200,
                    """{"result":{"status":{"print_stats":{"state":"standby"}}}}""",
                )
            }
            server.start()

            val status = connectedMoonraker(
                PrinterConnectionService.test(
                    localConnection(server, PrinterHostType.MOONRAKER, apiKey = "test-api-key"),
                ),
            )

            assertTrue(status.canStart)
            assertEquals(
                listOf(
                    "/server/info",
                    "/printer/objects/query?print_stats&virtual_sdcard&extruder&heater_bed",
                ),
                requestedPaths,
            )
            assertEquals("test-api-key", receivedApiKey)
        }
    }

    @Test
    fun moonrakerOfflineSkipsObjectsAnd503AfterIdentityStaysConnected() {
        val objectCalls = AtomicInteger()
        withServer { server ->
            server.createContext("/server/info") { exchange ->
                exchange.respond(
                    200,
                    """{"result":{"klippy_connected":false,"klippy_state":"disconnected"}}""",
                )
            }
            server.createContext("/printer/objects/query") { exchange ->
                objectCalls.incrementAndGet()
                exchange.respond(503, """{"error":{"message":"Klippy disconnected"}}""")
            }
            server.start()

            val status = connectedMoonraker(
                PrinterConnectionService.test(localConnection(server, PrinterHostType.MOONRAKER)),
            )

            assertEquals(PrinterOperationalState.OFFLINE, status.operationalState)
            assertEquals(0, objectCalls.get())
        }

        withServer { server ->
            server.createContext("/server/info") { exchange ->
                exchange.respond(
                    200,
                    """{"result":{"klippy_connected":true,"klippy_state":"ready"}}""",
                )
            }
            server.createContext("/printer/objects/query") { exchange ->
                exchange.respond(503, """{"error":{"message":"Klippy disconnected"}}""")
            }
            server.start()

            val status = connectedMoonraker(
                PrinterConnectionService.test(localConnection(server, PrinterHostType.MOONRAKER)),
            )

            assertEquals(PrinterOperationalState.OFFLINE, status.operationalState)
            assertFalse(status.canStart)
        }
    }

    @Test
    fun octoPrint409AfterIdentityIsConnectedOffline() {
        val jobCalls = AtomicInteger()
        withServer { server ->
            server.createContext("/api/version") { exchange ->
                exchange.respond(
                    200,
                    """{"api":"0.1","server":"1.10.3","text":"OctoPrint 1.10.3"}""",
                )
            }
            server.createContext("/api/printer") { exchange ->
                exchange.respond(409, """{"error":"Printer is not operational"}""")
            }
            server.createContext("/api/job") { exchange ->
                jobCalls.incrementAndGet()
                exchange.respond(200, """{"job":{},"progress":{},"state":"Offline"}""")
            }
            server.start()

            val status = connectedOctoPrint(
                PrinterConnectionService.test(localConnection(server, PrinterHostType.OCTOPRINT)),
            )

            assertEquals(PrinterOperationalState.OFFLINE, status.operationalState)
            assertEquals(0, jobCalls.get())
        }
    }

    @Test
    fun probeDoesNotFollowRedirects() {
        val targetCalls = AtomicInteger()
        withServer { server ->
            server.createContext("/server/info") { exchange ->
                exchange.responseHeaders.set(
                    "Location",
                    "http://127.0.0.1:${server.address.port}/redirect-target",
                )
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.createContext("/redirect-target") { exchange ->
                targetCalls.incrementAndGet()
                exchange.respond(
                    200,
                    """{"result":{"klippy_connected":true,"klippy_state":"ready"}}""",
                )
            }
            server.start()

            val failed = PrinterConnectionService.test(
                localConnection(server, PrinterHostType.MOONRAKER),
            ) as PrinterConnectionTestResult.Failed

            assertEquals(PrinterConnectionFailureKind.HTTP, failed.failure.kind)
            assertEquals(302, failed.failure.httpStatus)
            assertEquals(0, targetCalls.get())
        }
    }

    @Test
    fun moonrakerUploadAndStartAreSeparateAndUploadHasFixedLength() {
        val startCalls = AtomicInteger()
        var uploadBody = ""
        var uploadContentLength: String? = null
        var uploadTransferEncoding: String? = null
        var startBody = ""
        withServer { server ->
            server.createContext("/server/files/upload") { exchange ->
                uploadContentLength = exchange.requestHeaders.getFirst("Content-Length")
                uploadTransferEncoding = exchange.requestHeaders.getFirst("Transfer-Encoding")
                uploadBody = exchange.requestBody.bufferedReader().use { it.readText() }
                exchange.respond(
                    201,
                    """{"item":{"path":"jobs/uploaded.gcode","root":"gcodes"},"print_started":false}""",
                )
            }
            server.createContext("/printer/print/start") { exchange ->
                startCalls.incrementAndGet()
                startBody = exchange.requestBody.bufferedReader().use { it.readText() }
                exchange.respond(200, """{"result":"ok"}""")
            }
            server.start()
            val source = temporaryGcode()
            val connection = localConnection(server, PrinterHostType.MOONRAKER)

            val upload = NetworkPrinterClient.upload(connection, source, "phone test.gcode")

            assertEquals("jobs/uploaded.gcode", upload.remotePath)
            assertEquals(0, startCalls.get())
            assertTrue(uploadContentLength!!.toLong() > source.length())
            assertNull(uploadTransferEncoding)
            assertTrue(uploadBody.contains("filename=\"phone_test.gcode\""))

            val sent = NetworkPrinterClient.start(connection, upload.remotePath)

            assertTrue(sent.printStarted)
            assertEquals(1, startCalls.get())
            assertEquals("jobs/uploaded.gcode", JSONObject(startBody).getString("filename"))
        }
    }

    @Test
    fun octoPrintServiceProbesVersionPrinterAndJob() {
        val requestedPaths = mutableListOf<String>()
        withServer { server ->
            server.createContext("/api/version") { exchange ->
                requestedPaths += exchange.requestURI.path
                exchange.respond(
                    200,
                    """{"api":"0.1","server":"1.11.0","text":"OctoPrint 1.11.0"}""",
                )
            }
            server.createContext("/api/printer") { exchange ->
                requestedPaths += exchange.requestURI.path
                exchange.respond(
                    200,
                    """{"temperature":{"tool0":{"actual":25,"target":0}},"state":{"text":"Operational","flags":{"ready":true,"operational":true,"printing":false,"paused":false,"error":false,"closedOrError":false}}}""",
                )
            }
            server.createContext("/api/job") { exchange ->
                requestedPaths += exchange.requestURI.path
                exchange.respond(
                    200,
                    """{"job":{"file":{}},"progress":{"completion":null},"state":"Operational"}""",
                )
            }
            server.start()

            val status = connectedOctoPrint(
                PrinterConnectionService.test(localConnection(server, PrinterHostType.OCTOPRINT)),
            )

            assertEquals(listOf("/api/version", "/api/printer", "/api/job"), requestedPaths)
            assertEquals(PrinterOperationalState.READY, status.operationalState)
            assertEquals(25.0, status.temperatures.tool!!.actualCelsius!!, 0.0001)
            assertTrue(status.canStart)
        }
    }

    @Test
    fun octoPrintUploadAndStartAreSeparateAndRemotePathIsEncoded() {
        val startCalls = AtomicInteger()
        var uploadBody = ""
        var startRawPath = ""
        var startBody = ""
        withServer { server ->
            server.createContext("/api/files/local") { exchange ->
                if (exchange.requestURI.rawPath == "/api/files/local") {
                    uploadBody = exchange.requestBody.bufferedReader().use { it.readText() }
                    exchange.respond(
                        201,
                        """{"files":{"local":{"name":"My print.gcode","path":"folder/My print.gcode"}},"done":true}""",
                    )
                } else {
                    startCalls.incrementAndGet()
                    startRawPath = exchange.requestURI.rawPath
                    startBody = exchange.requestBody.bufferedReader().use { it.readText() }
                    exchange.sendResponseHeaders(204, -1)
                    exchange.close()
                }
            }
            server.start()
            val connection = localConnection(server, PrinterHostType.OCTOPRINT)

            val upload = NetworkPrinterClient.upload(connection, temporaryGcode(), "My print.gcode")

            assertEquals("folder/My print.gcode", upload.remotePath)
            assertEquals(0, startCalls.get())
            assertTrue(uploadBody.contains("name=\"select\"\r\n\r\nfalse"))
            assertTrue(uploadBody.contains("name=\"print\"\r\n\r\nfalse"))

            NetworkPrinterClient.start(connection, upload.remotePath)

            assertEquals(1, startCalls.get())
            assertEquals("/api/files/local/folder/My%20print.gcode", startRawPath)
            val command = JSONObject(startBody)
            assertEquals("select", command.getString("command"))
            assertTrue(command.getBoolean("print"))
        }
    }

    @Test
    fun malformedSuccessfulUploadResponseNeverStartsGuessedPath() {
        listOf(
            PrinterHostType.MOONRAKER to "/server/files/upload",
            PrinterHostType.OCTOPRINT to "/api/files/local",
        ).forEach { (hostType, uploadPath) ->
            withServer { server ->
                server.createContext(uploadPath) { exchange ->
                    exchange.requestBody.use { it.readBytes() }
                    exchange.respond(200, "{}")
                }
                server.start()

                val failure = assertThrows(IllegalStateException::class.java) {
                    NetworkPrinterClient.upload(
                        localConnection(server, hostType),
                        temporaryGcode(),
                        "must-not-be-guessed.gcode",
                    )
                }

                assertEquals(
                    "Сервер принтера не вернул путь загруженного файла",
                    failure.message,
                )
                assertFalse(failure.message.orEmpty().contains("must-not-be-guessed"))
            }
        }
    }

    @Test
    fun printerBaseUrlAddsPortAndKeepsReverseProxyPath() {
        assertEquals(
            "https://printer.example.test:7125/moonraker",
            printerBaseUrl(
                connection(
                    hostType = PrinterHostType.MOONRAKER,
                    host = "https://printer.example.test/moonraker/",
                    port = "7125",
                ),
            ),
        )
    }

    private fun connectedMoonraker(result: PrinterConnectionTestResult): PrinterStatus.Moonraker =
        (result as PrinterConnectionTestResult.Connected).status as PrinterStatus.Moonraker

    private fun connectedOctoPrint(result: PrinterConnectionTestResult): PrinterStatus.OctoPrint =
        (result as PrinterConnectionTestResult.Connected).status as PrinterStatus.OctoPrint

    private fun withServer(block: (HttpServer) -> Unit) {
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            0,
        )
        try {
            block(server)
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun localConnection(
        server: HttpServer,
        hostType: PrinterHostType,
        apiKey: String = "",
    ): OrcaPrinterConnection = connection(
        hostType = hostType,
        host = "127.0.0.1",
        port = server.address.port.toString(),
        apiKey = apiKey,
    )

    private fun connection(
        hostType: PrinterHostType,
        host: String,
        port: String = "",
        apiKey: String = "",
    ) = OrcaPrinterConnection(
        profileId = "profile",
        printerName = "Test printer",
        host = host,
        hostType = hostType,
        port = port,
        apiKey = apiKey,
    )

    private fun temporaryGcode(): File = Files.createTempFile("feresa-printer-test", ".gcode")
        .toFile()
        .apply {
            writeText("G28\nG1 X10 Y10\n", StandardCharsets.UTF_8)
            deleteOnExit()
        }
}

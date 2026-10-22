package dev.pocketagent.core.sandbox

import com.google.common.truth.Truth.assertThat
import dev.pocketagent.core.model.RemoteConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class RemoteExecBackendTest {

    private lateinit var mockServer: MockHttpServer

    class MockHttpServer : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        val port: Int = serverSocket.localPort
        @Volatile private var isRunning = true

        init {
            thread(name = "mock-http-server") {
                while (isRunning) {
                    try {
                        val socket = serverSocket.accept()
                        thread {
                            handleClient(socket)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        private fun handleClient(socket: Socket) {
            socket.use {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val method = parts[0]
                val path = parts.getOrElse(1) { "/" }

                var contentLength = 0
                var authHeader = ""
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isEmpty()) break
                    if (header.startsWith("Authorization:", ignoreCase = true)) {
                        authHeader = header.substringAfter(":").trim()
                    } else if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                val body = if (contentLength > 0) {
                    val chars = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val r = reader.read(chars, readTotal, contentLength - readTotal)
                        if (r <= 0) break
                        readTotal += r
                    }
                    String(chars, 0, readTotal)
                } else ""

                val output = socket.getOutputStream()
                if (path == "/api/health") {
                    if (authHeader == "Bearer valid_secret_token") {
                        val json = """{"status":"ok","os":"Linux 6.1","version":"1.2.0","uptime":12345}"""
                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${json.toByteArray().size}\r\nConnection: close\r\n\r\n$json"
                        output.write(resp.toByteArray())
                    } else {
                        val json = """{"error":"unauthorized"}"""
                        val resp = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nContent-Length: ${json.toByteArray().size}\r\nConnection: close\r\n\r\n$json"
                        output.write(resp.toByteArray())
                    }
                } else if (path == "/api/exec") {
                    val json = if (body.contains("npm") && body.contains("test")) {
                        """{"exitCode":0,"stdout":"PASS test/app.spec.js\nAll tests passed","stderr":""}"""
                    } else {
                        """{"exitCode":1,"stdout":"","stderr":"Command failed"}"""
                    }
                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${json.toByteArray().size}\r\nConnection: close\r\n\r\n$json"
                    output.write(resp.toByteArray())
                }
                output.flush()
            }
        }

        override fun close() {
            isRunning = false
            try {
                serverSocket.close()
            } catch (_: Exception) {
            }
        }
    }

    @Before
    fun setup() {
        mockServer = MockHttpServer()
    }

    @After
    fun tearDown() {
        mockServer.close()
    }

    @Test
    fun testRemoteHealthPing() = runBlocking {
        val config = RemoteConfig(
            host = "127.0.0.1",
            port = mockServer.port,
            token = "valid_secret_token"
        )
        val backend = RemoteExecBackend(configProvider = { config })

        val status = backend.ping()
        assertThat(status.isOnline).isTrue()
        assertThat(status.os).isEqualTo("Linux 6.1")
        assertThat(status.version).isEqualTo("1.2.0")
        assertThat(status.latencyMs).isAtLeast(0)
    }

    @Test
    fun testRemoteHealthUnauthorized() = runBlocking {
        val config = RemoteConfig(
            host = "127.0.0.1",
            port = mockServer.port,
            token = "invalid_token"
        )
        val backend = RemoteExecBackend(configProvider = { config })

        val status = backend.ping()
        assertThat(status.isOnline).isFalse()
        assertThat(status.errorMessage).contains("401")
    }

    @Test
    fun testRemoteExecSuccessAndFailure() = runBlocking {
        val config = RemoteConfig(
            host = "127.0.0.1",
            port = mockServer.port,
            token = "valid_secret_token"
        )
        val backend = RemoteExecBackend(configProvider = { config })

        val successResult = backend.exec(listOf("npm", "test"))
        assertThat(successResult.exitCode).isEqualTo(0)
        assertThat(successResult.stdout).contains("All tests passed")

        val failResult = backend.exec(listOf("invalid", "command"))
        assertThat(failResult.exitCode).isEqualTo(1)
        assertThat(failResult.stdout).contains("Command failed")
    }

    @Test
    fun testRemotePtyFallbackExecution() = runBlocking {
        val config = RemoteConfig(
            host = "127.0.0.1",
            port = mockServer.port,
            token = "valid_secret_token"
        )
        val backend = RemoteExecBackend(configProvider = { config })

        val session = backend.start(listOf("npm", "test"))
        val output = session.output.first()
        val exitCode = session.exitCode.await()

        assertThat(exitCode).isEqualTo(0)
        assertThat(String(output)).contains("All tests passed")
    }
}

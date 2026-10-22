package dev.pocketagent.core.sandbox

import dev.pocketagent.core.model.RemoteConfig
import dev.pocketagent.core.model.RemoteStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class RemoteExecRequest(
    val cmd: List<String>,
    val env: Map<String, String> = emptyMap(),
    val cwd: String = ""
)

@Serializable
data class RemoteExecResponse(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = ""
)

@Serializable
data class RemotePtyStartResponse(
    val sessionId: String,
    val pid: Int = 1
)

class RemoteExecBackend(
    val configProvider: () -> RemoteConfig,
    val client: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 600_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 600_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
) : ExecBackend {

    override val name: String
        get() {
            val cfg = configProvider()
            return "Remote (${cfg.host}:${cfg.port})"
        }

    suspend fun ping(): RemoteStatus {
        val config = configProvider()
        val startNs = System.nanoTime()
        return try {
            val response = client.get("${config.baseUrl}/api/health") {
                if (config.token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer ${config.token}")
                }
            }
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

            if (response.status.isSuccess()) {
                val bodyText = response.bodyAsText()
                val parsed = try {
                    Json.parseToJsonElement(bodyText) as? JsonObject
                } catch (_: Exception) {
                    null
                }

                val os = parsed?.get("os")?.jsonPrimitive?.content ?: "Linux"
                val version = parsed?.get("version")?.jsonPrimitive?.content ?: "1.0.0"
                val uptime = parsed?.get("uptime")?.jsonPrimitive?.longOrNull ?: 0L

                RemoteStatus(
                    isOnline = true,
                    os = os,
                    version = version,
                    uptime = uptime,
                    latencyMs = elapsedMs
                )
            } else {
                RemoteStatus(
                    isOnline = false,
                    latencyMs = elapsedMs,
                    errorMessage = "HTTP ${response.status.value}: ${response.status.description}"
                )
            }
        } catch (e: Exception) {
            RemoteStatus(
                isOnline = false,
                errorMessage = e.message ?: "Connection refused"
            )
        }
    }

    override suspend fun exec(
        cmd: List<String>,
        env: Map<String, String>,
        cwd: String
    ): ExecResult {
        val config = configProvider()
        return try {
            val response = client.post("${config.baseUrl}/api/exec") {
                contentType(ContentType.Application.Json)
                if (config.token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer ${config.token}")
                }
                setBody(
                    RemoteExecRequest(
                        cmd = cmd,
                        env = env,
                        cwd = cwd.ifBlank { config.remoteWorkspacePath }
                    )
                )
            }

            if (response.status.isSuccess()) {
                val execResp = response.body<RemoteExecResponse>()
                val fullOutput = if (execResp.stderr.isNotBlank()) {
                    if (execResp.stdout.isNotBlank()) "${execResp.stdout}\n${execResp.stderr}" else execResp.stderr
                } else {
                    execResp.stdout
                }
                ExecResult(exitCode = execResp.exitCode, stdout = fullOutput)
            } else {
                ExecResult(
                    exitCode = response.status.value,
                    stdout = "Remote execution error: HTTP ${response.status.value} ${response.bodyAsText()}"
                )
            }
        } catch (e: Exception) {
            ExecResult(
                exitCode = -1,
                stdout = "Remote connection failed to ${config.baseUrl}: ${e.localizedMessage}"
            )
        }
    }

    override suspend fun start(
        cmd: List<String>,
        env: Map<String, String>,
        cwd: String
    ): PtySession {
        val config = configProvider()
        return try {
            val response = client.post("${config.baseUrl}/api/pty/start") {
                contentType(ContentType.Application.Json)
                if (config.token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer ${config.token}")
                }
                setBody(
                    RemoteExecRequest(
                        cmd = cmd,
                        env = env,
                        cwd = cwd.ifBlank { config.remoteWorkspacePath }
                    )
                )
            }

            val ptyResp = response.body<RemotePtyStartResponse>()
            RemotePtySession(
                client = client,
                baseUrl = config.baseUrl,
                token = config.token,
                remoteSessionId = ptyResp.sessionId,
                remotePid = ptyResp.pid
            )
        } catch (e: Exception) {
            // If /api/pty is not supported on the remote daemon, fall back to streaming exec
            RemoteFallbackPtySession(
                remoteExecBackend = this,
                cmd = cmd,
                env = env,
                cwd = cwd
            )
        }
    }
}

class RemotePtySession(
    private val client: HttpClient,
    private val baseUrl: String,
    private val token: String,
    val remoteSessionId: String,
    val remotePid: Int
) : PtySession {

    override val pid: Int = remotePid

    private val _exitCode = CompletableDeferred<Int>()
    override val exitCode: Deferred<Int> = _exitCode

    override val output: Flow<ByteArray> = flow {
        try {
            val response = client.get("$baseUrl/api/pty/stream?sessionId=$remoteSessionId") {
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(4096)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                emit(buffer.copyOf(read))
            }
            _exitCode.complete(0)
        } catch (e: Exception) {
            _exitCode.complete(-1)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun write(data: ByteArray) {
        try {
            client.post("$baseUrl/api/pty/write?sessionId=$remoteSessionId") {
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(data)
            }
        } catch (_: Exception) {
        }
    }

    override suspend fun resize(cols: Int, rows: Int) {
        try {
            client.post("$baseUrl/api/pty/resize?sessionId=$remoteSessionId&cols=$cols&rows=$rows") {
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        } catch (_: Exception) {
        }
    }

    override suspend fun kill(signal: Int) {
        try {
            client.post("$baseUrl/api/pty/kill?sessionId=$remoteSessionId&signal=$signal") {
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            _exitCode.complete(128 + signal)
        } catch (_: Exception) {
            _exitCode.complete(-1)
        }
    }
}

class RemoteFallbackPtySession(
    private val remoteExecBackend: RemoteExecBackend,
    private val cmd: List<String>,
    private val env: Map<String, String>,
    private val cwd: String
) : PtySession {

    override val pid: Int = 1000

    private val _exitCode = CompletableDeferred<Int>()
    override val exitCode: Deferred<Int> = _exitCode

    override val output: Flow<ByteArray> = flow {
        val result = remoteExecBackend.exec(cmd, env, cwd)
        emit(result.stdout.toByteArray(Charsets.UTF_8))
        _exitCode.complete(result.exitCode)
    }.flowOn(Dispatchers.IO)

    override suspend fun write(data: ByteArray) {
        // Non-interactive fallback
    }

    override suspend fun resize(cols: Int, rows: Int) {
    }

    override suspend fun kill(signal: Int) {
        _exitCode.complete(128 + signal)
    }
}

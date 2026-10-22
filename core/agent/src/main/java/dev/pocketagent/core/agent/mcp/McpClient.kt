package dev.pocketagent.core.agent.mcp

import dev.pocketagent.core.model.McpServerConfig
import dev.pocketagent.core.model.McpToolDefinition
import dev.pocketagent.core.model.McpTransportType
import dev.pocketagent.core.sandbox.ExecBackend
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicInteger

open class McpClient(
    val config: McpServerConfig,
    private val execBackend: ExecBackend? = null,
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 30000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
) {
    private val idCounter = AtomicInteger(1)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Initializes connection with the MCP server, verifies protocol version, and returns discovered tools.
     */
    open suspend fun initialize(): List<McpToolDefinition> = withContext(Dispatchers.IO) {
        // 1. Send 'initialize'
        val initParams = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject {
                put("name", "PocketAgent")
                put("version", "1.0.0")
            })
        }

        val initResponse = sendJsonRpcRequest("initialize", initParams)
        val initResult = initResponse["result"]?.jsonObject
            ?: throw IllegalStateException("MCP Initialize failed: ${initResponse["error"] ?: "Invalid response"}")

        // 2. Send initialized notification (optional for some servers, standard in MCP spec)
        try {
            sendJsonRpcNotification("notifications/initialized", buildJsonObject {})
        } catch (_: Throwable) {
            // Notifications are fire-and-forget
        }

        // 3. Query 'tools/list'
        val toolsResponse = sendJsonRpcRequest("tools/list", buildJsonObject {})
        val toolsResult = toolsResponse["result"]?.jsonObject
            ?: throw IllegalStateException("MCP tools/list failed: ${toolsResponse["error"] ?: "Invalid response"}")

        val toolsArray = toolsResult["tools"]?.jsonArray ?: JsonArray(emptyList())
        val tools = toolsArray.mapNotNull { item ->
            val obj = item.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val schema = obj["inputSchema"]?.toString() ?: "{}"
            McpToolDefinition(name = name, description = desc, inputSchemaJson = schema)
        }

        tools
    }

    /**
     * Calls a specific tool on the MCP server and returns the raw output string.
     */
    open suspend fun callTool(toolName: String, argumentsJson: String): String = withContext(Dispatchers.IO) {
        val parsedArgs = try {
            json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Throwable) {
            buildJsonObject {}
        }

        val params = buildJsonObject {
            put("name", toolName)
            put("arguments", parsedArgs)
        }

        val response = sendJsonRpcRequest("tools/call", params)
        if (response.containsKey("error")) {
            val errObj = response["error"]?.jsonObject
            val msg = errObj?.get("message")?.jsonPrimitive?.contentOrNull ?: "Unknown MCP error"
            val code = errObj?.get("code")?.jsonPrimitive?.intOrNull ?: -1
            return@withContext "Error ($code): $msg"
        }

        val result = response["result"]?.jsonObject
            ?: return@withContext "Error: Missing result in MCP response"

        val isError = result["isError"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
        val contentArray = result["content"]?.jsonArray ?: JsonArray(emptyList())

        val textOutput = contentArray.joinToString("\n") { element ->
            val obj = element.jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> obj["text"]?.jsonPrimitive?.contentOrNull ?: ""
                "resource" -> obj["resource"]?.toString() ?: ""
                "image" -> "[Image artifact returned: ${obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: "image/png"}]"
                else -> element.toString()
            }
        }

        if (isError) {
            "Error from MCP Tool:\n$textOutput"
        } else {
            textOutput
        }
    }

    /**
     * Sends a JSON-RPC 2.0 request and awaits the response.
     */
    suspend fun sendJsonRpcRequest(method: String, params: JsonObject): JsonObject {
        val requestId = idCounter.getAndIncrement()
        val requestJson = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId)
            put("method", method)
            put("params", params)
        }

        return when (config.transportType) {
            McpTransportType.HTTP_SSE -> {
                sendHttpJsonRpc(requestJson)
            }
            McpTransportType.STDIO -> {
                sendStdioJsonRpc(requestJson)
            }
        }
    }

    /**
     * Sends a JSON-RPC 2.0 notification (no ID, no response expected).
     */
    suspend fun sendJsonRpcNotification(method: String, params: JsonObject) {
        val notificationJson = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }

        when (config.transportType) {
            McpTransportType.HTTP_SSE -> {
                try {
                    sendHttpJsonRpc(notificationJson)
                } catch (_: Throwable) {}
            }
            McpTransportType.STDIO -> {
                try {
                    sendStdioJsonRpc(notificationJson)
                } catch (_: Throwable) {}
            }
        }
    }

    private suspend fun sendHttpJsonRpc(bodyJson: JsonObject): JsonObject {
        val targetUrl = config.url.trim()
        if (targetUrl.isBlank()) {
            throw IllegalArgumentException("MCP server URL cannot be blank.")
        }

        val httpResponse = httpClient.post(targetUrl) {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json, text/event-stream")
            setBody(bodyJson.toString())
        }

        val rawText: String = httpResponse.body()
        return try {
            json.parseToJsonElement(rawText).jsonObject
        } catch (t: Throwable) {
            // Could be SSE formatted event e.g. "data: {...}"
            val dataLine = rawText.lines().firstOrNull { it.startsWith("data:") }
            if (dataLine != null) {
                val jsonPart = dataLine.removePrefix("data:").trim()
                json.parseToJsonElement(jsonPart).jsonObject
            } else {
                throw IllegalStateException("Failed to parse JSON-RPC response: $rawText", t)
            }
        }
    }

    private suspend fun sendStdioJsonRpc(bodyJson: JsonObject): JsonObject {
        val backend = execBackend
            ?: throw IllegalStateException("ExecBackend is required for STDIO MCP transport.")

        if (config.command.isEmpty()) {
            throw IllegalArgumentException("MCP STDIO command cannot be empty.")
        }

        val singleLine = bodyJson.toString().replace("\n", "")
        // Run command passing input via pipe or argument
        val cmd = config.command
        val res = backend.exec(cmd)
        val stdout = res.stdout.trim()
        val lastJsonLine = stdout.lines().lastOrNull { it.trim().startsWith("{") }
            ?: throw IllegalStateException("No valid JSON-RPC output from STDIO process:\n$stdout\nStderr:\n${res.stderr}")

        return json.parseToJsonElement(lastJsonLine).jsonObject
    }

    fun close() {
        try {
            httpClient.close()
        } catch (_: Throwable) {}
    }
}

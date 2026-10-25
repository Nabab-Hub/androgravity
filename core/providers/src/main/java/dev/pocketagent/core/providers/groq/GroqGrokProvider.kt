package dev.pocketagent.core.providers.groq

import dev.pocketagent.core.model.ChatEvent
import dev.pocketagent.core.model.ChatRequest
import dev.pocketagent.core.model.Message
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.core.providers.ModelProvider
import dev.pocketagent.core.providers.sse.SseParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class GroqGrokProvider(
    private val apiKeyProvider: () -> String,
    private val client: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
    }
) : ModelProvider {

    override val id: String = "groq-grok"
    override val name: String = "Groq / xAI Grok"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val activeKeyIndex = AtomicInteger(0)

    enum class StreamResult {
        SUCCESS,
        RETRY_NEXT_KEY,
        FATAL_ERROR
    }

    override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
        val rawKeys = apiKeyProvider()
        val allKeys = parseKeys(rawKeys)
        if (allKeys.isEmpty()) {
            emit(ChatEvent.Error("Groq / Grok API key is not configured. Please add one or more keys in Settings."))
            return@flow
        }

        val modelName = resolveModel(request.model)
        val url = resolveEndpoint(modelName, allKeys.firstOrNull() ?: "")
        val requestBody = buildRequestBody(request, modelName)

        val totalKeys = allKeys.size
        val startIndex = (activeKeyIndex.get() % totalKeys + totalKeys) % totalKeys

        for (offset in 0 until totalKeys) {
            val keyIndex = (startIndex + offset) % totalKeys
            val currentKey = allKeys[keyIndex]

            val result = tryExecuteStream(url, currentKey, requestBody, this)
            when (result) {
                StreamResult.SUCCESS -> {
                    activeKeyIndex.set(keyIndex)
                    break
                }
                StreamResult.RETRY_NEXT_KEY -> {
                    if (totalKeys > 1 && offset < totalKeys - 1) {
                        val nextIdx = (keyIndex + 1) % totalKeys + 1
                        emit(ChatEvent.TextDelta("*(Groq/Grok Key ${keyIndex + 1} exhausted or rate-limited. Auto-switching to Key $nextIdx of $totalKeys...)*\n\n"))
                    }
                    continue
                }
                StreamResult.FATAL_ERROR -> {
                    break
                }
            }
        }
    }

    private suspend fun tryExecuteStream(
        url: String,
        apiKey: String,
        requestBody: JsonObject,
        collector: kotlinx.coroutines.flow.FlowCollector<ChatEvent>
    ): StreamResult {
        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.Accept, "text/event-stream")
                setBody(requestBody.toString())
            }

            val statusCode = response.status.value
            if (statusCode !in 200..299) {
                val errorBody: String = try { response.body() } catch (_: Throwable) { "" }
                if (statusCode == 429 || statusCode == 403 || statusCode == 401) {
                    return StreamResult.RETRY_NEXT_KEY
                }
                collector.emit(ChatEvent.Error("Provider API returned status $statusCode: $errorBody"))
                return StreamResult.FATAL_ERROR
            }

            val channel = response.bodyAsChannel()
            val sseParser = SseParser()
            val toolCallsByIndex = mutableMapOf<Int, MutableMap<String, String>>()

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val sse = sseParser.parseLine(line)
                if (sse != null && sse.data.isNotBlank()) {
                    if (sse.data.trim() == "[DONE]") {
                        break
                    }
                    try {
                        val jsonElement = json.parseToJsonElement(sse.data).jsonObject

                        val usage = jsonElement["usage"]?.jsonObject
                        if (usage != null) {
                            val promptTokens = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                            val completionTokens = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                            val totalTokens = usage["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                            collector.emit(ChatEvent.Usage(promptTokens, completionTokens, totalTokens))
                        }

                        val choices = jsonElement["choices"]?.jsonArray
                        if (choices != null && choices.isNotEmpty()) {
                            val firstChoice = choices[0].jsonObject
                            val delta = firstChoice["delta"]?.jsonObject

                            val text = delta?.get("content")?.jsonPrimitive?.content
                            if (!text.isNullOrEmpty()) {
                                collector.emit(ChatEvent.TextDelta(text))
                            }

                            val streamToolCalls = delta?.get("tool_calls")?.jsonArray
                            if (streamToolCalls != null) {
                                for (tc in streamToolCalls) {
                                    val tcObj = tc.jsonObject
                                    val index = tcObj["index"]?.jsonPrimitive?.intOrNull ?: 0
                                    val callMap = toolCallsByIndex.getOrPut(index) { mutableMapOf() }

                                    val id = tcObj["id"]?.jsonPrimitive?.content
                                    if (id != null) callMap["id"] = id

                                    val func = tcObj["function"]?.jsonObject
                                    if (func != null) {
                                        val name = func["name"]?.jsonPrimitive?.content
                                        if (name != null) callMap["name"] = (callMap["name"] ?: "") + name

                                        val args = func["arguments"]?.jsonPrimitive?.content
                                        if (args != null) callMap["arguments"] = (callMap["arguments"] ?: "") + args
                                    }
                                }
                            }

                            val finishReason = firstChoice["finish_reason"]?.jsonPrimitive?.content
                            if (finishReason != null) {
                                for ((_, callMap) in toolCallsByIndex) {
                                    val callId = callMap["id"] ?: UUID.randomUUID().toString()
                                    val callName = callMap["name"] ?: "tool"
                                    val callArgs = callMap["arguments"] ?: "{}"
                                    collector.emit(ChatEvent.ToolCallChunk(ToolCall(callId, callName, callArgs)))
                                }
                                toolCallsByIndex.clear()
                                collector.emit(ChatEvent.Done(finishReason))
                            }
                        }
                    } catch (_: Exception) {
                        // ignore malformed SSE chunks
                    }
                }
            }

            for ((_, callMap) in toolCallsByIndex) {
                val callId = callMap["id"] ?: UUID.randomUUID().toString()
                val callName = callMap["name"] ?: "tool"
                val callArgs = callMap["arguments"] ?: "{}"
                collector.emit(ChatEvent.ToolCallChunk(ToolCall(callId, callName, callArgs)))
            }

            collector.emit(ChatEvent.Done())
            return StreamResult.SUCCESS
        } catch (e: Exception) {
            return StreamResult.RETRY_NEXT_KEY
        }
    }

    private fun resolveEndpoint(model: String, key: String): String {
        return if (model.startsWith("grok") || key.startsWith("xai-")) {
            "https://api.x.ai/v1/chat/completions"
        } else {
            "https://api.groq.com/openai/v1/chat/completions"
        }
    }

    private fun resolveModel(model: String): String {
        val clean = model.trim()
        return when {
            clean.isBlank() -> DEFAULT_MODEL
            clean.equals("qwen", ignoreCase = true) -> "qwen-2.5-coder-32b"
            clean.equals("grok", ignoreCase = true) -> "grok-2-latest"
            else -> clean
        }
    }

    private fun buildRequestBody(request: ChatRequest, modelName: String): JsonObject {
        return buildJsonObject {
            put("model", modelName)
            put("stream", true)
            put("messages", buildJsonArray {
                if (request.systemPrompt.isNotBlank()) {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", request.systemPrompt)
                    })
                }

                for (msg in request.messages) {
                    when (msg) {
                        is Message.User -> {
                            add(buildJsonObject {
                                put("role", "user")
                                put("content", msg.text)
                            })
                        }
                        is Message.Assistant -> {
                            add(buildJsonObject {
                                put("role", "assistant")
                                put("content", msg.text)
                                if (msg.toolCalls.isNotEmpty()) {
                                    put("tool_calls", buildJsonArray {
                                        for (tc in msg.toolCalls) {
                                            add(buildJsonObject {
                                                put("id", tc.id)
                                                put("type", "function")
                                                put("function", buildJsonObject {
                                                    put("name", tc.name)
                                                    put("arguments", tc.argsJson)
                                                })
                                            })
                                        }
                                    })
                                }
                            })
                        }
                        is Message.ToolResult -> {
                            add(buildJsonObject {
                                put("role", "tool")
                                put("tool_call_id", msg.callId)
                                put("content", msg.output)
                            })
                        }
                        is Message.System -> {
                            add(buildJsonObject {
                                put("role", "system")
                                put("content", msg.text)
                            })
                        }
                    }
                }
            })

            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in request.tools) {
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                try {
                                    put("parameters", json.parseToJsonElement(tool.parametersJson))
                                } catch (_: Exception) {
                                    put("parameters", buildJsonObject {})
                                }
                            })
                        })
                    }
                })
            }

            request.temperature?.let { put("temperature", it) }
            request.maxTokens?.let { put("max_tokens", it) }
        }
    }

    companion object {
        const val DEFAULT_MODEL = "qwen-2.5-coder-32b"

        fun parseKeys(raw: String): List<String> {
            return raw.split(Regex("[,;\\r?\\n]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
    }
}

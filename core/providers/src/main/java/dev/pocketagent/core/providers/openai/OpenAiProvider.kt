package dev.pocketagent.core.providers.openai

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

class OpenAiProvider(
    private val apiKeyProvider: () -> String,
    private val client: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
    }
) : ModelProvider {

    override val id: String = "openai"
    override val name: String = "OpenAI ChatGPT"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            emit(ChatEvent.Error("OpenAI API key is not configured. Please add it in Settings."))
            return@flow
        }

        val modelName = request.model.ifBlank { DEFAULT_MODEL }
        val url = "https://api.openai.com/v1/chat/completions"
        val requestBody = buildOpenAiRequestBody(request, modelName)

        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.Accept, "text/event-stream")
                setBody(requestBody.toString())
            }

            if (response.status.value !in 200..299) {
                val errorBody: String = response.body()
                emit(ChatEvent.Error("OpenAI API returned status ${response.status.value}: $errorBody"))
                return@flow
            }

            val channel = response.bodyAsChannel()
            val sseParser = SseParser()

            // Accumulate streaming tool calls
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

                        // Extract usage if present
                        val usage = jsonElement["usage"]?.jsonObject
                        if (usage != null) {
                            val promptTokens = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                            val completionTokens = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                            val totalTokens = usage["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                            emit(ChatEvent.Usage(promptTokens, completionTokens, totalTokens))
                        }

                        val choices = jsonElement["choices"]?.jsonArray
                        if (choices != null && choices.isNotEmpty()) {
                            val firstChoice = choices[0].jsonObject
                            val delta = firstChoice["delta"]?.jsonObject

                            // Text delta
                            val text = delta?.get("content")?.jsonPrimitive?.content
                            if (text != null && text.isNotEmpty()) {
                                emit(ChatEvent.TextDelta(text))
                            }

                            // Tool calls streaming
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
                                // Flush all accumulated tool calls
                                for ((_, callMap) in toolCallsByIndex) {
                                    val callId = callMap["id"] ?: UUID.randomUUID().toString()
                                    val callName = callMap["name"] ?: "tool"
                                    val callArgs = callMap["arguments"] ?: "{}"
                                    emit(ChatEvent.ToolCallChunk(ToolCall(callId, callName, callArgs)))
                                }
                                toolCallsByIndex.clear()
                                emit(ChatEvent.Done(finishReason))
                            }
                        }
                    } catch (_: Exception) {
                        // Skip malformed chunk
                    }
                }
            }

            // Flush remaining tool calls if any
            for ((_, callMap) in toolCallsByIndex) {
                val callId = callMap["id"] ?: UUID.randomUUID().toString()
                val callName = callMap["name"] ?: "tool"
                val callArgs = callMap["arguments"] ?: "{}"
                emit(ChatEvent.ToolCallChunk(ToolCall(callId, callName, callArgs)))
            }
            emit(ChatEvent.Done())
        } catch (t: Throwable) {
            emit(ChatEvent.Error("OpenAI connection error: ${t.message ?: "Unknown"}", t))
        }
    }

    private fun buildOpenAiRequestBody(request: ChatRequest, modelName: String): JsonObject {
        return buildJsonObject {
            put("model", modelName)
            put("stream", true)
            put("stream_options", buildJsonObject { put("include_usage", true) })

            // Messages
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

            // Tools
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
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}

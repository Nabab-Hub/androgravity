package dev.pocketagent.core.providers.gemini

import dev.pocketagent.core.model.ChatEvent
import dev.pocketagent.core.model.ChatRequest
import dev.pocketagent.core.model.Message
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.core.providers.ModelProvider
import dev.pocketagent.core.providers.sse.parseSse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import io.ktor.client.engine.okhttp.OkHttp
import java.util.UUID

class GeminiProvider(
    private val apiKeyProvider: () -> String,
    private val client: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
    }
) : ModelProvider {

    override val id: String = "gemini"
    override val name: String = "Google Gemini"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            emit(ChatEvent.Error("Gemini API key is not configured. Please add it in Settings."))
            return@flow
        }

        val effectiveModel = resolveGeminiModel(request.model)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:streamGenerateContent?alt=sse&key=$apiKey"

        val requestBody = buildGeminiRequestBody(request)

        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "text/event-stream")
                header("x-goog-api-key", apiKey)
                setBody(requestBody.toString())
            }

            if (response.status.value !in 200..299) {
                val errorBody: String = response.body()
                val hint = if (response.status.value == 403) {
                    "\n\nHint: Ensure your API key is generated from Google AI Studio (aistudio.google.com). Or switch to ChatGPT / gpt-4o-mini in Settings."
                } else ""
                emit(ChatEvent.Error("Gemini API returned status ${response.status.value}: $errorBody$hint"))
                return@flow
            }

            val channel = response.bodyAsChannel()
            val sseParser = dev.pocketagent.core.providers.sse.SseParser()

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val sse = sseParser.parseLine(line)
                if (sse != null && sse.data.isNotBlank()) {
                    try {
                        val jsonElement = json.parseToJsonElement(sse.data).jsonObject

                        // Extract usage if available
                        val usage = jsonElement["usageMetadata"]?.jsonObject
                        if (usage != null) {
                            val promptTokens = usage["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
                            val candidateTokens = usage["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
                            val totalTokens = usage["totalTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
                            emit(ChatEvent.Usage(promptTokens, candidateTokens, totalTokens))
                        }

                        // Extract candidates
                        val candidates = jsonElement["candidates"]?.jsonArray
                        if (candidates != null && candidates.isNotEmpty()) {
                            val firstCandidate = candidates[0].jsonObject
                            val finishReason = firstCandidate["finishReason"]?.jsonPrimitive?.contentOrNull
                            val content = firstCandidate["content"]?.jsonObject
                            val parts = content?.get("parts")?.jsonArray

                            if (parts != null) {
                                for (part in parts) {
                                    val partObj = part.jsonObject
                                    val text = partObj["text"]?.jsonPrimitive?.contentOrNull
                                    if (text != null && text.isNotEmpty()) {
                                        emit(ChatEvent.TextDelta(text))
                                    }

                                    val functionCall = partObj["functionCall"]?.jsonObject
                                    if (functionCall != null) {
                                        val name = functionCall["name"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                                        val args = functionCall["args"]?.jsonObject?.toString() ?: "{}"
                                        val callId = UUID.randomUUID().toString()
                                        emit(ChatEvent.ToolCallChunk(ToolCall(callId, name, args)))
                                    }
                                }
                            }

                            if (finishReason != null && finishReason != "STOP") {
                                emit(ChatEvent.Done(finishReason))
                            }
                        }
                    } catch (e: Exception) {
                        // Tolerate partial / unparseable intermediate chunks
                    }
                }
            }

            emit(ChatEvent.Done())
        } catch (e: Exception) {
            emit(ChatEvent.Error(e.message ?: "Unknown network error", e))
        }
    }

    private fun buildGeminiRequestBody(request: ChatRequest): JsonObject {
        return buildJsonObject {
            // Contents
            put("contents", buildJsonArray {
                for (message in request.messages) {
                    when (message) {
                        is Message.User -> {
                            add(buildJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray {
                                    add(buildJsonObject { put("text", message.text) })
                                })
                            })
                        }
                        is Message.Assistant -> {
                            add(buildJsonObject {
                                put("role", "model")
                                put("parts", buildJsonArray {
                                    if (message.text.isNotEmpty()) {
                                        add(buildJsonObject { put("text", message.text) })
                                    }
                                    for (toolCall in message.toolCalls) {
                                        add(buildJsonObject {
                                            put("functionCall", buildJsonObject {
                                                put("name", toolCall.name)
                                                try {
                                                    val parsedArgs = json.parseToJsonElement(toolCall.argsJson)
                                                    put("args", parsedArgs)
                                                } catch (e: Exception) {
                                                    put("args", buildJsonObject {})
                                                }
                                            })
                                        })
                                    }
                                })
                            })
                        }
                        is Message.ToolResult -> {
                            add(buildJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("functionResponse", buildJsonObject {
                                            put("name", message.toolName)
                                            put("response", buildJsonObject {
                                                put("output", message.output)
                                                put("ok", message.ok)
                                            })
                                        })
                                    })
                                })
                            })
                        }
                        is Message.System -> {
                            // Handled separately below in systemInstruction
                        }
                    }
                }
            })

            // System instructions
            if (request.systemPrompt.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", request.systemPrompt) })
                    })
                })
            }

            // Tools (Function Declarations)
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            for (tool in request.tools) {
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    try {
                                        val params = json.parseToJsonElement(tool.parametersJson)
                                        put("parameters", params)
                                    } catch (e: Exception) {
                                        // Ignore schema parse issues
                                    }
                                })
                            }
                        })
                    })
                })
            }

            // Generation config
            put("generationConfig", buildJsonObject {
                request.temperature?.let { put("temperature", it) }
                request.maxTokens?.let { put("maxOutputTokens", it) }
            })
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-1.5-flash"

        fun resolveGeminiModel(model: String): String {
            val clean = model.lowercase().trim()
            return when {
                clean.contains("3.7") || clean.contains("3.8") || clean.contains("2.5") || clean.contains("2.0") -> "gemini-2.0-flash"
                clean.contains("1.5-pro") || clean.contains("pro") -> "gemini-1.5-pro"
                clean.contains("1.5-flash") || clean.contains("flash") -> "gemini-1.5-flash"
                clean.isNotBlank() -> clean
                else -> DEFAULT_MODEL
            }
        }
    }
}

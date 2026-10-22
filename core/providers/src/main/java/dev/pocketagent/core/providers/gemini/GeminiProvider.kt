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
        val requestBody = buildGeminiRequestBody(request)

        val success = tryExecuteModelStream(effectiveModel, apiKey, requestBody, this)
        if (!success && effectiveModel != FALLBACK_MODEL) {
            emit(ChatEvent.TextDelta("*(Gemini model '$effectiveModel' was temporarily unavailable. Automatically fell back to $FALLBACK_MODEL)*\n\n"))
            tryExecuteModelStream(FALLBACK_MODEL, apiKey, requestBody, this)
        }
    }

    private suspend fun tryExecuteModelStream(
        modelName: String,
        apiKey: String,
        requestBody: JsonObject,
        collector: kotlinx.coroutines.flow.FlowCollector<ChatEvent>
    ): Boolean {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?alt=sse&key=$apiKey"

        val response = try {
            client.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "text/event-stream")
                header("x-goog-api-key", apiKey)
                setBody(requestBody.toString())
            }
        } catch (e: Exception) {
            if (modelName != FALLBACK_MODEL) {
                // Return false to allow fallback attempt
                return false
            }
            collector.emit(ChatEvent.Error("Gemini connection error (${e.javaClass.simpleName}): ${e.message}", e))
            return true
        }

        if (response.status.value !in 200..299) {
            if (modelName != FALLBACK_MODEL && response.status.value in listOf(503, 404, 500)) {
                // High demand / model not found, fallback to reliable 1.5 flash
                return false
            }

            val errorBody: String = try { response.body() } catch (_: Exception) { "" }
            val hint = when (response.status.value) {
                403 -> "\n\nHint: Permission Denied. Your Google Cloud project or key is restricted. Please create a fresh API key at https://aistudio.google.com/app/apikey (choose default project) and save it in Settings."
                404 -> "\n\nHint: Model '$modelName' was not found on Google's API endpoint. Use Gemini 1.5 Flash in Settings."
                503 -> "\n\nHint: Model is currently overloaded on Google's servers. Please try again or switch to Gemini 1.5 Flash."
                else -> ""
            }
            collector.emit(ChatEvent.Error("Gemini API returned status ${response.status.value}: $errorBody$hint"))
            return true
        }

        try {
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
                            collector.emit(ChatEvent.Usage(promptTokens, candidateTokens, totalTokens))
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
                                        collector.emit(ChatEvent.TextDelta(text))
                                    }

                                    val functionCall = partObj["functionCall"]?.jsonObject
                                    if (functionCall != null) {
                                        val name = functionCall["name"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                                        val args = functionCall["args"]?.jsonObject?.toString() ?: "{}"
                                        val callId = UUID.randomUUID().toString()
                                        collector.emit(ChatEvent.ToolCallChunk(ToolCall(callId, name, args)))
                                    }
                                }
                            }

                            if (finishReason != null && finishReason != "STOP") {
                                collector.emit(ChatEvent.Done(finishReason))
                            }
                        }
                    } catch (e: Exception) {
                        // Tolerate partial / unparseable intermediate chunks
                    }
                }
            }

            collector.emit(ChatEvent.Done())
            return true
        } catch (e: Exception) {
            if (modelName != FALLBACK_MODEL) {
                return false
            }
            collector.emit(ChatEvent.Error(e.message ?: "Unknown network error", e))
            return true
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
        const val FALLBACK_MODEL = "gemini-1.5-flash"

        fun resolveGeminiModel(model: String): String {
            val clean = model.trim().removePrefix("models/")
            return when {
                clean.isBlank() -> DEFAULT_MODEL
                clean == "gemini-1.5-flash" -> "gemini-1.5-flash"
                clean == "gemini-1.5-pro" -> "gemini-1.5-pro"
                clean == "gemini-2.0-flash" -> "gemini-2.0-flash"
                clean == "gemini-2.0-flash-exp" -> "gemini-2.0-flash"
                clean.contains("3.7") || clean.contains("3.8") || clean.contains("2.5") -> DEFAULT_MODEL
                else -> clean
            }
        }
    }
}

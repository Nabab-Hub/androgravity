package dev.pocketagent.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val systemPrompt: String = "",
    val messages: List<Message>,
    val tools: List<ToolSpec> = emptyList(),
    val temperature: Float? = null,
    val maxTokens: Int? = null
)

sealed interface ChatEvent {
    data class TextDelta(val text: String) : ChatEvent
    data class ToolCallChunk(val call: ToolCall) : ChatEvent
    data class Usage(val promptTokens: Int, val candidateTokens: Int, val totalTokens: Int) : ChatEvent
    data class Done(val finishReason: String? = null) : ChatEvent
    data class Error(val message: String, val throwable: Throwable? = null) : ChatEvent
}

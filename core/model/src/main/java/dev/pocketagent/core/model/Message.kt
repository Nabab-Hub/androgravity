package dev.pocketagent.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed interface Message {
    val id: String
    val timestamp: Long

    @Serializable
    data class User(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val attachments: List<String> = emptyList(),
        override val timestamp: Long = java.lang.System.currentTimeMillis()
    ) : Message

    @Serializable
    data class Assistant(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val toolCalls: List<ToolCall> = emptyList(),
        override val timestamp: Long = java.lang.System.currentTimeMillis()
    ) : Message

    @Serializable
    data class ToolResult(
        override val id: String = UUID.randomUUID().toString(),
        val callId: String,
        val toolName: String,
        val ok: Boolean,
        val output: String,
        override val timestamp: Long = java.lang.System.currentTimeMillis()
    ) : Message

    @Serializable
    data class System(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        override val timestamp: Long = java.lang.System.currentTimeMillis()
    ) : Message
}

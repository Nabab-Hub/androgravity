package dev.pocketagent.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val argsJson: String
)

@Serializable
data class ToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String
)

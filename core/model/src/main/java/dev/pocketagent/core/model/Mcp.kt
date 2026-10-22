package dev.pocketagent.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class McpTransportType {
    HTTP_SSE,
    STDIO
}

@Serializable
enum class McpPolicy {
    ALLOW,
    ASK,
    DENY
}

@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String = "",
    val inputSchemaJson: String = "{}"
)

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val transportType: McpTransportType = McpTransportType.HTTP_SSE,
    val url: String = "",
    val command: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    val policy: McpPolicy = McpPolicy.ASK,
    val tools: List<McpToolDefinition> = emptyList(),
    val lastConnectedTimeMs: Long? = null
)

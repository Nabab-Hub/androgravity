package dev.pocketagent.core.agent.mcp

import dev.pocketagent.core.agent.tools.Tool
import dev.pocketagent.core.agent.tools.ToolContext
import dev.pocketagent.core.model.McpToolDefinition

class McpTool(
    val serverName: String,
    val toolDef: McpToolDefinition,
    private val clientProvider: () -> McpClient
) : Tool {
    override val name: String = "mcp.${serverName}.${toolDef.name}"
    override val description: String = "[MCP: $serverName] ${toolDef.description.ifBlank { "Tool from MCP server $serverName" }}"
    override val parametersSchema: String = toolDef.inputSchemaJson
    override val isReadOnly: Boolean = false

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val client = clientProvider()
        val rawOutput = client.callTool(toolDef.name, argsJson)

        // Strict security boundary: MCP outputs are treated as untrusted external content
        return """
            [MCP Tool Result from '$serverName/${toolDef.name}'] (Untrusted external content):
            $rawOutput
        """.trimIndent()
    }
}

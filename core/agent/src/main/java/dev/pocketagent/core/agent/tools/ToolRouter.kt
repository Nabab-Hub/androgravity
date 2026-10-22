package dev.pocketagent.core.agent.tools

import dev.pocketagent.core.model.Message
import dev.pocketagent.core.model.ToolCall
import java.util.concurrent.ConcurrentHashMap

class ToolRouter(
    initialTools: List<Tool> = listOf(
        FsReadFileTool(),
        FsWriteFileTool(),
        FsEditFileTool(),
        FsListDirTool(),
        FsDeleteFileTool(),
        SearchGrepTool(),
        SearchGlobTool(),
        TerminalRunTool(),
        GitStatusTool(),
        GitDiffTool(),
        GitCommitTool(),
        GitLogTool(),
        GitBranchTool(),
        ArtifactWriteTool(),
        BrowserOpenTool(),
        BrowserScreenshotTool(),
        BrowserClickTool(),
        BrowserTypeTool(),
        BrowserEvalTool(),
        BrowserConsoleTool()
    )
) {
    private val tools = ConcurrentHashMap<String, Tool>()
    private val ansiRegex = Regex("""\u001B\[[;?0-9]*[a-zA-Z]""")

    init {
        initialTools.forEach { registerTool(it) }
    }

    fun registerTool(tool: Tool) {
        tools[tool.name] = tool
    }

    fun unregisterTool(name: String) {
        tools.remove(name)
    }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllTools(): List<Tool> = tools.values.toList()

    suspend fun execute(toolCall: ToolCall, context: ToolContext): Message.ToolResult {
        val tool = tools[toolCall.name]
            ?: return Message.ToolResult(
                callId = toolCall.id,
                toolName = toolCall.name,
                ok = false,
                output = "Error: Unrecognized tool '${toolCall.name}'. Available tools: ${tools.keys.joinToString()}"
            )

        val startTime = System.currentTimeMillis()
        return try {
            val rawOutput = tool.execute(toolCall.argsJson, context)
            val durationMs = System.currentTimeMillis() - startTime
            val cleaned = stripAnsi(rawOutput)
            val truncated = truncateOutput(cleaned)
            Message.ToolResult(
                callId = toolCall.id,
                toolName = toolCall.name,
                ok = !truncated.startsWith("Error:"),
                output = "$truncated\n(Duration: ${durationMs}ms)"
            )
        } catch (t: Throwable) {
            val durationMs = System.currentTimeMillis() - startTime
            Message.ToolResult(
                callId = toolCall.id,
                toolName = toolCall.name,
                ok = false,
                output = "Tool Execution Error: ${t.message ?: t.javaClass.simpleName} (Duration: ${durationMs}ms)"
            )
        }
    }

    fun stripAnsi(text: String): String = ansiRegex.replace(text, "")

    fun truncateOutput(output: String, maxBytes: Int = 32768): String {
        if (output.length <= maxBytes) return output
        val half = maxBytes / 2
        val head = output.take(half)
        val tail = output.takeLast(half)
        val omitted = output.length - (head.length + tail.length)
        return "$head\n\n[... Truncated $omitted characters to preserve context window ...]\n\n$tail"
    }
}

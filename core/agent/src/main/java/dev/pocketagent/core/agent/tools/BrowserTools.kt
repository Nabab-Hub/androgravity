package dev.pocketagent.core.agent.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class BrowserOpenTool : Tool {
    override val name: String = "browser_open"
    override val description: String = "Open a URL in the in-app browser or preview WebView (e.g. http://localhost:3000)."
    override val isReadOnly: Boolean = false
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "url": { "type": "string", "description": "URL to navigate to (e.g. http://localhost:3000)" }
          },
          "required": ["url"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val url = root["url"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'url' argument")

        val controller = context.browserController
            ?: return "Error: Browser/Preview controller is not attached or active."

        val success = controller.openUrl(url)
        return if (success) {
            "Successfully navigated to $url"
        } else {
            "Failed to navigate to $url"
        }
    }
}

class BrowserScreenshotTool : Tool {
    override val name: String = "browser_screenshot"
    override val description: String = "Capture a screenshot of the current browser page and save it as an artifact under screens/."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "name": { "type": "string", "description": "Optional name for screenshot (without extension)" }
          }
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = try { Json.parseToJsonElement(argsJson).jsonObject } catch (_: Throwable) { null }
        val customName = root?.get("name")?.jsonPrimitive?.content?.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        val controller = context.browserController
            ?: return "Error: Browser/Preview controller is not attached or active."

        val screensDir = File(context.workspaceRoot, ".agent/artifacts/${context.sessionId}/screens")
        screensDir.mkdirs()

        val fileName = if (!customName.isNullOrBlank()) {
            "${customName}.png"
        } else {
            "screen_${System.currentTimeMillis()}.png"
        }
        val targetFile = File(screensDir, fileName)

        return try {
            val savedPath = controller.takeScreenshot(targetFile)
            val relPath = ".agent/artifacts/${context.sessionId}/screens/$fileName"
            "Screenshot saved successfully at $relPath (Absolute: $savedPath)"
        } catch (t: Throwable) {
            "Error taking screenshot: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

class BrowserClickTool : Tool {
    override val name: String = "browser_click"
    override val description: String = "Click on an element in the browser page matching a CSS selector."
    override val isReadOnly: Boolean = false
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "selector": { "type": "string", "description": "CSS selector of the element to click (e.g. 'button#submit', '.nav-item')" }
          },
          "required": ["selector"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val selector = root["selector"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'selector'")

        val controller = context.browserController
            ?: return "Error: Browser/Preview controller is not attached or active."

        val success = controller.click(selector)
        return if (success) {
            "Successfully clicked element matching '$selector'"
        } else {
            "Element matching '$selector' was not found or could not be clicked"
        }
    }
}

class BrowserTypeTool : Tool {
    override val name: String = "browser_type"
    override val description: String = "Type text into an input or textarea element matching a CSS selector."
    override val isReadOnly: Boolean = false
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "selector": { "type": "string", "description": "CSS selector of the input element" },
            "text": { "type": "string", "description": "Text to type into the input" },
            "clear": { "type": "boolean", "description": "Whether to clear existing text before typing (default false)" }
          },
          "required": ["selector", "text"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val selector = root["selector"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'selector'")
        val text = root["text"]?.jsonPrimitive?.content ?: ""
        val clear = root["clear"]?.jsonPrimitive?.booleanOrNull ?: false

        val controller = context.browserController
            ?: return "Error: Browser/Preview controller is not attached or active."

        val success = controller.typeText(selector, text, clear)
        return if (success) {
            "Successfully typed text into element matching '$selector'"
        } else {
            "Element matching '$selector' was not found or could not receive input"
        }
    }
}

class BrowserEvalTool : Tool {
    override val name: String = "browser_eval"
    override val description: String = "Evaluate a JavaScript expression in the browser page context and return the result."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "script": { "type": "string", "description": "JavaScript code to execute in the page" }
          },
          "required": ["script"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val script = root["script"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'script'")

        val controller = context.browserController
            ?: return "Error: Browser/Preview controller is not attached or active."

        return try {
            val result = controller.evaluateJs(script)
            "Result: $result"
        } catch (t: Throwable) {
            "JavaScript evaluation error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

class BrowserConsoleTool : Tool {
    override val name: String = "browser_console"
    override val description: String = "Retrieve recent console logs (log, warn, error) captured from the browser page."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "limit": { "type": "integer", "description": "Maximum number of logs to return (default 50)" },
            "level": { "type": "string", "description": "Optional level filter: ALL, LOG, INFO, WARN, ERROR (default ALL)" }
          }
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = try { Json.parseToJsonElement(argsJson).jsonObject } catch (_: Throwable) { null }
        val limit = root?.get("limit")?.jsonPrimitive?.intOrNull ?: 50
        val levelFilter = root?.get("level")?.jsonPrimitive?.content?.uppercase() ?: "ALL"

        val controller = context.browserController
            ?: return "Error: Browser/Preview controller is not attached or active."

        val allLogs = controller.getConsoleLogs()
        val filtered = allLogs.filter { entry ->
            if (levelFilter == "ALL") true else entry.level.name == levelFilter
        }.takeLast(limit)

        if (filtered.isEmpty()) {
            return "No console logs recorded (total buffered: ${allLogs.size})"
        }

        return buildString {
            appendLine("=== Browser Console Logs (${filtered.size} entries) ===")
            filtered.forEach { log ->
                val src = if (log.sourceId.isNotBlank()) " [${File(log.sourceId).name}:${log.lineNumber}]" else ""
                appendLine("[${log.level}]${src} ${log.message}")
            }
        }
    }
}

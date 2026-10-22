package dev.pocketagent.core.agent.browser

import dev.pocketagent.core.model.BrowserNavigationState
import dev.pocketagent.core.model.ConsoleLogEntry
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface BrowserController {
    val navigationState: StateFlow<BrowserNavigationState>
    val consoleLogs: StateFlow<List<ConsoleLogEntry>>

    suspend fun openUrl(url: String): Boolean
    suspend fun takeScreenshot(outputFile: File): String
    suspend fun click(selector: String): Boolean
    suspend fun typeText(selector: String, text: String, clearFirst: Boolean = false): Boolean
    suspend fun evaluateJs(script: String): String
    fun getConsoleLogs(): List<ConsoleLogEntry>
    fun clearConsoleLogs()
}

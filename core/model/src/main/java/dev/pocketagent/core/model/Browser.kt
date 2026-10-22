package dev.pocketagent.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConsoleLogLevel {
    LOG,
    INFO,
    WARN,
    ERROR,
    DEBUG
}

@Serializable
data class ConsoleLogEntry(
    val level: ConsoleLogLevel,
    val message: String,
    val sourceId: String = "",
    val lineNumber: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class BrowserNavigationState(
    val currentUrl: String = "http://localhost:3000",
    val title: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false
)

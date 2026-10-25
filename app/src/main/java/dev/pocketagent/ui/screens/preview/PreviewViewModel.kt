package dev.pocketagent.ui.screens.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketagent.PocketAgentApp
import dev.pocketagent.core.agent.browser.BrowserController
import dev.pocketagent.core.model.BrowserNavigationState
import dev.pocketagent.core.model.ConsoleLogEntry
import dev.pocketagent.core.model.ConsoleLogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class DeviceFrameMode {
    RESPONSIVE,
    PHONE,
    TABLET
}

data class PreviewUiState(
    val urlInput: String = "http://localhost:3000",
    val navigationState: BrowserNavigationState = BrowserNavigationState(),
    val deviceFrameMode: DeviceFrameMode = DeviceFrameMode.RESPONSIVE,
    val isConsoleOpen: Boolean = false,
    val selectedLogLevel: String = "ALL",
    val filteredLogs: List<ConsoleLogEntry> = emptyList(),
    val notificationMessage: String? = null
)

class PreviewViewModel(
    val browserController: BrowserController = PocketAgentApp.instance.browserController
) : ViewModel() {

    private val _urlInput = MutableStateFlow("http://localhost:3000")
    private val _deviceFrameMode = MutableStateFlow(DeviceFrameMode.RESPONSIVE)
    private val _isConsoleOpen = MutableStateFlow(false)
    private val _selectedLogLevel = MutableStateFlow("ALL")
    private val _notificationMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PreviewUiState> = combine(
        _urlInput,
        browserController.navigationState,
        _deviceFrameMode,
        _isConsoleOpen,
        _selectedLogLevel,
        browserController.consoleLogs,
        _notificationMessage
    ) { args: Array<Any?> ->
        val urlInput = args[0] as String
        val navState = args[1] as BrowserNavigationState
        val frameMode = args[2] as DeviceFrameMode
        val isConsoleOpen = args[3] as Boolean
        val logLevel = args[4] as String
        @Suppress("UNCHECKED_CAST")
        val safeLogs = args[5] as? List<ConsoleLogEntry> ?: emptyList()
        val notification = args[6] as? String

        val filtered = safeLogs.filter { entry ->
            when (logLevel) {
                "ALL" -> true
                "ERROR" -> entry.level == ConsoleLogLevel.ERROR
                "WARN" -> entry.level == ConsoleLogLevel.WARN
                "LOG" -> entry.level == ConsoleLogLevel.LOG
                else -> true
            }
        }
        PreviewUiState(
            urlInput = urlInput,
            navigationState = navState,
            deviceFrameMode = frameMode,
            isConsoleOpen = isConsoleOpen,
            selectedLogLevel = logLevel,
            filteredLogs = filtered,
            notificationMessage = notification
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreviewUiState()
    )

    init {
        viewModelScope.launch {
            browserController.navigationState.collect { navState ->
                if (navState.currentUrl.isNotBlank() && navState.currentUrl != _urlInput.value) {
                    _urlInput.value = navState.currentUrl
                }
            }
        }
    }

    fun onUrlChange(newUrl: String) {
        _urlInput.value = newUrl
    }

    fun navigate() {
        var target = _urlInput.value.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = "http://$target"
            _urlInput.value = target
        }
        viewModelScope.launch {
            browserController.openUrl(target)
        }
    }

    fun selectPort(port: Int) {
        val target = "http://localhost:$port"
        _urlInput.value = target
        viewModelScope.launch {
            browserController.openUrl(target)
        }
    }

    fun openWorkspaceIndex() {
        val activeProj = PocketAgentApp.instance.workspaceManager.getActiveProject()
        val projDir = File(activeProj.path)
        val indexFile = File(projDir, "index.html")
        if (indexFile.exists()) {
            val target = "file://${indexFile.absolutePath}"
            _urlInput.value = target
            viewModelScope.launch {
                browserController.openUrl(target)
            }
        } else {
            val anyHtml = projDir.listFiles { file -> file.extension.equals("html", ignoreCase = true) }?.firstOrNull()
            if (anyHtml != null) {
                val target = "file://${anyHtml.absolutePath}"
                _urlInput.value = target
                viewModelScope.launch {
                    browserController.openUrl(target)
                }
            } else {
                _notificationMessage.value = "No index.html found in workspace: ${activeProj.name}"
            }
        }
    }

    fun toggleDeviceFrame() {
        _deviceFrameMode.value = when (_deviceFrameMode.value) {
            DeviceFrameMode.RESPONSIVE -> DeviceFrameMode.PHONE
            DeviceFrameMode.PHONE -> DeviceFrameMode.TABLET
            DeviceFrameMode.TABLET -> DeviceFrameMode.RESPONSIVE
        }
    }

    fun toggleConsole() {
        _isConsoleOpen.value = !_isConsoleOpen.value
    }

    fun selectLogLevel(level: String) {
        _selectedLogLevel.value = level
    }

    fun clearConsole() {
        browserController.clearConsoleLogs()
    }

    fun captureScreenshot(workspaceRoot: File? = null, sessionId: String = "manual") {
        viewModelScope.launch {
            try {
                val screensDir = if (workspaceRoot != null) {
                    File(workspaceRoot, ".agent/artifacts/$sessionId/screens")
                } else {
                    File(PocketAgentApp.instance.filesDir, "screenshots")
                }
                screensDir.mkdirs()
                val target = File(screensDir, "preview_${System.currentTimeMillis()}.png")
                val path = browserController.takeScreenshot(target)
                _notificationMessage.value = "Screenshot saved: ${target.name}"
            } catch (t: Throwable) {
                _notificationMessage.value = "Screenshot failed: ${t.message}"
            }
        }
    }

    fun clearNotification() {
        _notificationMessage.value = null
    }
}

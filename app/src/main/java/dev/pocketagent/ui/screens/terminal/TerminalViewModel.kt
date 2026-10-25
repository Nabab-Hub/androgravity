package dev.pocketagent.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketagent.PocketAgentApp
import dev.pocketagent.core.sandbox.BootstrapStatus
import dev.pocketagent.core.sandbox.LinuxEnvironmentManager
import dev.pocketagent.core.sandbox.PtySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class TerminalTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val output: String = "",
    val isRunning: Boolean = true
)

class TerminalViewModel(
    private val app: PocketAgentApp = PocketAgentApp.instance
) : ViewModel() {

    private val linuxManager: LinuxEnvironmentManager = app.linuxManager

    val bootstrapStatus: StateFlow<BootstrapStatus> = linuxManager.status

    private val _tabs = MutableStateFlow<List<TerminalTab>>(emptyList())
    val tabs: StateFlow<List<TerminalTab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val sessions = mutableMapOf<String, PtySession>()
    private val listenerJobs = mutableMapOf<String, Job>()

    init {
        openNewTab()

        // Sync terminal working directory whenever active project changes
        viewModelScope.launch {
            app.workspaceManager.activeProject.collect { proj ->
                if (proj != null) {
                    val tabs = _tabs.value
                    val index = _activeTabIndex.value
                    if (index in tabs.indices) {
                        val tab = tabs[index]
                        appendOutput(tab.id, "\r\n\u001b[1;36m[Workspace switched to: ${proj.name}]\u001b[0m\r\n")
                        sessions[tab.id]?.let { s ->
                            try { s.write("cd \"${proj.path}\"\n") } catch (_: Throwable) {}
                        }
                    }
                }
            }
        }
    }

    fun startBootstrap() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = linuxManager.bootstrap()
            if (ok && _tabs.value.isEmpty()) {
                openNewTab()
            }
        }
    }

    fun openNewTab(title: String = "sh") {
        viewModelScope.launch(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val tabNumber = _tabs.value.size + 1
            val tabTitle = "term-$tabNumber"

            val activeProject = app.workspaceManager.getActiveProject()
            val defaultProjectDir = File(activeProject.path)
            val activeBackend = app.getActiveExecBackend()

            val isRemote = activeBackend is dev.pocketagent.core.sandbox.RemoteExecBackend
            val backendName = when {
                isRemote -> "Termux Bridge / Remote Daemon"
                app.prootBackend.isProotAvailable() -> "Alpine Linux (proot)"
                else -> "Android Direct Shell"
            }

            if (isRemote) {
                val newTab = TerminalTab(
                    id = id,
                    title = tabTitle,
                    output = "\u001b[1;32m=== Androgravity Terminal ($tabTitle) ===\u001b[0m\r\n" +
                            "Workspace: \u001b[1;34m${activeProject.name}\u001b[0m (${defaultProjectDir.absolutePath})\r\n" +
                            "Environment: \u001b[1;33m$backendName\u001b[0m\r\n" +
                            "Packages: \u001b[32mFull Termux Environment (python3, node, git, npm, pkg)\u001b[0m\r\n\r\n" +
                            "\u001b[1;34m${activeProject.name}\u001b[0m $ ",
                    isRunning = true
                )
                _tabs.value = _tabs.value + newTab
                _activeTabIndex.value = _tabs.value.lastIndex
                return@launch
            }

            val session = try {
                if (app.prootBackend.isProotAvailable()) {
                    app.prootBackend.start(
                        cmd = listOf("/bin/sh", "-l"),
                        env = emptyMap(),
                        cwd = defaultProjectDir.absolutePath
                    )
                } else {
                    // Fallback to local process (direct shell)
                    val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
                    val shellCmd = if (isWindows) listOf("cmd.exe") else listOf("/bin/sh")
                    app.directBackend.start(
                        cmd = shellCmd,
                        env = emptyMap(),
                        cwd = defaultProjectDir.absolutePath
                    )
                }
            } catch (t: Throwable) {
                // If shell spawning fails, show error in tab
                val newTab = TerminalTab(
                    id = id,
                    title = tabTitle,
                    output = "Failed to launch shell: ${t.message}\n",
                    isRunning = false
                )
                _tabs.value = _tabs.value + newTab
                _activeTabIndex.value = _tabs.value.lastIndex
                return@launch
            }

            sessions[id] = session

            val newTab = TerminalTab(
                id = id,
                title = tabTitle,
                output = "Androgravity Terminal ($tabTitle)\nWorkspace: ${activeProject.name} (${defaultProjectDir.absolutePath})\nShell: $backendName\n\n",
                isRunning = true
            )
            _tabs.value = _tabs.value + newTab
            _activeTabIndex.value = _tabs.value.lastIndex

            // Collect output
            val job = launch {
                session.output.collect { bytes ->
                    val text = String(bytes, Charsets.UTF_8)
                    appendOutput(id, text)
                }
                // Process terminated
                val exitCode = session.exitCode.await()
                appendOutput(id, "\n[Process completed with exit code $exitCode]\n")
                setRunning(id, false)
            }
            listenerJobs[id] = job
        }
    }

    private fun appendOutput(tabId: String, text: String) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                // Keep last 50,000 chars to avoid memory growth in long sessions
                val combined = (tab.output + text).takeLast(50000)
                tab.copy(output = combined)
            } else {
                tab
            }
        }
    }

    private fun setRunning(tabId: String, isRunning: Boolean) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) tab.copy(isRunning = isRunning) else tab
        }
    }

    fun selectTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
        }
    }

    fun closeTab(tabId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            listenerJobs[tabId]?.cancel()
            listenerJobs.remove(tabId)

            sessions[tabId]?.kill()
            sessions.remove(tabId)

            val current = _tabs.value
            val idx = current.indexOfFirst { it.id == tabId }
            val updated = current.filter { it.id != tabId }
            _tabs.value = updated

            if (updated.isEmpty()) {
                _activeTabIndex.value = 0
            } else if (_activeTabIndex.value >= updated.size) {
                _activeTabIndex.value = updated.size - 1
            }
        }
    }

    fun sendInput(text: String, echo: Boolean = false) {
        val currentTabs = _tabs.value
        val index = _activeTabIndex.value
        if (index !in currentTabs.indices) return
        val currentTab = currentTabs[index]
        val session = sessions[currentTab.id]

        val activeBackend = app.getActiveExecBackend()
        val isRemote = activeBackend is dev.pocketagent.core.sandbox.RemoteExecBackend

        if (echo) {
            val trimmed = text.trimEnd('\r', '\n')
            appendOutput(currentTab.id, "\u001b[1;32m$ $trimmed\u001b[0m\r\n")

            if ((trimmed.startsWith("apk") || trimmed.startsWith("pkg") || trimmed.startsWith("apt")) && !app.prootBackend.isProotAvailable() && !isRemote) {
                appendOutput(
                    currentTab.id,
                    "\u001b[1;33m[Termux / Package Manager Notice]\u001b[0m\r\n" +
                    "Android security sandbox prevents direct access to Termux private files (/data/data/com.termux/).\r\n" +
                    "• To connect Androgravity with your Termux packages (git, node, python, pkg):\r\n" +
                    "  1) In Termux, run:\r\n" +
                    "     python <(curl -sL https://raw.githubusercontent.com/Nabab-Hub/androgravity/main/tools/pocket-api/pocket-api.py) --port 8787\r\n" +
                    "  2) In Androgravity > Settings > Sandbox, select 'Termux Bridge / Remote PC' -> Host: 127.0.0.1, Port: 8787.\r\n" +
                    "  All terminal and agent operations will then execute directly inside your Termux environment.\r\n"
                )
            }
        }

        if (isRemote) {
            val command = text.trim()
            if (command.isNotBlank()) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val activeProject = app.workspaceManager.getActiveProject()
                        val result = activeBackend.exec(
                            cmd = listOf("sh", "-c", command),
                            cwd = activeProject.path
                        )
                        if (result.stdout.isNotBlank()) {
                            appendOutput(currentTab.id, "${result.stdout}\r\n")
                        }
                        appendOutput(currentTab.id, "\u001b[1;34m${activeProject.name}\u001b[0m $ ")
                    } catch (t: Throwable) {
                        appendOutput(currentTab.id, "\u001b[1;31m[Termux Bridge Error: ${t.message}]\u001b[0m\r\n")
                    }
                }
            }
            return
        }

        if (session != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    session.write(text)
                } catch (t: Throwable) {
                    appendOutput(currentTab.id, "\n[Error: ${t.message}]\n")
                }
            }
        } else {
            appendOutput(currentTab.id, "\n[No active session. Tap + above to start a shell.]\n")
        }
    }

    fun sendSpecialKey(code: String) {
        sendInput(code)
    }

    fun clearActiveTab() {
        val currentTabs = _tabs.value
        val index = _activeTabIndex.value
        if (index in currentTabs.indices) {
            val target = currentTabs[index]
            _tabs.value = currentTabs.map {
                if (it.id == target.id) it.copy(output = "") else it
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessions.values.forEach { session ->
            try {
                kotlinx.coroutines.runBlocking { session.kill() }
            } catch (_: Throwable) {
            }
        }
        sessions.clear()
        listenerJobs.values.forEach { it.cancel() }
        listenerJobs.clear()
    }
}

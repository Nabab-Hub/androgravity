package dev.pocketagent.ui.screens.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketagent.PocketAgentApp
import dev.pocketagent.core.agent.mcp.McpManager
import dev.pocketagent.core.model.McpPolicy
import dev.pocketagent.core.model.McpServerConfig
import dev.pocketagent.core.model.McpTransportType
import dev.pocketagent.core.storage.McpServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class McpViewModel(
    private val repository: McpServerRepository = PocketAgentApp.instance.mcpServerRepository,
    private val mcpManager: McpManager = PocketAgentApp.instance.mcpManager
) : ViewModel() {

    val servers: StateFlow<List<McpServerConfig>> = repository.servers

    private val _connectingMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val connectingMap: StateFlow<Map<String, Boolean>> = _connectingMap.asStateFlow()

    private val _statusMessageMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val statusMessageMap: StateFlow<Map<String, String>> = _statusMessageMap.asStateFlow()

    fun addServer(
        name: String,
        transportType: McpTransportType,
        url: String,
        commandStr: String,
        policy: McpPolicy
    ) {
        val command = if (commandStr.isNotBlank()) {
            commandStr.split(" ").filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        val server = repository.addServer(
            name = name.trim(),
            transportType = transportType,
            url = url.trim(),
            command = command,
            policy = policy
        )
        // Automatically attempt handshake and tool discovery
        testConnection(server.id)
    }

    fun removeServer(id: String) {
        repository.removeServer(id)
    }

    fun toggleServer(id: String, enabled: Boolean) {
        repository.toggleServer(id, enabled)
    }

    fun setPolicy(id: String, policy: McpPolicy) {
        repository.setPolicy(id, policy)
    }

    fun testConnection(id: String) {
        viewModelScope.launch {
            _connectingMap.value = _connectingMap.value + (id to true)
            _statusMessageMap.value = _statusMessageMap.value - id

            val res = mcpManager.connectAndDiscoverTools(id)
            _connectingMap.value = _connectingMap.value - id

            res.fold(
                onSuccess = { tools ->
                    _statusMessageMap.value = _statusMessageMap.value + (id to "Connected! Discovered ${tools.size} tools.")
                },
                onFailure = { err ->
                    _statusMessageMap.value = _statusMessageMap.value + (id to "Error: ${err.message ?: err.javaClass.simpleName}")
                }
            )
        }
    }
}

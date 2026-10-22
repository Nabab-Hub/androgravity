package dev.pocketagent.core.storage

import dev.pocketagent.core.model.McpPolicy
import dev.pocketagent.core.model.McpServerConfig
import dev.pocketagent.core.model.McpToolDefinition
import dev.pocketagent.core.model.McpTransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class McpServerRepository(
    private val storageDir: File
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val configFile = File(storageDir, "mcp_servers.json")
    private val _servers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    init {
        loadServers()
    }

    @Synchronized
    private fun loadServers() {
        if (!configFile.exists()) {
            _servers.value = emptyList()
            return
        }
        try {
            val content = configFile.readText()
            val list = json.decodeFromString<List<McpServerConfig>>(content)
            _servers.value = list
        } catch (_: Throwable) {
            _servers.value = emptyList()
        }
    }

    @Synchronized
    private fun persist() {
        try {
            configFile.parentFile?.mkdirs()
            val content = json.encodeToString(_servers.value)
            configFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun addServer(
        name: String,
        transportType: McpTransportType,
        url: String = "",
        command: List<String> = emptyList(),
        policy: McpPolicy = McpPolicy.ASK
    ): McpServerConfig {
        val server = McpServerConfig(
            id = "mcp_${System.currentTimeMillis()}_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}",
            name = name,
            transportType = transportType,
            url = url,
            command = command,
            isEnabled = true,
            policy = policy,
            tools = emptyList()
        )
        _servers.value = _servers.value + server
        persist()
        return server
    }

    @Synchronized
    fun updateServer(config: McpServerConfig) {
        _servers.value = _servers.value.map {
            if (it.id == config.id) config else it
        }
        persist()
    }

    @Synchronized
    fun removeServer(id: String) {
        _servers.value = _servers.value.filter { it.id != id }
        persist()
    }

    @Synchronized
    fun setPolicy(id: String, policy: McpPolicy) {
        _servers.value = _servers.value.map {
            if (it.id == id) it.copy(policy = policy) else it
        }
        persist()
    }

    @Synchronized
    fun toggleServer(id: String, enabled: Boolean) {
        _servers.value = _servers.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        persist()
    }

    @Synchronized
    fun updateServerTools(id: String, tools: List<McpToolDefinition>, connectedTimeMs: Long = System.currentTimeMillis()) {
        _servers.value = _servers.value.map {
            if (it.id == id) it.copy(tools = tools, lastConnectedTimeMs = connectedTimeMs) else it
        }
        persist()
    }

    fun getServer(id: String): McpServerConfig? = _servers.value.find { it.id == id }
}

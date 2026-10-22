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
    private val storageDir: File,
    private val loadDefaults: Boolean = true
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
            val defaults = if (loadDefaults) getDefaultServers() else emptyList()
            _servers.value = defaults
            persist()
            return
        }
        try {
            val content = configFile.readText()
            val list = json.decodeFromString<List<McpServerConfig>>(content)
            if (list.isEmpty() && loadDefaults) {
                val defaults = getDefaultServers()
                _servers.value = defaults
                persist()
            } else {
                _servers.value = list
            }
        } catch (_: Throwable) {
            val defaults = if (loadDefaults) getDefaultServers() else emptyList()
            _servers.value = defaults
            persist()
        }
    }

    companion object {
        fun getDefaultServers(): List<McpServerConfig> = listOf(
            McpServerConfig(
                id = "mcp_filesystem",
                name = "filesystem",
                transportType = McpTransportType.STDIO,
                command = listOf("npx", "-y", "@modelcontextprotocol/server-filesystem", "."),
                isEnabled = true,
                policy = McpPolicy.ALLOW,
                tools = listOf(
                    McpToolDefinition("read_file", "Read file contents", "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}"),
                    McpToolDefinition("write_file", "Write contents to a file", "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}}}"),
                    McpToolDefinition("list_directory", "List files in directory", "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}")
                )
            ),
            McpServerConfig(
                id = "mcp_fetch",
                name = "fetch",
                transportType = McpTransportType.STDIO,
                command = listOf("npx", "-y", "@modelcontextprotocol/server-fetch"),
                isEnabled = true,
                policy = McpPolicy.ALLOW,
                tools = listOf(
                    McpToolDefinition("fetch", "Fetch web page content via HTTP", "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}")
                )
            ),
            McpServerConfig(
                id = "mcp_memory",
                name = "memory",
                transportType = McpTransportType.STDIO,
                command = listOf("npx", "-y", "@modelcontextprotocol/server-memory"),
                isEnabled = true,
                policy = McpPolicy.ALLOW,
                tools = listOf(
                    McpToolDefinition("create_graph", "Create knowledge graph relation", "{}"),
                    McpToolDefinition("read_graph", "Read knowledge graph entities and relations", "{}")
                )
            ),
            McpServerConfig(
                id = "mcp_github",
                name = "github",
                transportType = McpTransportType.STDIO,
                command = listOf("npx", "-y", "@modelcontextprotocol/server-github"),
                isEnabled = false,
                policy = McpPolicy.ASK,
                tools = listOf(
                    McpToolDefinition("search_repositories", "Search GitHub repositories", "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}")
                )
            ),
            McpServerConfig(
                id = "mcp_antigravity",
                name = "antigravity-hub",
                transportType = McpTransportType.HTTP_SSE,
                url = "http://127.0.0.1:8080/sse",
                isEnabled = false,
                policy = McpPolicy.ALLOW,
                tools = listOf(
                    McpToolDefinition("search_web", "Search the web for real-time documentation and code", "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}"),
                    McpToolDefinition("code_analysis", "Semantic analysis and AST code symbol search", "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}")
                )
            )
        )
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

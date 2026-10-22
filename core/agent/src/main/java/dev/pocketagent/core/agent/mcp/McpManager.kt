package dev.pocketagent.core.agent.mcp

import dev.pocketagent.core.agent.policy.PolicyEngine
import dev.pocketagent.core.agent.tools.ToolRouter
import dev.pocketagent.core.model.McpPolicy
import dev.pocketagent.core.model.McpServerConfig
import dev.pocketagent.core.model.McpToolDefinition
import dev.pocketagent.core.sandbox.ExecBackend
import dev.pocketagent.core.storage.McpServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class McpManager(
    private val repository: McpServerRepository,
    private val toolRouter: ToolRouter,
    private val policyEngine: PolicyEngine,
    private val execBackend: ExecBackend? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val clients = ConcurrentHashMap<String, McpClient>()
    private val activeRegisteredToolNames = ConcurrentHashMap<String, MutableSet<String>>()
    private val mutex = Mutex()

    init {
        // Wire policy engine to consult MCP server policies
        policyEngine.mcpPolicyProvider = { serverName ->
            getServerPolicy(serverName)
        }

        // Listen for repository changes to auto-sync registered tools
        repository.servers.onEach { serverConfigs ->
            syncServers(serverConfigs)
        }.launchIn(scope)
    }

    private fun getServerPolicy(serverName: String): McpPolicy {
        val server = repository.servers.value.find {
            it.name.equals(serverName, ignoreCase = true) || it.id.equals(serverName, ignoreCase = true)
        } ?: return McpPolicy.ASK

        if (!server.isEnabled) return McpPolicy.DENY
        return server.policy
    }

    suspend fun syncServers(serverConfigs: List<McpServerConfig>) = mutex.withLock {
        val currentIds = serverConfigs.map { it.id }.toSet()

        // 1. Clean up removed or disabled servers
        val removedIds = activeRegisteredToolNames.keys.filter { it !in currentIds || serverConfigs.find { s -> s.id == it }?.isEnabled == false }
        for (id in removedIds) {
            unregisterServerTools(id)
            clients.remove(id)?.close()
        }

        // 2. Register tools for enabled servers
        for (config in serverConfigs) {
            if (config.isEnabled) {
                val client = clients.getOrPut(config.id) {
                    McpClient(config, execBackend)
                }
                registerServerTools(config, client)
            }
        }
    }

    private fun registerServerTools(config: McpServerConfig, client: McpClient) {
        val registeredSet = activeRegisteredToolNames.getOrPut(config.id) { ConcurrentHashMap.newKeySet() }

        // Unregister previously registered tools for this server
        for (toolName in registeredSet) {
            toolRouter.unregisterTool(toolName)
        }
        registeredSet.clear()

        // Register each tool
        for (toolDef in config.tools) {
            val mcpTool = McpTool(config.name, toolDef) { client }
            toolRouter.registerTool(mcpTool)
            registeredSet.add(mcpTool.name)
        }
    }

    private fun unregisterServerTools(serverId: String) {
        val tools = activeRegisteredToolNames.remove(serverId) ?: return
        for (name in tools) {
            toolRouter.unregisterTool(name)
        }
    }

    /**
     * Tests connection to an MCP server, performs handshake, queries tools, and updates the repository.
     */
    suspend fun connectAndDiscoverTools(serverId: String): Result<List<McpToolDefinition>> {
        val server = repository.getServer(serverId)
            ?: return Result.failure(IllegalArgumentException("Server '$serverId' not found."))

        return try {
            val client = McpClient(server, execBackend)
            val discovered = client.initialize()
            clients[serverId]?.close()
            clients[serverId] = client

            repository.updateServerTools(serverId, discovered)
            Result.success(discovered)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun close() {
        clients.values.forEach { it.close() }
        clients.clear()
    }
}

package dev.pocketagent.core.agent

import com.google.common.truth.Truth.assertThat
import dev.pocketagent.core.agent.mcp.McpClient
import dev.pocketagent.core.agent.mcp.McpManager
import dev.pocketagent.core.agent.mcp.McpTool
import dev.pocketagent.core.agent.policy.PolicyDecision
import dev.pocketagent.core.agent.policy.PolicyEngine
import dev.pocketagent.core.agent.tools.ToolContext
import dev.pocketagent.core.agent.tools.ToolRouter
import dev.pocketagent.core.agent.policy.AutonomyMode
import dev.pocketagent.core.model.McpPolicy
import dev.pocketagent.core.model.McpServerConfig
import dev.pocketagent.core.model.McpToolDefinition
import dev.pocketagent.core.model.McpTransportType
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.core.sandbox.DirectBackend
import dev.pocketagent.core.storage.McpServerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class McpIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var workspace: File
    private lateinit var storageDir: File
    private lateinit var repo: McpServerRepository
    private lateinit var toolRouter: ToolRouter
    private lateinit var policyEngine: PolicyEngine

    @Before
    fun setup() {
        workspace = tempFolder.newFolder("ws_mcp")
        storageDir = tempFolder.newFolder("storage_mcp")
        repo = McpServerRepository(storageDir)
        toolRouter = ToolRouter()
        policyEngine = PolicyEngine(AutonomyMode.SEMI_AUTONOMY)
    }

    @Test
    fun testMcpServerRepositoryCrud() {
        val server = repo.addServer(
            name = "weather_service",
            transportType = McpTransportType.HTTP_SSE,
            url = "http://localhost:8000/sse",
            policy = McpPolicy.ASK
        )
        assertThat(repo.servers.value).hasSize(1)
        assertThat(repo.servers.value[0].name).isEqualTo("weather_service")

        // Update policy
        repo.setPolicy(server.id, McpPolicy.ALLOW)
        assertThat(repo.servers.value[0].policy).isEqualTo(McpPolicy.ALLOW)

        // Update tools
        val tools = listOf(
            McpToolDefinition("get_forecast", "Fetches 5-day weather forecast", "{}")
        )
        repo.updateServerTools(server.id, tools)
        assertThat(repo.servers.value[0].tools).hasSize(1)

        // Re-read from disk to test persistence
        val newRepo = McpServerRepository(storageDir)
        assertThat(newRepo.servers.value).hasSize(1)
        assertThat(newRepo.servers.value[0].tools[0].name).isEqualTo("get_forecast")

        // Delete
        repo.removeServer(server.id)
        assertThat(repo.servers.value).isEmpty()
    }

    @Test
    fun testMcpToolFramesOutputAsUntrusted() = runBlocking {
        val toolDef = McpToolDefinition(
            name = "calculate",
            description = "Calculates expressions",
            inputSchemaJson = "{}"
        )

        val fakeClient = object : McpClient(
            McpServerConfig("id", "math", McpTransportType.HTTP_SSE, "http://localhost")
        ) {
            override suspend fun callTool(toolName: String, argumentsJson: String): String {
                return "42"
            }
        }

        val tool = McpTool("math", toolDef) { fakeClient }

        val context = ToolContext(
            projectId = "p1",
            workspaceRoot = workspace,
            execBackend = DirectBackend()
        )

        val output = tool.execute("{}", context)
        assertThat(output).contains("Untrusted external content")
        assertThat(output).contains("math/calculate")
        assertThat(output).contains("42")
    }

    @Test
    fun testMcpPoliciesInPolicyEngine() {
        // Setup MCP policy provider
        val serverPolicies = mutableMapOf(
            "allowed_server" to McpPolicy.ALLOW,
            "ask_server" to McpPolicy.ASK,
            "denied_server" to McpPolicy.DENY
        )
        policyEngine.mcpPolicyProvider = { serverName ->
            serverPolicies[serverName] ?: McpPolicy.ASK
        }

        // 1. ALLOW Policy -> PolicyDecision.Allow in SEMI_AUTONOMY
        val callAllowed = ToolCall("1", "mcp.allowed_server.fetch_data", "{}")
        assertThat(policyEngine.evaluate(callAllowed, workspace)).isInstanceOf(PolicyDecision.Allow::class.java)

        // 2. ASK Policy -> RequireApproval
        val callAsk = ToolCall("2", "mcp.ask_server.run_action", "{}")
        val askDecision = policyEngine.evaluate(callAsk, workspace)
        assertThat(askDecision).isInstanceOf(PolicyDecision.RequireApproval::class.java)
        assertThat((askDecision as PolicyDecision.RequireApproval).reason).contains("requires user approval")

        // 3. DENY Policy -> PolicyDecision.Deny
        val callDeny = ToolCall("3", "mcp.denied_server.destructive_op", "{}")
        val denyDecision = policyEngine.evaluate(callDeny, workspace)
        assertThat(denyDecision).isInstanceOf(PolicyDecision.Deny::class.java)
        assertThat((denyDecision as PolicyDecision.Deny).reason).contains("disabled by policy")
    }

    @Test
    fun testMcpManagerSyncsToolsWithRouter() = runBlocking {
        val manager = McpManager(repo, toolRouter, policyEngine, DirectBackend())

        // Add server with pre-configured tools
        val tools = listOf(
            McpToolDefinition("lookup_user", "Lookup user by id", "{}"),
            McpToolDefinition("send_alert", "Send alert", "{}")
        )
        val server = repo.addServer("directory", McpTransportType.HTTP_SSE, "http://10.0.2.2:3000/sse", policy = McpPolicy.ALLOW)
        repo.updateServerTools(server.id, tools)

        // Trigger sync
        manager.syncServers(repo.servers.value)

        // Verify tools registered in ToolRouter
        assertThat(toolRouter.getTool("mcp.directory.lookup_user")).isNotNull()
        assertThat(toolRouter.getTool("mcp.directory.send_alert")).isNotNull()

        // Disable server -> tools unregistered
        repo.toggleServer(server.id, false)
        manager.syncServers(repo.servers.value)
        assertThat(toolRouter.getTool("mcp.directory.lookup_user")).isNull()
        assertThat(toolRouter.getTool("mcp.directory.send_alert")).isNull()
    }
}

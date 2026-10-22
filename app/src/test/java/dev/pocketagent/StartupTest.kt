package dev.pocketagent

import org.junit.Test

class StartupTest {
    @Test
    fun testDependencyInstantiation() {
        println("Testing GeminiProvider...")
        val provider = dev.pocketagent.core.providers.gemini.GeminiProvider(apiKeyProvider = { "" })
        println("GeminiProvider OK: ${provider.name}")

        println("Testing PolicyEngine...")
        val policyEngine = dev.pocketagent.core.agent.policy.PolicyEngine()
        println("PolicyEngine OK")

        println("Testing ToolRouter...")
        val toolRouter = dev.pocketagent.core.agent.tools.ToolRouter()
        println("ToolRouter OK: ${toolRouter.getAllTools().size} tools")

        println("Testing RemoteExecBackend...")
        val remoteExecBackend = dev.pocketagent.core.sandbox.RemoteExecBackend(
            configProvider = { dev.pocketagent.core.model.RemoteConfig() }
        )
        println("RemoteExecBackend OK")

        println("Testing DirectBackend...")
        val directBackend = dev.pocketagent.core.sandbox.DirectBackend()
        println("DirectBackend OK")

        println("Testing McpServerRepository...")
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "test_mcp_" + System.currentTimeMillis())
        tempDir.mkdirs()
        val mcpRepo = dev.pocketagent.core.storage.McpServerRepository(tempDir)
        println("McpServerRepository OK: ${mcpRepo.servers.value.size} servers")

        println("Testing McpManager...")
        val mcpManager = dev.pocketagent.core.agent.mcp.McpManager(
            repository = mcpRepo,
            toolRouter = toolRouter,
            policyEngine = policyEngine,
            execBackend = directBackend
        )
        println("McpManager OK")

        println("Testing AgentLoop...")
        val agentLoop = dev.pocketagent.core.agent.loop.AgentLoop(
            modelProvider = provider,
            toolRouter = toolRouter,
            policyEngine = policyEngine
        )
        println("AgentLoop OK")
    }
}

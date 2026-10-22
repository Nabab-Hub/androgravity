package dev.pocketagent.core.agent

import com.google.common.truth.Truth.assertThat
import dev.pocketagent.core.agent.loop.AgentEvent
import dev.pocketagent.core.agent.loop.AgentLoop
import dev.pocketagent.core.agent.policy.AutonomyMode
import dev.pocketagent.core.agent.policy.PolicyEngine
import dev.pocketagent.core.agent.tools.ToolRouter
import dev.pocketagent.core.model.ChatEvent
import dev.pocketagent.core.model.ChatRequest
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.core.providers.ModelProvider
import dev.pocketagent.core.sandbox.DirectBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AgentLoopTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testAgentBuildsHelloWorldAppEndToEnd() = runBlocking {
        val workspace = tempFolder.newFolder("workspace")

        // Mock model provider that emulates multi-turn tool calling
        val mockProvider = object : ModelProvider {
            override val id: String = "mock"
            override val name: String = "Mock Provider"
            var turn = 0

            override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
                turn++
                if (turn == 1) {
                    // Turn 1: Propose creating index.html
                    emit(ChatEvent.TextDelta("I will create the hello world web application."))
                    emit(
                        ChatEvent.ToolCallChunk(
                            ToolCall(
                                id = "call-1",
                                name = "fs_write_file",
                                argsJson = """{"path": "index.html", "content": "<!DOCTYPE html><html><body><h1>Hello PocketAgent!</h1></body></html>"}"""
                            )
                        )
                    )
                    emit(ChatEvent.Done())
                } else {
                    // Turn 2: Inspects result and produces completion
                    emit(ChatEvent.TextDelta(" Successfully created the hello-world application in index.html!"))
                    emit(ChatEvent.Done())
                }
            }
        }

        val policy = PolicyEngine(AutonomyMode.FULL_AUTONOMY)
        val router = ToolRouter()
        val loop = AgentLoop(
            modelProvider = mockProvider,
            toolRouter = router,
            policyEngine = policy,
            maxSteps = 5
        )

        val events = mutableListOf<AgentEvent>()
        loop.run(
            userPrompt = "Build a hello-world web app",
            workspaceRoot = workspace,
            projectId = "hw-project",
            execBackend = DirectBackend()
        ).collect { event ->
            events.add(event)
        }

        // Verify index.html was created
        val indexHtml = File(workspace, "index.html")
        assertThat(indexHtml.exists()).isTrue()
        assertThat(indexHtml.readText()).contains("<h1>Hello PocketAgent!</h1>")

        // Verify event stream
        val hasStepStarted = events.any { it is AgentEvent.StepStarted }
        val hasToolCall = events.any { it is AgentEvent.ToolCallProposed }
        val hasToolExecuted = events.any { it is AgentEvent.ToolCallExecuted }
        val hasDone = events.any { it is AgentEvent.Done }

        assertThat(hasStepStarted).isTrue()
        assertThat(hasToolCall).isTrue()
        assertThat(hasToolExecuted).isTrue()
        assertThat(hasDone).isTrue()
    }

    @Test
    fun testPolicyDenialInAgentLoop() = runBlocking {
        val workspace = tempFolder.newFolder("workspace")

        val mockProvider = object : ModelProvider {
            override val id: String = "mock"
            override val name: String = "Mock Provider"
            var turn = 0

            override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
                turn++
                if (turn == 1) {
                    emit(
                        ChatEvent.ToolCallChunk(
                            ToolCall(
                                id = "call-deny",
                                name = "fs_read_file",
                                argsJson = """{"path": "../../escape.txt"}"""
                            )
                        )
                    )
                    emit(ChatEvent.Done())
                } else {
                    // Model sees denial message and explains to user
                    emit(ChatEvent.TextDelta("Understood, I cannot access paths outside the workspace."))
                    emit(ChatEvent.Done())
                }
            }
        }

        val policy = PolicyEngine(AutonomyMode.FULL_AUTONOMY)
        val loop = AgentLoop(
            modelProvider = mockProvider,
            toolRouter = ToolRouter(),
            policyEngine = policy,
            maxSteps = 3
        )

        val executedEvents = mutableListOf<AgentEvent.ToolCallExecuted>()
        loop.run(
            userPrompt = "Read outside file",
            workspaceRoot = workspace,
            projectId = "deny-project",
            execBackend = DirectBackend()
        ).collect { event ->
            if (event is AgentEvent.ToolCallExecuted) {
                executedEvents.add(event)
            }
        }

        assertThat(executedEvents).isNotEmpty()
        val deniedResult = executedEvents.first().result
        assertThat(deniedResult.ok).isFalse()
        assertThat(deniedResult.output).contains("escapes active workspace boundary")
    }
}

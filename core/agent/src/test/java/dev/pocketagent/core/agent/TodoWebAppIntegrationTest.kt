package dev.pocketagent.core.agent

import com.google.common.truth.Truth.assertThat
import dev.pocketagent.core.agent.loop.AgentEvent
import dev.pocketagent.core.agent.loop.AgentLoop
import dev.pocketagent.core.agent.policy.AutonomyMode
import dev.pocketagent.core.agent.policy.PolicyEngine
import dev.pocketagent.core.agent.tools.ToolRouter
import dev.pocketagent.core.model.ChatEvent
import dev.pocketagent.core.model.ChatRequest
import dev.pocketagent.core.providers.ModelProvider
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.core.sandbox.DirectBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TodoWebAppIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testBuildTodoWebAppFullLifecycle() = runBlocking {
        val workspace = tempFolder.newFolder("todo_workspace")
        val sessionId = "todo_session_42"
        val fakeBrowser = FakeBrowserController()

        // Scripted Model Provider that simulates an autonomous agent building a full Todo App
        val scriptedProvider = object : ModelProvider {
            override val id: String = "scripted_model"
            override val name: String = "Scripted Autonomous Agent"
            var turn = 0

            override fun stream(request: ChatRequest): Flow<ChatEvent> = flow {
                turn++
                when (turn) {
                    1 -> {
                        // Turn 1: Write Implementation Plan
                        emit(ChatEvent.TextDelta("Step 1: Outlining the implementation plan for the Todo Web App."))
                        emit(
                            ChatEvent.ToolCallChunk(
                                ToolCall(
                                    id = "call-plan",
                                    name = "artifact_write",
                                    argsJson = """{"name": "plan.md", "content": "# Todo App Plan\n- [ ] Create HTML structure\n- [ ] Create styling\n- [ ] Visual verification"}"""
                                )
                            )
                        )
                        emit(ChatEvent.Done())
                    }
                    2 -> {
                        // Turn 2: Create index.html
                        emit(ChatEvent.TextDelta("Step 2: Building index.html with interactive todo items."))
                        emit(
                            ChatEvent.ToolCallChunk(
                                ToolCall(
                                    id = "call-html",
                                    name = "fs_write_file",
                                    argsJson = """{"path": "index.html", "content": "<!DOCTYPE html><html><head><link rel='stylesheet' href='style.css'></head><body><h1>Todo List</h1><ul id='todos'><li>First Item</li></ul><script src='app.js'></script></body></html>"}"""
                                )
                            )
                        )
                        emit(ChatEvent.Done())
                    }
                    3 -> {
                        // Turn 3: Create style.css
                        emit(ChatEvent.TextDelta("Step 3: Creating style.css for responsive design."))
                        emit(
                            ChatEvent.ToolCallChunk(
                                ToolCall(
                                    id = "call-css",
                                    name = "fs_write_file",
                                    argsJson = """{"path": "style.css", "content": "body { font-family: sans-serif; background: #121212; color: white; padding: 20px; }"}"""
                                )
                            )
                        )
                        emit(ChatEvent.Done())
                    }
                    4 -> {
                        // Turn 4: Open in preview browser
                        emit(ChatEvent.TextDelta("Step 4: Launching preview browser to test application."))
                        emit(
                            ChatEvent.ToolCallChunk(
                                ToolCall(
                                    id = "call-browser",
                                    name = "browser_open",
                                    argsJson = """{"url": "http://localhost:3000"}"""
                                )
                            )
                        )
                        emit(ChatEvent.Done())
                    }
                    5 -> {
                        // Turn 5: Take verification screenshot
                        emit(ChatEvent.TextDelta("Step 5: Capturing screenshot artifact for visual inspection."))
                        emit(
                            ChatEvent.ToolCallChunk(
                                ToolCall(
                                    id = "call-screen",
                                    name = "browser_screenshot",
                                    argsJson = """{"name": "todo_app_verified"}"""
                                )
                            )
                        )
                        emit(ChatEvent.Done())
                    }
                    6 -> {
                        // Turn 6: Write walkthrough artifact
                        emit(ChatEvent.TextDelta("Step 6: Writing walkthrough artifact."))
                        emit(
                            ChatEvent.ToolCallChunk(
                                ToolCall(
                                    id = "call-walkthrough",
                                    name = "artifact_write",
                                    argsJson = """{"name": "walkthrough.md", "content": "# Walkthrough\nSuccessfully implemented Todo App with HTML and CSS."}"""
                                )
                            )
                        )
                        emit(ChatEvent.Done())
                    }
                    else -> {
                        // Turn 7: Final Completion
                        emit(ChatEvent.TextDelta("All tasks completed! The Todo Web App has been created and verified."))
                        emit(ChatEvent.Done())
                    }
                }
            }
        }

        val policy = PolicyEngine(AutonomyMode.FULL_AUTONOMY)
        val toolRouter = ToolRouter()
        val agentLoop = AgentLoop(
            modelProvider = scriptedProvider,
            toolRouter = toolRouter,
            policyEngine = policy,
            maxSteps = 10,
            browserController = fakeBrowser
        )

        val events = mutableListOf<AgentEvent>()
        agentLoop.run(
            userPrompt = "Build a todo web app with index.html and style.css",
            workspaceRoot = workspace,
            projectId = "todo_project",
            execBackend = DirectBackend(),
            sessionId = sessionId
        ).collect { event ->
            events.add(event)
        }

        // 1. Verify Event Flow Completion
        assertThat(events.any { it is AgentEvent.Done }).isTrue()
        assertThat(scriptedProvider.turn).isAtLeast(7)

        // 2. Verify File System Modifications
        val indexHtml = File(workspace, "index.html")
        assertThat(indexHtml.exists()).isTrue()
        assertThat(indexHtml.readText()).contains("<h1>Todo List</h1>")

        val styleCss = File(workspace, "style.css")
        assertThat(styleCss.exists()).isTrue()
        assertThat(styleCss.readText()).contains("background: #121212")

        // 3. Verify Artifacts Creation (plan.md, walkthrough.md)
        val artifactsDir = File(workspace, ".agent/artifacts/$sessionId")
        val planFile = File(artifactsDir, "plan.md")
        assertThat(planFile.exists()).isTrue()
        assertThat(planFile.readText()).contains("Todo App Plan")

        val walkthroughFile = File(artifactsDir, "walkthrough.md")
        assertThat(walkthroughFile.exists()).isTrue()
        assertThat(walkthroughFile.readText()).contains("Walkthrough")

        // 4. Verify In-App Browser Interactions and Screenshot Artifact
        assertThat(fakeBrowser.lastOpenedUrl).isEqualTo("http://localhost:3000")
        val screenshotFile = File(artifactsDir, "screens/todo_app_verified.png")
        assertThat(screenshotFile.exists()).isTrue()
    }
}

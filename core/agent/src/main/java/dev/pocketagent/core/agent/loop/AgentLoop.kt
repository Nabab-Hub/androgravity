package dev.pocketagent.core.agent.loop

import dev.pocketagent.core.agent.context.ContextBuilder
import dev.pocketagent.core.agent.policy.PolicyDecision
import dev.pocketagent.core.agent.policy.PolicyEngine
import dev.pocketagent.core.agent.tools.ToolContext
import dev.pocketagent.core.agent.tools.ToolRouter
import dev.pocketagent.core.model.ChatEvent
import dev.pocketagent.core.model.ChatRequest
import dev.pocketagent.core.model.Message
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.core.model.ToolSpec
import dev.pocketagent.core.providers.ModelProvider
import dev.pocketagent.core.sandbox.ExecBackend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface AgentEvent {
    data class StepStarted(val stepIndex: Int) : AgentEvent
    data class TextDelta(val delta: String) : AgentEvent
    data class ToolCallProposed(val toolCall: ToolCall) : AgentEvent
    data class AwaitingApproval(
        val approvalId: String,
        val toolCall: ToolCall,
        val reason: String
    ) : AgentEvent
    data class ToolCallExecuted(val result: Message.ToolResult) : AgentEvent
    data class Done(val totalSteps: Int) : AgentEvent
    data class Cancelled(val reason: String = "Operation cancelled by user.") : AgentEvent
    data class Error(val message: String) : AgentEvent
}

class AgentLoop(
    val modelProvider: ModelProvider,
    val toolRouter: ToolRouter,
    val policyEngine: PolicyEngine,
    val contextBuilder: ContextBuilder = ContextBuilder(),
    val maxSteps: Int = 20,
    val browserController: dev.pocketagent.core.agent.browser.BrowserController? = null
) {

    private val pendingApprovals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    fun approve(approvalId: String) {
        pendingApprovals[approvalId]?.complete(true)
    }

    fun deny(approvalId: String) {
        pendingApprovals[approvalId]?.complete(false)
    }

    fun run(
        userPrompt: String,
        workspaceRoot: File,
        projectId: String,
        execBackend: ExecBackend,
        existingHistory: List<Message> = emptyList(),
        sessionId: String = UUID.randomUUID().toString(),
        model: String = "gemini-1.5-flash",
        activeBrowserController: dev.pocketagent.core.agent.browser.BrowserController? = browserController
    ): Flow<AgentEvent> = flow {
        val toolContext = ToolContext(
            projectId = projectId,
            workspaceRoot = workspaceRoot,
            execBackend = execBackend,
            sessionId = sessionId,
            browserController = activeBrowserController
        )

        val conversation = mutableListOf<Message>()
        conversation.addAll(existingHistory)

        val toolSpecs = toolRouter.getAllTools().map { t ->
            ToolSpec(
                name = t.name,
                description = t.description,
                parametersJson = t.parametersSchema
            )
        }

        var step = 0
        var isCompleted = false

        while (step < maxSteps && !isCompleted) {
            currentCoroutineContext().ensureActive()
            step++
            emit(AgentEvent.StepStarted(step))

            val basePrompt = buildBaseSystemPrompt(workspaceRoot)
            val builtContext = contextBuilder.build(
                baseSystemPrompt = basePrompt,
                workspaceRoot = workspaceRoot,
                sessionId = sessionId,
                history = conversation,
                userPrompt = if (step == 1) userPrompt else ""
            )

            // Sync conversation if compressed or user prompt added
            if (conversation.isEmpty() || (step == 1 && conversation.none { it is Message.User && (it as Message.User).text == userPrompt })) {
                conversation.clear()
                conversation.addAll(builtContext.messages)
            }

            val request = ChatRequest(
                model = model,
                systemPrompt = builtContext.systemPrompt,
                messages = conversation,
                tools = toolSpecs
            )

            val currentAssistantText = StringBuilder()
            val proposedCalls = mutableListOf<ToolCall>()

            try {
                modelProvider.stream(request).collect { event ->
                    currentCoroutineContext().ensureActive()
                    when (event) {
                        is ChatEvent.TextDelta -> {
                            currentAssistantText.append(event.text)
                            emit(AgentEvent.TextDelta(event.text))
                        }
                        is ChatEvent.ToolCallChunk -> {
                            proposedCalls.add(event.call)
                            emit(AgentEvent.ToolCallProposed(event.call))
                        }
                        is ChatEvent.Error -> {
                            emit(AgentEvent.Error(event.message))
                        }
                        else -> {}
                    }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) {
                    emit(AgentEvent.Cancelled())
                    throw t
                }
                emit(AgentEvent.Error("Model stream failed: ${t.message}"))
                return@flow
            }

            if (currentAssistantText.isNotEmpty() || proposedCalls.isNotEmpty()) {
                conversation.add(
                    Message.Assistant(
                        text = currentAssistantText.toString(),
                        toolCalls = proposedCalls
                    )
                )
            }

            // If model didn't call any tools, the turn is finished
            if (proposedCalls.isEmpty()) {
                isCompleted = true
                emit(AgentEvent.Done(step))
                break
            }

            // Execute proposed tool calls
            for (call in proposedCalls) {
                currentCoroutineContext().ensureActive()

                // Policy check
                val decision = policyEngine.evaluate(call, workspaceRoot)

                val executionApproved = when (decision) {
                    is PolicyDecision.Allow -> true
                    is PolicyDecision.Deny -> {
                        val result = Message.ToolResult(
                            callId = call.id,
                            toolName = call.name,
                            ok = false,
                            output = decision.reason
                        )
                        conversation.add(result)
                        emit(AgentEvent.ToolCallExecuted(result))
                        false
                    }
                    is PolicyDecision.RequireApproval -> {
                        val approvalId = UUID.randomUUID().toString()
                        val deferred = CompletableDeferred<Boolean>()
                        pendingApprovals[approvalId] = deferred
                        emit(AgentEvent.AwaitingApproval(approvalId, call, decision.reason))

                        val approved = try {
                            deferred.await()
                        } catch (e: Throwable) {
                            pendingApprovals.remove(approvalId)
                            throw e
                        }
                        pendingApprovals.remove(approvalId)
                        if (!approved) {
                            val result = Message.ToolResult(
                                callId = call.id,
                                toolName = call.name,
                                ok = false,
                                output = "Execution denied by user: ${decision.reason}"
                            )
                            conversation.add(result)
                            emit(AgentEvent.ToolCallExecuted(result))
                        }
                        approved
                    }
                }

                if (executionApproved) {
                    val result = toolRouter.execute(call, toolContext)
                    conversation.add(result)
                    emit(AgentEvent.ToolCallExecuted(result))
                }
            }
        }

        if (!isCompleted && step >= maxSteps) {
            emit(AgentEvent.Done(step))
        }
    }

    private fun buildBaseSystemPrompt(workspaceRoot: File): String {
        return """
            You are PocketAgent, an expert autonomous software engineer and pair programmer running locally on an Android device.
            Active Workspace Path: ${workspaceRoot.absolutePath}

            Available Tools:
            - fs_read_file: inspect file contents in the workspace.
            - fs_write_file: create or overwrite workspace files.
            - fs_edit_file: perform surgical search-and-replace modifications.
            - fs_list_dir: list files and directories.
            - fs_delete_file: delete a file or directory in workspace.
            - search_grep: search text or regex across workspace files.
            - search_glob: find files matching pattern (e.g. *.kt).
            - terminal_run: execute shell commands in the Linux environment.
            - git_status, git_diff, git_commit, git_log, git_branch: version control tools.
            - artifact_write: write plan.md, tasks.md, walkthrough.md.

            Rules:
            1. Always inspect existing code before making edits.
            2. For targeted modifications, prefer fs_edit_file over overwriting entire files.
            3. When asked to create an application or solve a coding task, complete it end-to-end using your tools.
            4. Never invent file paths outside the workspace.
            5. Provide clear, concise status updates as you work.
        """.trimIndent()
    }
}

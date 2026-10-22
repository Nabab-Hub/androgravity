package dev.pocketagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketagent.PocketAgentApp
import dev.pocketagent.core.agent.loop.AgentEvent
import dev.pocketagent.core.agent.loop.AgentLoop
import dev.pocketagent.core.agent.policy.AutonomyMode
import dev.pocketagent.core.agent.policy.PolicyEngine
import dev.pocketagent.core.model.Message
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.core.storage.SecurePreferences
import dev.pocketagent.core.storage.database.AppDatabase
import dev.pocketagent.core.storage.database.MessageEntity
import dev.pocketagent.core.storage.database.SessionEntity
import dev.pocketagent.service.AgentForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class PendingApproval(
    val id: String,
    val toolCall: ToolCall,
    val reason: String
)

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
    val currentStep: Int = 0,
    val pendingApproval: PendingApproval? = null,
    val autonomyMode: AutonomyMode = AutonomyMode.SEMI_AUTONOMY,
    val errorMessage: String? = null,
    val currentSessionId: String = UUID.randomUUID().toString()
)

class ChatViewModel(
    private val app: PocketAgentApp = PocketAgentApp.instance,
    private val agentLoop: AgentLoop = app.agentLoop,
    private val policyEngine: PolicyEngine = app.policyEngine,
    private val database: AppDatabase = app.database,
    private val securePreferences: SecurePreferences = app.securePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var agentJob: Job? = null

    init {
        _uiState.update {
            it.copy(
                autonomyMode = policyEngine.autonomyMode,
                messages = listOf(
                    Message.Assistant(
                        text = "Hello! I am Maza Agent. I can inspect files, write code, run shell commands, and autonomously build your projects. What would you like to build?"
                    )
                )
            )
        }
    }

    fun setAutonomyMode(mode: AutonomyMode) {
        policyEngine.autonomyMode = mode
        _uiState.update { it.copy(autonomyMode = mode) }
    }

    fun sendMessage(userPrompt: String) {
        if (userPrompt.isBlank() || _uiState.value.isStreaming) return

        val userMessage = Message.User(text = userPrompt.trim())
        val historyBeforeThis = _uiState.value.messages

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isStreaming = true,
                errorMessage = null,
                currentStep = 0,
                pendingApproval = null
            )
        }

        val projects = app.workspaceManager.listProjects()
        val defaultProjectDir = if (projects.isNotEmpty()) {
            File(projects.first().path)
        } else {
            app.workspaceManager.workspacesRoot
        }

        val execBackend = app.getActiveExecBackend()

        val selectedModel = securePreferences.getString(SecurePreferences.KEY_SELECTED_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"

        agentJob = viewModelScope.launch {
            val runSessionId = _uiState.value.currentSessionId
            AgentForegroundService.start(app, runSessionId, userPrompt.take(40))

            try {
                var activeAssistantId = UUID.randomUUID().toString()
                var activeAssistantText = ""
                var activeToolCalls = mutableListOf<ToolCall>()

                agentLoop.run(
                    userPrompt = userPrompt,
                    workspaceRoot = defaultProjectDir,
                    projectId = defaultProjectDir.name,
                    execBackend = execBackend,
                    existingHistory = historyBeforeThis,
                    model = selectedModel
                ).collect { event ->
                    when (event) {
                        is AgentEvent.StepStarted -> {
                            _uiState.update { it.copy(currentStep = event.stepIndex) }
                            AgentForegroundService.updateProgress(app, event.stepIndex, 20, "Step ${event.stepIndex}")
                            activeAssistantId = UUID.randomUUID().toString()
                            activeAssistantText = ""
                            activeToolCalls = mutableListOf()
                        }
                        is AgentEvent.TextDelta -> {
                            activeAssistantText += event.delta
                            updateOrAppendAssistantMessage(activeAssistantId, activeAssistantText, activeToolCalls)
                        }
                        is AgentEvent.ToolCallProposed -> {
                            activeToolCalls.add(event.toolCall)
                            updateOrAppendAssistantMessage(activeAssistantId, activeAssistantText, activeToolCalls)
                        }
                        is AgentEvent.AwaitingApproval -> {
                            _uiState.update {
                                it.copy(
                                    pendingApproval = PendingApproval(
                                        id = event.approvalId,
                                        toolCall = event.toolCall,
                                        reason = event.reason
                                    )
                                )
                            }
                            AgentForegroundService.showApproval(app, event.approvalId, event.toolCall.name, event.reason)
                        }
                        is AgentEvent.ToolCallExecuted -> {
                            _uiState.update {
                                it.copy(
                                    messages = it.messages + event.result,
                                    pendingApproval = null
                                )
                            }
                        }
                        is AgentEvent.Done -> {
                            _uiState.update { it.copy(isStreaming = false, pendingApproval = null) }
                            persistSessionAndMessages()
                        }
                        is AgentEvent.Cancelled -> {
                            _uiState.update {
                                it.copy(
                                    isStreaming = false,
                                    pendingApproval = null,
                                    messages = it.messages + Message.System(text = "Agent execution cancelled.")
                                )
                            }
                        }
                        is AgentEvent.Error -> {
                            _uiState.update {
                                it.copy(
                                    isStreaming = false,
                                    pendingApproval = null,
                                    errorMessage = event.message
                                )
                            }
                        }
                    }
                }
            } finally {
                AgentForegroundService.stop(app)
                _uiState.update { it.copy(isStreaming = false) }
            }
        }
    }

    fun approvePending() {
        val pending = _uiState.value.pendingApproval ?: return
        _uiState.update { it.copy(pendingApproval = null) }
        agentLoop.approve(pending.id)
    }

    fun denyPending() {
        val pending = _uiState.value.pendingApproval ?: return
        _uiState.update { it.copy(pendingApproval = null) }
        agentLoop.deny(pending.id)
    }

    fun cancelStream() {
        agentJob?.cancel()
        _uiState.update {
            it.copy(
                isStreaming = false,
                pendingApproval = null,
                messages = it.messages + Message.System(text = "Agent execution stopped by user.")
            )
        }
    }

    private fun updateOrAppendAssistantMessage(id: String, text: String, tools: List<ToolCall>) {
        _uiState.update { state ->
            val exists = state.messages.any { it is Message.Assistant && it.id == id }
            val updated = if (exists) {
                state.messages.map { msg ->
                    if (msg is Message.Assistant && msg.id == id) {
                        msg.copy(text = text, toolCalls = tools)
                    } else {
                        msg
                    }
                }
            } else {
                state.messages + Message.Assistant(id = id, text = text, toolCalls = tools)
            }
            state.copy(messages = updated)
        }
    }

    fun loadSession(sessionId: String) {
        if (_uiState.value.currentSessionId == sessionId && _uiState.value.messages.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(currentSessionId = sessionId) }
            database.messageDao().getMessagesForSession(sessionId).collect { entities ->
                if (entities.isNotEmpty()) {
                    val loaded = entities.map { entity ->
                        when (entity.role) {
                            "user" -> Message.User(id = entity.id, text = entity.content, timestamp = entity.timestamp)
                            "assistant" -> Message.Assistant(id = entity.id, text = entity.content, timestamp = entity.timestamp)
                            "tool_result" -> Message.ToolResult(
                                id = entity.id,
                                callId = entity.toolCallId ?: "",
                                toolName = entity.toolName ?: "",
                                ok = entity.ok ?: true,
                                output = entity.content,
                                timestamp = entity.timestamp
                            )
                            else -> Message.System(id = entity.id, text = entity.content, timestamp = entity.timestamp)
                        }
                    }
                    _uiState.update { it.copy(messages = loaded) }
                }
            }
        }
    }

    private fun persistSessionAndMessages() {
        viewModelScope.launch {
            try {
                val sessionId = _uiState.value.currentSessionId
                val session = SessionEntity(
                    id = sessionId,
                    projectId = "default",
                    title = _uiState.value.messages.firstOrNull { it is Message.User }?.let { (it as Message.User).text.take(30) } ?: "Agent Task",
                    status = "DONE",
                    mode = _uiState.value.autonomyMode.name,
                    model = "gemini-1.5-flash",
                    currentStep = _uiState.value.currentStep,
                    maxSteps = 20,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                database.sessionDao().upsertSession(session)

                for (msg in _uiState.value.messages) {
                    val entity = when (msg) {
                        is Message.User -> MessageEntity(
                            id = msg.id,
                            sessionId = sessionId,
                            role = "user",
                            content = msg.text,
                            timestamp = msg.timestamp
                        )
                        is Message.Assistant -> MessageEntity(
                            id = msg.id,
                            sessionId = sessionId,
                            role = "assistant",
                            content = msg.text,
                            timestamp = msg.timestamp
                        )
                        is Message.ToolResult -> MessageEntity(
                            id = msg.id,
                            sessionId = sessionId,
                            role = "tool_result",
                            content = msg.output,
                            toolCallId = msg.callId,
                            toolName = msg.toolName,
                            ok = msg.ok,
                            timestamp = msg.timestamp
                        )
                        is Message.System -> MessageEntity(
                            id = msg.id,
                            sessionId = sessionId,
                            role = "system",
                            content = msg.text,
                            timestamp = msg.timestamp
                        )
                    }
                    database.messageDao().insertMessage(entity)
                }
            } catch (_: Throwable) {
            }
        }
    }
}

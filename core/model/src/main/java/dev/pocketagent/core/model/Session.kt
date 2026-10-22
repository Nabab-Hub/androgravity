package dev.pocketagent.core.model

import kotlinx.serialization.Serializable

enum class AutonomyMode {
    ASK_ALWAYS,
    AGENT_DECIDES,
    ALWAYS_PROCEED
}

enum class SessionStatus {
    PLANNING,
    RUNNING,
    WAITING_APPROVAL,
    DONE,
    FAILED,
    CANCELLED
}

@Serializable
data class AgentSession(
    val id: String,
    val projectId: String = "default",
    val title: String,
    val status: SessionStatus = SessionStatus.PLANNING,
    val mode: AutonomyMode = AutonomyMode.AGENT_DECIDES,
    val model: String = "gemini-1.5-flash",
    val currentStep: Int = 0,
    val maxSteps: Int = 20,
    val elapsedTimeMs: Long = 0L,
    val changedFilesCount: Int = 0,
    val worktreePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ProviderType {
    GEMINI,
    ANTHROPIC,
    OPENAI_COMPAT
}

@Serializable
data class ProviderConfig(
    val type: ProviderType,
    val apiKey: String,
    val selectedModel: String
)

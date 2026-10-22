package dev.pocketagent.core.storage

import dev.pocketagent.core.model.AgentSession
import dev.pocketagent.core.model.SessionStatus
import kotlinx.coroutines.flow.first

class SessionRecoveryManager(
    private val sessionRepository: SessionRepository
) {

    /**
     * Scans for sessions left in active states (RUNNING, WAITING_APPROVAL) that may have been
     * interrupted by OS process death, crash, or battery killer, and transitions them into
     * recoverable paused state.
     */
    suspend fun recoverInterruptedSessions(): List<AgentSession> {
        val allSessions = sessionRepository.getAllSessions().first()
        val interrupted = allSessions.filter {
            it.status == SessionStatus.RUNNING || it.status == SessionStatus.WAITING_APPROVAL
        }

        for (session in interrupted) {
            val recoveredSession = session.copy(
                status = SessionStatus.WAITING_APPROVAL,
                updatedAt = System.currentTimeMillis()
            )
            sessionRepository.upsertSession(recoveredSession)
        }

        return interrupted
    }
}

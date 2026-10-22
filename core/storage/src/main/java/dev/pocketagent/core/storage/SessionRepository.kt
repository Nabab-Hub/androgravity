package dev.pocketagent.core.storage

import dev.pocketagent.core.model.AgentSession
import dev.pocketagent.core.model.AutonomyMode
import dev.pocketagent.core.model.SessionStatus
import dev.pocketagent.core.storage.database.AppDatabase
import dev.pocketagent.core.storage.database.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(
    private val database: AppDatabase
) {
    private val sessionDao = database.sessionDao()

    fun getAllSessions(): Flow<List<AgentSession>> {
        return sessionDao.getAllSessions().map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getSessionsForProject(projectId: String): Flow<List<AgentSession>> {
        return sessionDao.getSessionsForProject(projectId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getSessionById(sessionId: String): AgentSession? {
        return sessionDao.getSessionById(sessionId)?.toDomain()
    }

    suspend fun upsertSession(session: AgentSession) {
        sessionDao.upsertSession(session.toEntity())
    }

    suspend fun updateSessionProgress(
        sessionId: String,
        status: SessionStatus,
        step: Int? = null,
        changedFiles: Int? = null,
        elapsedTimeMs: Long? = null
    ) {
        val existing = sessionDao.getSessionById(sessionId) ?: return
        val updated = existing.copy(
            status = status.name,
            currentStep = step ?: existing.currentStep,
            changedFilesCount = changedFiles ?: existing.changedFilesCount,
            elapsedTimeMs = elapsedTimeMs ?: existing.elapsedTimeMs,
            updatedAt = System.currentTimeMillis()
        )
        sessionDao.upsertSession(updated)
    }

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSession(sessionId)
    }

    private fun SessionEntity.toDomain(): AgentSession {
        val statusVal = try {
            SessionStatus.valueOf(status)
        } catch (_: Throwable) {
            SessionStatus.PLANNING
        }
        val modeVal = try {
            AutonomyMode.valueOf(mode)
        } catch (_: Throwable) {
            AutonomyMode.AGENT_DECIDES
        }

        return AgentSession(
            id = id,
            projectId = projectId,
            title = title,
            status = statusVal,
            mode = modeVal,
            model = model,
            currentStep = currentStep,
            maxSteps = maxSteps,
            elapsedTimeMs = elapsedTimeMs,
            changedFilesCount = changedFilesCount,
            worktreePath = worktreePath,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun AgentSession.toEntity(): SessionEntity {
        return SessionEntity(
            id = id,
            projectId = projectId,
            title = title,
            status = status.name,
            mode = mode.name,
            model = model,
            currentStep = currentStep,
            maxSteps = maxSteps,
            elapsedTimeMs = elapsedTimeMs,
            changedFilesCount = changedFilesCount,
            worktreePath = worktreePath,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

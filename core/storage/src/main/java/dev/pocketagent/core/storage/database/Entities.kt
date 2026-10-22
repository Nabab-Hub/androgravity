package dev.pocketagent.core.storage.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.pocketagent.core.model.AutonomyMode
import dev.pocketagent.core.model.SessionStatus

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val projectId: String = "default",
    val title: String,
    val status: String,
    val mode: String,
    val model: String,
    val currentStep: Int = 0,
    val maxSteps: Int = 20,
    val elapsedTimeMs: Long = 0L,
    val changedFilesCount: Int = 0,
    val worktreePath: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val ok: Boolean? = null,
    val timestamp: Long
)

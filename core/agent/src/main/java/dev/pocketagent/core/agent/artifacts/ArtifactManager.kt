package dev.pocketagent.core.agent.artifacts

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

data class TaskItem(
    val index: Int,
    val text: String,
    val isCompleted: Boolean
)

@Serializable
data class ArtifactComment(
    val id: String = UUID.randomUUID().toString(),
    val artifactName: String,
    val lineNumber: Int? = null,
    val sectionHeader: String? = null,
    val commentText: String,
    val author: String = "User",
    val timestamp: Long = System.currentTimeMillis()
)

class ArtifactManager {
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun getArtifactsDir(workspaceRoot: File, sessionId: String): File {
        val dir = File(workspaceRoot, ".agent/artifacts/$sessionId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // --- Plan ---
    fun getPlan(workspaceRoot: File, sessionId: String): String? {
        val file = File(getArtifactsDir(workspaceRoot, sessionId), "plan.md")
        return if (file.exists() && file.isFile) file.readText() else null
    }

    fun savePlan(workspaceRoot: File, sessionId: String, content: String) {
        val file = File(getArtifactsDir(workspaceRoot, sessionId), "plan.md")
        file.writeText(content)
    }

    // --- Tasks ---
    fun getTasks(workspaceRoot: File, sessionId: String): String? {
        val file = File(getArtifactsDir(workspaceRoot, sessionId), "tasks.md")
        return if (file.exists() && file.isFile) file.readText() else null
    }

    fun saveTasks(workspaceRoot: File, sessionId: String, content: String) {
        val file = File(getArtifactsDir(workspaceRoot, sessionId), "tasks.md")
        file.writeText(content)
    }

    fun parseTasks(workspaceRoot: File, sessionId: String): List<TaskItem> {
        val text = getTasks(workspaceRoot, sessionId) ?: return emptyList()
        val items = mutableListOf<TaskItem>()
        var taskIdx = 0

        text.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]")) {
                val taskText = trimmed.removePrefix("- [ ]").removePrefix("* [ ]").trim()
                items.add(TaskItem(taskIdx++, taskText, isCompleted = false))
            } else if (trimmed.startsWith("- [x]") || trimmed.startsWith("* [x]") || trimmed.startsWith("- [X]") || trimmed.startsWith("* [X]")) {
                val taskText = trimmed.removePrefix("- [x]").removePrefix("* [x]").removePrefix("- [X]").removePrefix("* [X]").trim()
                items.add(TaskItem(taskIdx++, taskText, isCompleted = true))
            }
        }
        return items
    }

    fun toggleTask(workspaceRoot: File, sessionId: String, taskIndex: Int): Boolean {
        val file = File(getArtifactsDir(workspaceRoot, sessionId), "tasks.md")
        if (!file.exists()) return false

        val lines = file.readLines().toMutableList()
        var currentIdx = 0
        var found = false

        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            val isUnchecked = trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]")
            val isChecked = trimmed.startsWith("- [x]") || trimmed.startsWith("* [x]") || trimmed.startsWith("- [X]") || trimmed.startsWith("* [X]")

            if (isUnchecked || isChecked) {
                if (currentIdx == taskIndex) {
                    lines[i] = if (isUnchecked) {
                        lines[i].replaceFirst("[ ]", "[x]")
                    } else {
                        lines[i].replaceFirst("[x]", "[ ]").replaceFirst("[X]", "[ ]")
                    }
                    found = true
                    break
                }
                currentIdx++
            }
        }

        if (found) {
            file.writeText(lines.joinToString("\n"))
        }
        return found
    }

    // --- Walkthrough ---
    fun getWalkthrough(workspaceRoot: File, sessionId: String): String? {
        val file = File(getArtifactsDir(workspaceRoot, sessionId), "walkthrough.md")
        return if (file.exists() && file.isFile) file.readText() else null
    }

    fun saveWalkthrough(workspaceRoot: File, sessionId: String, content: String) {
        val file = File(getArtifactsDir(workspaceRoot, sessionId), "walkthrough.md")
        file.writeText(content)
    }

    // --- Comments ---
    fun getComments(workspaceRoot: File, sessionId: String): List<ArtifactComment> {
        val file = File(getArtifactsDir(workspaceRoot, sessionId), "comments.json")
        if (!file.exists() || !file.isFile) return emptyList()

        return try {
            json.decodeFromString<List<ArtifactComment>>(file.readText())
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun addComment(workspaceRoot: File, sessionId: String, comment: ArtifactComment) {
        val existing = getComments(workspaceRoot, sessionId).toMutableList()
        existing.add(comment)
        val file = File(getArtifactsDir(workspaceRoot, sessionId), "comments.json")
        file.writeText(json.encodeToString(existing))
    }

    fun deleteComment(workspaceRoot: File, sessionId: String, commentId: String): Boolean {
        val existing = getComments(workspaceRoot, sessionId).toMutableList()
        val removed = existing.removeAll { it.id == commentId }
        if (removed) {
            val file = File(getArtifactsDir(workspaceRoot, sessionId), "comments.json")
            file.writeText(json.encodeToString(existing))
        }
        return removed
    }

    fun formatCommentsForAgent(workspaceRoot: File, sessionId: String): String {
        val comments = getComments(workspaceRoot, sessionId)
        if (comments.isEmpty()) return ""

        return buildString {
            appendLine("=== User Review Comments ===")
            appendLine("The user has left the following feedback on your artifacts/code. Please address them:")
            comments.forEach { c ->
                val loc = buildString {
                    append(c.artifactName)
                    if (c.lineNumber != null) append(" (Line ${c.lineNumber})")
                    if (!c.sectionHeader.isNullOrBlank()) append(" in section '${c.sectionHeader}'")
                }
                appendLine("- [$loc] ${c.author}: \"${c.commentText}\"")
            }
            appendLine("============================")
        }.trim()
    }
}

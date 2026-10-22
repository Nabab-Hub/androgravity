package dev.pocketagent.core.agent.checkpoint

import dev.pocketagent.core.sandbox.ExecBackend
import java.io.File

data class CheckpointInfo(
    val commitHash: String,
    val message: String,
    val timestamp: Long
)

data class ChangedFile(
    val statusCode: String, // "M ", "A ", "D ", "??"
    val path: String
)

class CheckpointManager(
    private val execBackend: ExecBackend
) {

    private val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true

    private suspend fun runGit(workspaceRoot: File, gitCommand: String): String {
        val cmd = if (isWindows) {
            listOf("cmd.exe", "/c", "git $gitCommand")
        } else {
            listOf("/bin/sh", "-c", "git $gitCommand")
        }
        val res = execBackend.exec(cmd, cwd = workspaceRoot.absolutePath)
        return (res.stdout + if (res.stderr.isNotBlank() && res.exitCode != 0) "\n${res.stderr}" else "").trim()
    }

    suspend fun ensureGitRepo(workspaceRoot: File): Boolean {
        val gitDir = File(workspaceRoot, ".git")
        if (!gitDir.exists()) {
            runGit(workspaceRoot, "init")
            runGit(workspaceRoot, "config user.name \"PocketAgent\"")
            runGit(workspaceRoot, "config user.email \"agent@pocketagent.dev\"")
            // Create initial commit if empty
            runGit(workspaceRoot, "add -A")
            runGit(workspaceRoot, "commit -m \"Initial commit\" --allow-empty")
            return true
        }
        return false
    }

    suspend fun getHeadCommit(workspaceRoot: File): String {
        ensureGitRepo(workspaceRoot)
        return runGit(workspaceRoot, "rev-parse HEAD").trim()
    }

    suspend fun createCheckpoint(
        workspaceRoot: File,
        sessionId: String,
        step: Int,
        message: String = "checkpoint"
    ): String {
        ensureGitRepo(workspaceRoot)
        runGit(workspaceRoot, "add -A")
        val safeMsg = "checkpoint: step $step - $message".replace("\"", "\\\"")
        runGit(workspaceRoot, "commit -m \"$safeMsg\" --allow-empty")
        val head = runGit(workspaceRoot, "rev-parse HEAD").trim()
        if (head.isNotBlank() && !head.startsWith("fatal")) {
            runGit(workspaceRoot, "update-ref refs/agent/$sessionId $head")
        }
        return head
    }

    suspend fun undoLastStep(workspaceRoot: File): Boolean {
        ensureGitRepo(workspaceRoot)
        val res = runGit(workspaceRoot, "reset --hard HEAD~1")
        return !res.startsWith("fatal")
    }

    suspend fun revertSession(workspaceRoot: File, startCommitHash: String): Boolean {
        ensureGitRepo(workspaceRoot)
        val res = runGit(workspaceRoot, "reset --hard $startCommitHash")
        return !res.startsWith("fatal")
    }

    suspend fun getDiff(workspaceRoot: File, staged: Boolean = false, path: String? = null): String {
        ensureGitRepo(workspaceRoot)
        val cmd = buildString {
            append("diff")
            if (staged) append(" --cached")
            if (!path.isNullOrBlank()) append(" -- \"$path\"")
        }
        val out = runGit(workspaceRoot, cmd)
        return if (out.isBlank()) "(no changes)" else out
    }

    suspend fun getChangedFiles(workspaceRoot: File): List<ChangedFile> {
        ensureGitRepo(workspaceRoot)
        val out = runGit(workspaceRoot, "status --porcelain")
        if (out.isBlank()) return emptyList()

        return out.lines().mapNotNull { line ->
            if (line.length >= 4) {
                val status = line.substring(0, 2)
                val path = line.substring(3).trim()
                ChangedFile(statusCode = status, path = path)
            } else null
        }
    }

    suspend fun getCheckpointHistory(workspaceRoot: File, limit: Int = 10): List<CheckpointInfo> {
        ensureGitRepo(workspaceRoot)
        val out = runGit(workspaceRoot, "log -n $limit --format=\"%H|%s|%ct\"")
        if (out.isBlank() || out.startsWith("fatal")) return emptyList()

        return out.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 3) {
                val hash = parts[0]
                val msg = parts[1]
                val timeSec = parts[2].toLongOrNull() ?: 0L
                CheckpointInfo(commitHash = hash, message = msg, timestamp = timeSec * 1000L)
            } else null
        }
    }
}

package dev.pocketagent.core.agent.worktree

import dev.pocketagent.core.sandbox.ExecBackend
import java.io.File

class WorktreeManager(
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

    fun getWorktreesDir(workspaceRoot: File): File {
        val dir = File(workspaceRoot, ".worktrees")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun createWorktree(workspaceRoot: File, sessionId: String): File {
        val worktreeDir = File(getWorktreesDir(workspaceRoot), sessionId)
        if (worktreeDir.exists()) {
            return worktreeDir
        }

        val branchName = "session/$sessionId"
        val relPath = ".worktrees/$sessionId"
        val out = runGit(workspaceRoot, "worktree add -b \"$branchName\" \"$relPath\" HEAD")

        if (!worktreeDir.exists() || out.contains("fatal:")) {
            // Fallback: Copy-on-write directory
            worktreeDir.mkdirs()
            copyWorkspaceFiles(workspaceRoot, worktreeDir)
        }

        return worktreeDir
    }

    suspend fun removeWorktree(workspaceRoot: File, sessionId: String): Boolean {
        val branchName = "session/$sessionId"
        val relPath = ".worktrees/$sessionId"
        val worktreeDir = File(getWorktreesDir(workspaceRoot), sessionId)

        runGit(workspaceRoot, "worktree remove --force \"$relPath\"")
        runGit(workspaceRoot, "branch -D \"$branchName\"")

        if (worktreeDir.exists()) {
            worktreeDir.deleteRecursively()
        }
        return !worktreeDir.exists()
    }

    suspend fun mergeWorktree(workspaceRoot: File, sessionId: String): String {
        val branchName = "session/$sessionId"
        val out = runGit(workspaceRoot, "merge --no-ff \"$branchName\" -m \"Merge agent session $sessionId\"")
        return out
    }

    private fun copyWorkspaceFiles(source: File, target: File) {
        val ignoredDirs = setOf(".git", ".worktrees", ".agent", "node_modules", "build", ".gradle")
        source.walkTopDown().onEnter { dir ->
            !ignoredDirs.contains(dir.name)
        }.forEach { file ->
            if (file.isFile) {
                val rel = file.relativeTo(source).path
                val destFile = File(target, rel)
                destFile.parentFile?.mkdirs()
                file.copyTo(destFile, overwrite = true)
            }
        }
    }
}

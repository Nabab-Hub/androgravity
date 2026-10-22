package dev.pocketagent.core.agent.tools

import dev.pocketagent.core.sandbox.ExecBackend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class ToolContext(
    val projectId: String,
    val workspaceRoot: File,
    val execBackend: ExecBackend,
    val sessionId: String = "default",
    val browserController: dev.pocketagent.core.agent.browser.BrowserController? = null
)

interface Tool {
    val name: String
    val description: String
    val parametersSchema: String
    val isReadOnly: Boolean

    suspend fun execute(argsJson: String, context: ToolContext): String
}

class FsReadFileTool : Tool {
    override val name: String = "fs_read_file"
    override val description: String = "Read file contents from the workspace. Supports start/end line slice."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "path": { "type": "string", "description": "Relative path to file in workspace" },
            "start_line": { "type": "integer", "description": "Optional 1-indexed start line" },
            "end_line": { "type": "integer", "description": "Optional 1-indexed end line" }
          },
          "required": ["path"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val path = root["path"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'path' argument")
        val startLine = root["start_line"]?.jsonPrimitive?.intOrNull
        val endLine = root["end_line"]?.jsonPrimitive?.intOrNull

        val file = File(context.workspaceRoot, path)
        if (!file.canonicalPath.startsWith(context.workspaceRoot.canonicalPath)) {
            throw SecurityException("Path escapes workspace boundary: $path")
        }
        if (!file.exists()) {
            return "Error: File '$path' does not exist."
        }
        if (file.isDirectory) {
            return "Error: '$path' is a directory, not a file. Use fs_list_dir instead."
        }

        val lines = file.readLines()
        val start = (startLine ?: 1).coerceAtLeast(1) - 1
        val end = (endLine ?: lines.size).coerceAtMost(lines.size)

        if (start >= lines.size) {
            return "[Empty content: start_line is beyond end of file]"
        }

        val sliced = lines.subList(start, maxOf(start, end))
        val numbered = sliced.mapIndexed { idx, line ->
            "${start + idx + 1}: $line"
        }.joinToString("\n")

        return numbered
    }
}

class FsWriteFileTool : Tool {
    override val name: String = "fs_write_file"
    override val description: String = "Create or overwrite a file in the workspace."
    override val isReadOnly: Boolean = false
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "path": { "type": "string", "description": "Relative path to file in workspace" },
            "content": { "type": "string", "description": "File content to write" },
            "overwrite": { "type": "boolean", "description": "Allow overwriting if exists" }
          },
          "required": ["path", "content"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val path = root["path"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'path'")
        val content = root["content"]?.jsonPrimitive?.content ?: ""
        val overwrite = root["overwrite"]?.jsonPrimitive?.booleanOrNull ?: true

        val file = File(context.workspaceRoot, path)
        if (!file.canonicalPath.startsWith(context.workspaceRoot.canonicalPath)) {
            throw SecurityException("Path escapes workspace: $path")
        }
        if (file.exists() && !overwrite) {
            return "Error: File '$path' already exists and overwrite is set to false."
        }

        file.parentFile?.mkdirs()
        file.writeText(content)
        return "Successfully wrote ${content.length} characters to '$path'."
    }
}

class FsEditFileTool : Tool {
    override val name: String = "fs_edit_file"
    override val description: String = "Perform exact search-and-replace edit on a workspace file."
    override val isReadOnly: Boolean = false
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "path": { "type": "string", "description": "Relative path to file" },
            "target_content": { "type": "string", "description": "Exact text to replace" },
            "replacement_content": { "type": "string", "description": "New replacement content" },
            "allow_multiple": { "type": "boolean", "description": "Replace multiple occurrences" }
          },
          "required": ["path", "target_content", "replacement_content"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val path = root["path"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'path'")
        val target = root["target_content"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'target_content'")
        val replacement = root["replacement_content"]?.jsonPrimitive?.content ?: ""
        val allowMultiple = root["allow_multiple"]?.jsonPrimitive?.booleanOrNull ?: false

        val file = File(context.workspaceRoot, path)
        if (!file.canonicalPath.startsWith(context.workspaceRoot.canonicalPath)) {
            throw SecurityException("Path escapes workspace: $path")
        }
        if (!file.exists()) {
            return "Error: File '$path' does not exist."
        }

        val existing = file.readText()
        if (!existing.contains(target)) {
            return "Error: target_content not found in '$path'. Make sure the text matches exactly."
        }

        val count = existing.windowed(target.length) { if (it == target) 1 else 0 }.sum()
        if (count > 1 && !allowMultiple) {
            return "Error: target_content found $count times in '$path'. Set allow_multiple to true or provide a more unique snippet."
        }

        val newContent = if (allowMultiple) {
            existing.replace(target, replacement)
        } else {
            existing.replaceFirst(target, replacement)
        }

        file.writeText(newContent)
        return "Successfully replaced target content in '$path'."
    }
}

class FsListDirTool : Tool {
    override val name: String = "fs_list_dir"
    override val description: String = "List entries in a workspace directory."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "path": { "type": "string", "description": "Relative directory path (empty for root)" },
            "recursive": { "type": "boolean", "description": "Recursively list children" }
          }
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (_: Throwable) {
            null
        }
        val relPath = root?.get("path")?.jsonPrimitive?.content ?: ""
        val recursive = root?.get("recursive")?.jsonPrimitive?.booleanOrNull ?: false

        val dir = if (relPath.isBlank()) context.workspaceRoot else File(context.workspaceRoot, relPath)
        if (!dir.canonicalPath.startsWith(context.workspaceRoot.canonicalPath)) {
            throw SecurityException("Path escapes workspace: $relPath")
        }
        if (!dir.exists() || !dir.isDirectory) {
            return "Error: Directory '${relPath.ifBlank { "/" }}' does not exist."
        }

        val results = mutableListOf<String>()
        if (recursive) {
            dir.walkTopDown().maxDepth(5).forEach { file ->
                val rel = file.relativeTo(context.workspaceRoot).path.replace('\\', '/')
                val type = if (file.isDirectory) "DIR " else "FILE"
                val size = if (file.isFile) " (${file.length()} bytes)" else ""
                results.add("[$type] $rel$size")
            }
        } else {
            dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { file ->
                val type = if (file.isDirectory) "DIR " else "FILE"
                val size = if (file.isFile) " (${file.length()} bytes)" else ""
                results.add("[$type] ${file.name}$size")
            }
        }

        return if (results.isEmpty()) "(empty directory)" else results.joinToString("\n")
    }
}

class SearchGrepTool : Tool {
    override val name: String = "search_grep"
    override val description: String = "Search text patterns or regex across workspace files."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "query": { "type": "string", "description": "Text or regex search pattern" },
            "path": { "type": "string", "description": "Subdirectory to restrict search" },
            "case_insensitive": { "type": "boolean", "description": "Ignore case" }
          },
          "required": ["query"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val query = root["query"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'query'")
        val relPath = root["path"]?.jsonPrimitive?.content ?: ""
        val caseInsensitive = root["case_insensitive"]?.jsonPrimitive?.booleanOrNull ?: false

        val searchDir = if (relPath.isBlank()) context.workspaceRoot else File(context.workspaceRoot, relPath)
        if (!searchDir.canonicalPath.startsWith(context.workspaceRoot.canonicalPath)) {
            throw SecurityException("Path escapes workspace")
        }

        val matches = mutableListOf<String>()
        searchDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") && it.length() < 1_000_000 }
            .take(200)
            .forEach { file ->
                try {
                    val rel = file.relativeTo(context.workspaceRoot).path.replace('\\', '/')
                    file.useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            val found = if (caseInsensitive) {
                                line.contains(query, ignoreCase = true)
                            } else {
                                line.contains(query)
                            }
                            if (found && matches.size < 50) {
                                matches.add("$rel:${idx + 1}: ${line.trim()}")
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }

        return if (matches.isEmpty()) "No matches found for '$query'." else matches.joinToString("\n")
    }
}

class TerminalRunTool : Tool {
    override val name: String = "terminal_run"
    override val description: String = "Execute a shell command inside the workspace environment."
    override val isReadOnly: Boolean = false
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "command": { "type": "string", "description": "Exact shell command to run" },
            "cwd": { "type": "string", "description": "Working directory relative to workspace root" }
          },
          "required": ["command"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val command = root["command"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'command'")
        val relCwd = root["cwd"]?.jsonPrimitive?.content ?: ""

        val workDir = if (relCwd.isNotBlank()) File(context.workspaceRoot, relCwd) else context.workspaceRoot
        if (!workDir.canonicalPath.startsWith(context.workspaceRoot.canonicalPath)) {
            throw SecurityException("Working directory escapes workspace")
        }

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        val cmdList = if (isWindows) {
            listOf("cmd.exe", "/c", command)
        } else {
            listOf("/bin/sh", "-c", command)
        }

        val result = context.execBackend.exec(cmdList, cwd = workDir.absolutePath)
        return buildString {
            appendLine("Exit Code: ${result.exitCode}")
            if (result.stdout.isNotBlank()) {
                appendLine("Output:\n${result.stdout.trim()}")
            }
            if (result.stderr.isNotBlank()) {
                appendLine("Stderr:\n${result.stderr.trim()}")
            }
        }
    }
}

class FsDeleteFileTool : Tool {
    override val name: String = "fs_delete_file"
    override val description: String = "Delete a file or empty directory in the workspace."
    override val isReadOnly: Boolean = false
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "path": { "type": "string", "description": "Relative path to file or directory" },
            "recursive": { "type": "boolean", "description": "Recursively delete if directory" }
          },
          "required": ["path"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val path = root["path"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'path'")
        val recursive = root["recursive"]?.jsonPrimitive?.booleanOrNull ?: false

        val file = File(context.workspaceRoot, path)
        if (!file.canonicalPath.startsWith(context.workspaceRoot.canonicalPath)) {
            throw SecurityException("Path escapes workspace boundary: $path")
        }
        if (!file.exists()) {
            return "Error: File or directory '$path' does not exist."
        }

        val success = if (file.isDirectory && recursive) {
            file.deleteRecursively()
        } else {
            file.delete()
        }

        return if (success) {
            "Successfully deleted '$path'."
        } else {
            "Error: Failed to delete '$path'. If non-empty directory, set recursive to true."
        }
    }
}

class SearchGlobTool : Tool {
    override val name: String = "search_glob"
    override val description: String = "Find files in the workspace matching a glob pattern (e.g. *.kt, **/*.json)."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "pattern": { "type": "string", "description": "Glob pattern (e.g. *.kt, **/*.json)" },
            "path": { "type": "string", "description": "Optional subdirectory to restrict search" }
          },
          "required": ["pattern"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val pattern = root["pattern"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'pattern'")
        val relPath = root["path"]?.jsonPrimitive?.content ?: ""

        val dir = if (relPath.isBlank()) context.workspaceRoot else File(context.workspaceRoot, relPath)
        if (!dir.canonicalPath.startsWith(context.workspaceRoot.canonicalPath)) {
            throw SecurityException("Path escapes workspace boundary")
        }
        if (!dir.exists()) {
            return "Error: Directory '$relPath' does not exist."
        }

        val regex = globToRegex(pattern)
        val matchedFiles = mutableListOf<String>()

        dir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .take(500)
            .forEach { file ->
                val rel = file.relativeTo(context.workspaceRoot).path.replace('\\', '/')
                if (regex.matches(rel) || regex.matches(file.name)) {
                    matchedFiles.add(rel)
                }
            }

        return if (matchedFiles.isEmpty()) {
            "No files matched pattern '$pattern'."
        } else {
            matchedFiles.take(100).joinToString("\n")
        }
    }

    private fun globToRegex(glob: String): Regex {
        val pattern = buildString {
            var i = 0
            while (i < glob.length) {
                when (val c = glob[i]) {
                    '*' -> {
                        if (i + 1 < glob.length && glob[i + 1] == '*') {
                            append(".*")
                            i++
                        } else {
                            append("[^/]*")
                        }
                    }
                    '?' -> append("[^/]")
                    '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']' -> {
                        append("\\").append(c)
                    }
                    else -> append(c)
                }
                i++
            }
        }
        return Regex(pattern, RegexOption.IGNORE_CASE)
    }
}

class GitStatusTool : Tool {
    override val name: String = "git_status"
    override val description: String = "Check working tree status (git status --short)."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """{ "type": "object", "properties": {} }"""

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        val cmd = if (isWindows) listOf("cmd.exe", "/c", "git status --short") else listOf("/bin/sh", "-c", "git status --short")
        val res = context.execBackend.exec(cmd, cwd = context.workspaceRoot.absolutePath)
        return if (res.stdout.isBlank()) "(working tree clean)" else res.stdout.trim()
    }
}

class GitDiffTool : Tool {
    override val name: String = "git_diff"
    override val description: String = "View git diff of unstaged or staged changes."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "path": { "type": "string", "description": "Optional file path to diff" },
            "staged": { "type": "boolean", "description": "Diff staged changes (--cached)" }
          }
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = try { Json.parseToJsonElement(argsJson).jsonObject } catch (_: Throwable) { null }
        val path = root?.get("path")?.jsonPrimitive?.content ?: ""
        val staged = root?.get("staged")?.jsonPrimitive?.booleanOrNull ?: false

        val diffArgs = buildString {
            append("git diff")
            if (staged) append(" --cached")
            if (path.isNotBlank()) append(" -- \"$path\"")
        }

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        val cmd = if (isWindows) listOf("cmd.exe", "/c", diffArgs) else listOf("/bin/sh", "-c", diffArgs)
        val res = context.execBackend.exec(cmd, cwd = context.workspaceRoot.absolutePath)
        return if (res.stdout.isBlank()) "(no changes)" else res.stdout.trim()
    }
}

class GitCommitTool : Tool {
    override val name: String = "git_commit"
    override val description: String = "Stage and commit changes to the workspace repository."
    override val isReadOnly: Boolean = false
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "message": { "type": "string", "description": "Commit message" },
            "add_all": { "type": "boolean", "description": "Stage all modified and untracked files" }
          },
          "required": ["message"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val message = root["message"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'message'")
        val addAll = root["add_all"]?.jsonPrimitive?.booleanOrNull ?: true

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        val safeMessage = message.replace("\"", "\\\"")
        val commitCmd = if (addAll) {
            "git add -A && git commit -m \"$safeMessage\""
        } else {
            "git commit -m \"$safeMessage\""
        }

        val cmd = if (isWindows) listOf("cmd.exe", "/c", commitCmd) else listOf("/bin/sh", "-c", commitCmd)
        val res = context.execBackend.exec(cmd, cwd = context.workspaceRoot.absolutePath)
        return buildString {
            if (res.exitCode == 0) appendLine("Commit successful:")
            if (res.stdout.isNotBlank()) appendLine(res.stdout.trim())
            if (res.stderr.isNotBlank()) appendLine(res.stderr.trim())
        }.trim()
    }
}

class GitLogTool : Tool {
    override val name: String = "git_log"
    override val description: String = "View recent git commit history."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "limit": { "type": "integer", "description": "Number of commits to show (default: 10)" }
          }
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = try { Json.parseToJsonElement(argsJson).jsonObject } catch (_: Throwable) { null }
        val limit = root?.get("limit")?.jsonPrimitive?.intOrNull ?: 10

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        val cmd = if (isWindows) listOf("cmd.exe", "/c", "git log -n $limit --oneline") else listOf("/bin/sh", "-c", "git log -n $limit --oneline")
        val res = context.execBackend.exec(cmd, cwd = context.workspaceRoot.absolutePath)
        return if (res.stdout.isBlank()) "(no commits)" else res.stdout.trim()
    }
}

class GitBranchTool : Tool {
    override val name: String = "git_branch"
    override val description: String = "List branches or create a new branch."
    override val isReadOnly: Boolean = true
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "name": { "type": "string", "description": "Optional new branch name to create" }
          }
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = try { Json.parseToJsonElement(argsJson).jsonObject } catch (_: Throwable) { null }
        val branchName = root?.get("name")?.jsonPrimitive?.content

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        val gitCmd = if (branchName.isNullOrBlank()) "git branch -a" else "git checkout -b \"$branchName\""
        val cmd = if (isWindows) listOf("cmd.exe", "/c", gitCmd) else listOf("/bin/sh", "-c", gitCmd)
        val res = context.execBackend.exec(cmd, cwd = context.workspaceRoot.absolutePath)
        return (res.stdout + "\n" + res.stderr).trim()
    }
}

class ArtifactWriteTool : Tool {
    override val name: String = "artifact_write"
    override val description: String = "Write an agent artifact (plan.md, tasks.md, walkthrough.md) for the active session."
    override val isReadOnly: Boolean = false
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "name": { "type": "string", "description": "Artifact filename, e.g. plan.md, tasks.md, walkthrough.md" },
            "content": { "type": "string", "description": "Markdown content of the artifact" }
          },
          "required": ["name", "content"]
        }
    """.trimIndent()

    override suspend fun execute(argsJson: String, context: ToolContext): String {
        val root = Json.parseToJsonElement(argsJson).jsonObject
        val name = root["name"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'name'")
        val content = root["content"]?.jsonPrimitive?.content ?: ""

        val safeName = File(name).name // avoid directory traversal in artifact name
        val sessionArtifactsDir = File(context.workspaceRoot, ".agent/artifacts/${context.sessionId}")
        sessionArtifactsDir.mkdirs()

        val artifactFile = File(sessionArtifactsDir, safeName)
        artifactFile.writeText(content)
        return "Artifact '$safeName' successfully saved (${content.length} characters)."
    }
}


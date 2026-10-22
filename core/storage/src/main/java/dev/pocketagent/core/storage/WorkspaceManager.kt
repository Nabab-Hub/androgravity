package dev.pocketagent.core.storage

import android.content.Context
import dev.pocketagent.core.model.FileNode
import dev.pocketagent.core.model.Project
import dev.pocketagent.core.model.SearchMatch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class WorkspaceManager(private val context: Context) {

    val workspacesRoot: File by lazy {
        File(context.filesDir, "workspaces").apply {
            if (!exists()) mkdirs()
        }
    }

    fun listProjects(): List<Project> {
        val dirs = workspacesRoot.listFiles { file -> file.isDirectory } ?: emptyArray()
        return dirs.map { dir ->
            Project(
                id = dir.name,
                name = dir.name,
                path = dir.absolutePath,
                createdAt = dir.lastModified()
            )
        }.sortedByDescending { it.createdAt }
    }

    fun createProject(name: String): Project {
        val sanitized = name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "project_${UUID.randomUUID().toString().take(8)}" }
        var targetDir = File(workspacesRoot, sanitized)
        if (targetDir.exists()) {
            targetDir = File(workspacesRoot, "${sanitized}_${System.currentTimeMillis() % 10000}")
        }
        targetDir.mkdirs()

        // Create a default README.md
        val readme = File(targetDir, "README.md")
        readme.writeText("# ${targetDir.name}\n\nCreated with PocketAgent.")

        return Project(
            id = targetDir.name,
            name = targetDir.name,
            path = targetDir.absolutePath,
            createdAt = targetDir.lastModified()
        )
    }

    fun createProjectFromTemplate(name: String, template: ProjectTemplate = ProjectTemplate.EMPTY): Project {
        val sanitized = name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "project_${UUID.randomUUID().toString().take(8)}" }
        var targetDir = File(workspacesRoot, sanitized)
        if (targetDir.exists()) {
            targetDir = File(workspacesRoot, "${sanitized}_${System.currentTimeMillis() % 10000}")
        }
        targetDir.mkdirs()

        val templateManager = TemplateManager()
        templateManager.applyTemplate(targetDir, template)

        return Project(
            id = targetDir.name,
            name = targetDir.name,
            path = targetDir.absolutePath,
            createdAt = targetDir.lastModified()
        )
    }

    fun deleteProject(projectId: String): Boolean {
        val dir = getProjectDir(projectId)
        return dir.deleteRecursively()
    }

    fun getProjectDir(projectId: String): File {
        val dir = File(workspacesRoot, projectId)
        validatePath(dir)
        return dir
    }

    fun getFileTree(projectId: String): FileNode {
        val root = getProjectDir(projectId)
        return buildNode(root, root)
    }

    private fun buildNode(file: File, projectRoot: File): FileNode {
        val relative = file.relativeTo(projectRoot).path.replace('\\', '/')
        if (file.isDirectory) {
            val children = (file.listFiles() ?: emptyArray())
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map { buildNode(it, projectRoot) }
            return FileNode(
                name = file.name,
                relativePath = relative,
                isDirectory = true,
                children = children,
                lastModified = file.lastModified()
            )
        } else {
            return FileNode(
                name = file.name,
                relativePath = relative,
                isDirectory = false,
                sizeBytes = file.length(),
                lastModified = file.lastModified()
            )
        }
    }

    fun readFile(projectId: String, relativePath: String): String {
        val file = resolveFile(projectId, relativePath)
        if (!file.exists() || file.isDirectory) {
            throw IllegalArgumentException("File does not exist or is a directory: $relativePath")
        }
        return file.readText(Charsets.UTF_8)
    }

    fun writeFile(projectId: String, relativePath: String, content: String) {
        val file = resolveFile(projectId, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }

    fun createFile(projectId: String, relativePath: String): Boolean {
        val file = resolveFile(projectId, relativePath)
        if (file.exists()) return false
        file.parentFile?.mkdirs()
        return file.createNewFile()
    }

    fun createFolder(projectId: String, relativePath: String): Boolean {
        val dir = resolveFile(projectId, relativePath)
        return dir.mkdirs()
    }

    fun rename(projectId: String, oldRelative: String, newRelative: String): Boolean {
        val src = resolveFile(projectId, oldRelative)
        val dst = resolveFile(projectId, newRelative)
        if (!src.exists() || dst.exists()) return false
        dst.parentFile?.mkdirs()
        return src.renameTo(dst)
    }

    fun delete(projectId: String, relativePath: String): Boolean {
        val file = resolveFile(projectId, relativePath)
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun search(
        projectId: String,
        query: String,
        isRegex: Boolean = false,
        caseSensitive: Boolean = false
    ): List<SearchMatch> {
        val root = getProjectDir(projectId)
        val results = mutableListOf<SearchMatch>()
        if (query.isBlank()) return results

        val pattern = if (isRegex) {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            try {
                Regex(query, options)
            } catch (e: Exception) {
                return results
            }
        } else null

        root.walkTopDown().forEach { file ->
            if (file.isFile && !file.name.startsWith(".")) {
                try {
                    val relative = file.relativeTo(root).path.replace('\\', '/')
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val matches = if (pattern != null) {
                                pattern.containsMatchIn(line)
                            } else {
                                line.contains(query, ignoreCase = !caseSensitive)
                            }
                            if (matches) {
                                results.add(
                                    SearchMatch(
                                        relativePath = relative,
                                        lineNumber = index + 1,
                                        lineContent = line.trim()
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip binary or unreadable files
                }
            }
        }
        return results
    }

    fun exportToZip(projectId: String, outputStream: OutputStream) {
        val root = getProjectDir(projectId)
        ZipOutputStream(outputStream).use { zos ->
            root.walkTopDown().forEach { file ->
                val relative = file.relativeTo(root).path.replace('\\', '/')
                if (relative.isNotEmpty()) {
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry("$relative/"))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(relative))
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    fun importFromZip(projectId: String, inputStream: InputStream) {
        val root = getProjectDir(projectId)
        root.mkdirs()

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val targetFile = File(root, entry.name)
                validatePath(targetFile, root)

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun resolveFile(projectId: String, relativePath: String): File {
        val projectDir = getProjectDir(projectId)
        val file = File(projectDir, relativePath)
        validatePath(file, projectDir)
        return file
    }

    private fun validatePath(file: File, parent: File = workspacesRoot) {
        val canonicalParent = parent.canonicalPath
        val canonicalFile = file.canonicalPath
        if (!canonicalFile.startsWith(canonicalParent)) {
            throw SecurityException("Sandbox violation: Path $canonicalFile escapes allowed root $canonicalParent")
        }
    }
}

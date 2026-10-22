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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class WorkspaceManager(private val context: Context) {

    companion object {
        private val IGNORED_DIR_NAMES = setOf(
            "node_modules", ".git", ".gradle", "build", ".idea", ".dart_tool", "dist", ".cache", "venv", ".venv", "__pycache__"
        )
    }

    val workspacesRoot: File by lazy {
        File(context.filesDir, "workspaces").apply {
            if (!exists()) mkdirs()
        }
    }

    private val externalProjectsFile by lazy { File(context.filesDir, "external_projects.json") }

    private fun loadExternalPaths(): Set<String> {
        return try {
            if (externalProjectsFile.exists()) {
                val list = Json.decodeFromString<List<String>>(externalProjectsFile.readText())
                list.filter { File(it).isDirectory }.toSet()
            } else emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun saveExternalPaths(paths: Set<String>) {
        try {
            externalProjectsFile.writeText(Json.encodeToString(paths.toList()))
        } catch (_: Throwable) {}
    }

    fun listProjects(): List<Project> {
        val internalDirs = (workspacesRoot.listFiles { file -> file.isDirectory } ?: emptyArray()).map { dir ->
            Project(
                id = dir.name,
                name = dir.name,
                path = dir.absolutePath,
                createdAt = dir.lastModified()
            )
        }
        val externalDirs = loadExternalPaths().mapNotNull { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                Project(
                    id = dir.absolutePath,
                    name = dir.name,
                    path = dir.absolutePath,
                    createdAt = dir.lastModified()
                )
            } else null
        }
        return (internalDirs + externalDirs).sortedByDescending { it.createdAt }
    }

    fun registerExternalProject(directory: File): Project {
        val canonicalDir = directory.canonicalFile
        if (!canonicalDir.exists()) canonicalDir.mkdirs()
        val current = loadExternalPaths().toMutableSet()
        current.add(canonicalDir.absolutePath)
        saveExternalPaths(current)

        val readme = File(canonicalDir, "README.md")
        if (!readme.exists()) {
            readme.writeText("# ${canonicalDir.name}\n\nCreated with Maza Agent.")
        }

        return Project(
            id = canonicalDir.absolutePath,
            name = canonicalDir.name,
            path = canonicalDir.absolutePath,
            createdAt = canonicalDir.lastModified()
        )
    }

    fun createProject(name: String, parentDir: File? = null): Project {
        val sanitized = name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "project_${UUID.randomUUID().toString().take(8)}" }
        val root = parentDir ?: workspacesRoot
        var targetDir = File(root, sanitized)
        if (targetDir.exists()) {
            targetDir = File(root, "${sanitized}_${System.currentTimeMillis() % 10000}")
        }
        targetDir.mkdirs()

        // Create a default README.md
        val readme = File(targetDir, "README.md")
        readme.writeText("# ${targetDir.name}\n\nCreated with Maza Agent.")

        if (parentDir != null && parentDir != workspacesRoot) {
            val current = loadExternalPaths().toMutableSet()
            current.add(targetDir.canonicalPath)
            saveExternalPaths(current)
        }

        return Project(
            id = if (parentDir != null && parentDir != workspacesRoot) targetDir.absolutePath else targetDir.name,
            name = targetDir.name,
            path = targetDir.absolutePath,
            createdAt = targetDir.lastModified()
        )
    }

    fun createProjectFromTemplate(name: String, template: ProjectTemplate = ProjectTemplate.EMPTY, parentDir: File? = null): Project {
        val sanitized = name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "project_${UUID.randomUUID().toString().take(8)}" }
        val root = parentDir ?: workspacesRoot
        var targetDir = File(root, sanitized)
        if (targetDir.exists()) {
            targetDir = File(root, "${sanitized}_${System.currentTimeMillis() % 10000}")
        }
        targetDir.mkdirs()

        val templateManager = TemplateManager()
        templateManager.applyTemplate(targetDir, template)

        if (parentDir != null && parentDir != workspacesRoot) {
            val current = loadExternalPaths().toMutableSet()
            current.add(targetDir.canonicalPath)
            saveExternalPaths(current)
        }

        return Project(
            id = if (parentDir != null && parentDir != workspacesRoot) targetDir.absolutePath else targetDir.name,
            name = targetDir.name,
            path = targetDir.absolutePath,
            createdAt = targetDir.lastModified()
        )
    }

    fun deleteProject(projectId: String): Boolean {
        val dir = getProjectDir(projectId)
        val external = loadExternalPaths()
        if (external.contains(dir.canonicalPath) || external.contains(dir.absolutePath)) {
            val current = external.toMutableSet()
            current.remove(dir.canonicalPath)
            current.remove(dir.absolutePath)
            saveExternalPaths(current)
            return dir.deleteRecursively()
        }
        return dir.deleteRecursively()
    }

    fun getProjectDir(projectId: String): File {
        val direct = File(projectId)
        if (direct.isAbsolute && direct.exists() && direct.isDirectory) {
            return direct
        }
        val registered = loadExternalPaths().firstOrNull { it == projectId || File(it).name == projectId }
        if (registered != null) {
            val dir = File(registered)
            if (dir.exists()) return dir
        }
        val dir = File(workspacesRoot, projectId)
        validatePath(dir)
        return dir
    }

    fun getFileTree(projectId: String): FileNode {
        val root = getProjectDir(projectId)
        return buildNode(root, root, depth = 0)
    }

    private fun buildNode(file: File, projectRoot: File, depth: Int = 0): FileNode {
        val relative = file.relativeTo(projectRoot).path.replace('\\', '/')
        if (file.isDirectory) {
            val isIgnored = file != projectRoot && (file.name in IGNORED_DIR_NAMES || file.name.startsWith("."))
            val children = if (isIgnored || depth >= 5) {
                emptyList()
            } else {
                (file.listFiles() ?: emptyArray())
                    .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    .take(200)
                    .map { buildNode(it, projectRoot, depth + 1) }
            }
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

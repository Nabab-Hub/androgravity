package dev.pocketagent.core.storage

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class WorkspaceManagerTest {

    private lateinit var tempDir: File
    private lateinit var manager: WorkspaceManager

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("workspace_test").toFile()
        // Mock a Context with tempDir as filesDir
        val fakeContext = object : FakeContext(tempDir) {}
        manager = WorkspaceManager(fakeContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun createAndListProjects() {
        val p1 = manager.createProject("todo_app")
        val p2 = manager.createProject("api_service")

        val projects = manager.listProjects()
        assertThat(projects.map { it.name }).containsAtLeast("todo_app", "api_service")
    }

    @Test
    fun fileCrudOperations() {
        val project = manager.createProject("test_project")
        manager.createFolder(project.id, "src/main")
        manager.createFile(project.id, "src/main/App.kt")
        manager.writeFile(project.id, "src/main/App.kt", "fun main() = println(\"Hello\")")

        val content = manager.readFile(project.id, "src/main/App.kt")
        assertThat(content).isEqualTo("fun main() = println(\"Hello\")")

        // Rename
        manager.rename(project.id, "src/main/App.kt", "src/main/Main.kt")
        assertThat(manager.readFile(project.id, "src/main/Main.kt")).isEqualTo("fun main() = println(\"Hello\")")

        // Delete
        manager.delete(project.id, "src/main/Main.kt")
        val tree = manager.getFileTree(project.id)
        val srcNode = tree.children.find { it.name == "src" }
        val mainNode = srcNode?.children?.find { it.name == "main" }
        assertThat(mainNode?.children).isEmpty()
    }

    @Test(expected = SecurityException::class)
    fun blocksPathTraversalAttacks() {
        val project = manager.createProject("safe_project")
        manager.readFile(project.id, "../../protected.txt")
    }

    @Test
    fun recursiveSearch() {
        val project = manager.createProject("search_project")
        manager.writeFile(project.id, "index.js", "const port = 3000;\nconsole.log(port);")
        manager.writeFile(project.id, "src/config.js", "export const PORT = 3000;")

        val results = manager.search(project.id, "3000")
        assertThat(results).hasSize(2)
        assertThat(results.map { it.relativePath }).containsExactly("index.js", "src/config.js")
    }

    @Test
    fun zipExportAndImport() {
        val project = manager.createProject("zip_project")
        manager.writeFile(project.id, "file1.txt", "content one")
        manager.writeFile(project.id, "folder/file2.txt", "content two")

        val baos = ByteArrayOutputStream()
        manager.exportToZip(project.id, baos)
        val zipBytes = baos.toByteArray()
        assertThat(zipBytes.size).isGreaterThan(0)

        // Import into new project
        val importedProject = manager.createProject("imported_project")
        manager.importFromZip(importedProject.id, ByteArrayInputStream(zipBytes))

        assertThat(manager.readFile(importedProject.id, "file1.txt")).isEqualTo("content one")
        assertThat(manager.readFile(importedProject.id, "folder/file2.txt")).isEqualTo("content two")
    }
}

open class FakeContext(private val testFilesDir: File) : android.content.ContextWrapper(null) {
    override fun getFilesDir(): File = testFilesDir
}

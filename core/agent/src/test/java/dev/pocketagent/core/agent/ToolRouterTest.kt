package dev.pocketagent.core.agent

import com.google.common.truth.Truth.assertThat
import dev.pocketagent.core.agent.tools.ToolContext
import dev.pocketagent.core.agent.tools.ToolRouter
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.core.sandbox.DirectBackend
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ToolRouterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFilesystemAndSearchToolsEndToEnd() = runBlocking {
        val workspace = tempFolder.newFolder("workspace")
        val router = ToolRouter()
        val context = ToolContext(
            projectId = "test-project",
            workspaceRoot = workspace,
            execBackend = DirectBackend(),
            sessionId = "session-123"
        )

        // 1. Write file
        val writeCall = ToolCall(
            id = "call-1",
            name = "fs_write_file",
            argsJson = """{"path": "app.js", "content": "console.log('Hello World');\nfunction test() { return 42; }"}"""
        )
        val writeRes = router.execute(writeCall, context)
        assertThat(writeRes.ok).isTrue()
        assertThat(File(workspace, "app.js").exists()).isTrue()

        // 2. Read file
        val readCall = ToolCall(
            id = "call-2",
            name = "fs_read_file",
            argsJson = """{"path": "app.js", "start_line": 1, "end_line": 2}"""
        )
        val readRes = router.execute(readCall, context)
        assertThat(readRes.ok).isTrue()
        assertThat(readRes.output).contains("1: console.log('Hello World');")

        // 3. Edit file
        val editCall = ToolCall(
            id = "call-3",
            name = "fs_edit_file",
            argsJson = """{"path": "app.js", "target_content": "42", "replacement_content": "100"}"""
        )
        val editRes = router.execute(editCall, context)
        assertThat(editRes.ok).isTrue()
        assertThat(File(workspace, "app.js").readText()).contains("return 100;")

        // 4. Search grep
        val grepCall = ToolCall(
            id = "call-4",
            name = "search_grep",
            argsJson = """{"query": "return 100"}"""
        )
        val grepRes = router.execute(grepCall, context)
        assertThat(grepRes.ok).isTrue()
        assertThat(grepRes.output).contains("app.js:2: function test() { return 100; }")

        // 5. Search glob
        val globCall = ToolCall(
            id = "call-glob",
            name = "search_glob",
            argsJson = """{"pattern": "*.js"}"""
        )
        val globRes = router.execute(globCall, context)
        assertThat(globRes.ok).isTrue()
        assertThat(globRes.output).contains("app.js")

        // 6. Artifact write
        val artifactCall = ToolCall(
            id = "call-art",
            name = "artifact_write",
            argsJson = """{"name": "plan.md", "content": "# Implementation Plan\n- Step 1: Done"}"""
        )
        val artRes = router.execute(artifactCall, context)
        assertThat(artRes.ok).isTrue()
        val planFile = File(workspace, ".agent/artifacts/session-123/plan.md")
        assertThat(planFile.exists()).isTrue()
        assertThat(planFile.readText()).contains("# Implementation Plan")

        // 7. Delete file
        val delCall = ToolCall(
            id = "call-del",
            name = "fs_delete_file",
            argsJson = """{"path": "app.js"}"""
        )
        val delRes = router.execute(delCall, context)
        assertThat(delRes.ok).isTrue()
        assertThat(File(workspace, "app.js").exists()).isFalse()
    }

    @Test
    fun testAnsiStrippingAndOutputTruncation() {
        val router = ToolRouter()

        val coloredText = "\u001B[31mError:\u001B[0m something went wrong \u001B[32m[OK]\u001B[0m"
        val stripped = router.stripAnsi(coloredText)
        assertThat(stripped).isEqualTo("Error: something went wrong [OK]")

        val hugeOutput = "A".repeat(50_000)
        val truncated = router.truncateOutput(hugeOutput, maxBytes = 1000)
        assertThat(truncated.length).isLessThan(2000)
        assertThat(truncated).contains("Truncated")
        assertThat(truncated.startsWith("A".repeat(500))).isTrue()
        assertThat(truncated.endsWith("A".repeat(500))).isTrue()
    }
}

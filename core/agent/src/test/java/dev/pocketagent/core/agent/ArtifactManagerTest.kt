package dev.pocketagent.core.agent

import com.google.common.truth.Truth.assertThat
import dev.pocketagent.core.agent.artifacts.ArtifactComment
import dev.pocketagent.core.agent.artifacts.ArtifactManager
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtifactManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPlanAndWalkthroughArtifacts() {
        val workspace = tempFolder.newFolder("ws_artifacts")
        val manager = ArtifactManager()
        val sessionId = "session-plan-1"

        // 1. Plan
        assertThat(manager.getPlan(workspace, sessionId)).isNull()
        manager.savePlan(workspace, sessionId, "# System Plan\n1. Setup server\n2. Add database")
        assertThat(manager.getPlan(workspace, sessionId)).contains("# System Plan")

        // 2. Walkthrough
        assertThat(manager.getWalkthrough(workspace, sessionId)).isNull()
        manager.saveWalkthrough(workspace, sessionId, "# Changes Walkthrough\n- Updated index.html with new styling")
        assertThat(manager.getWalkthrough(workspace, sessionId)).contains("Updated index.html")
    }

    @Test
    fun testTasksChecklistParsingAndToggling() {
        val workspace = tempFolder.newFolder("ws_tasks")
        val manager = ArtifactManager()
        val sessionId = "session-tasks-1"

        val initialTasks = """
            # Project Checklist
            - [ ] Task 1: Create model
            - [x] Task 2: Write tests
            - [ ] Task 3: Build UI
        """.trimIndent()

        manager.saveTasks(workspace, sessionId, initialTasks)

        val parsed = manager.parseTasks(workspace, sessionId)
        assertThat(parsed.size).isEqualTo(3)
        assertThat(parsed[0].text).isEqualTo("Task 1: Create model")
        assertThat(parsed[0].isCompleted).isFalse()
        assertThat(parsed[1].text).isEqualTo("Task 2: Write tests")
        assertThat(parsed[1].isCompleted).isTrue()
        assertThat(parsed[2].text).isEqualTo("Task 3: Build UI")
        assertThat(parsed[2].isCompleted).isFalse()

        // Toggle Task 1 from unchecked to checked
        val ok1 = manager.toggleTask(workspace, sessionId, 0)
        assertThat(ok1).isTrue()

        val afterToggle1 = manager.parseTasks(workspace, sessionId)
        assertThat(afterToggle1[0].isCompleted).isTrue()

        // Toggle Task 1 back to unchecked
        val ok2 = manager.toggleTask(workspace, sessionId, 0)
        assertThat(ok2).isTrue()

        val afterToggle2 = manager.parseTasks(workspace, sessionId)
        assertThat(afterToggle2[0].isCompleted).isFalse()
    }

    @Test
    fun testArtifactCommentsPersistenceAndContextFormatting() {
        val workspace = tempFolder.newFolder("ws_comments")
        val manager = ArtifactManager()
        val sessionId = "session-comments-1"

        assertThat(manager.getComments(workspace, sessionId)).isEmpty()

        val comment1 = ArtifactComment(
            artifactName = "plan.md",
            lineNumber = 14,
            sectionHeader = "Architecture",
            commentText = "Please use Ktor instead of Retrofit."
        )
        val comment2 = ArtifactComment(
            artifactName = "tasks.md",
            commentText = "Add unit tests for error handling."
        )

        manager.addComment(workspace, sessionId, comment1)
        manager.addComment(workspace, sessionId, comment2)

        val comments = manager.getComments(workspace, sessionId)
        assertThat(comments.size).isEqualTo(2)
        assertThat(comments[0].commentText).isEqualTo("Please use Ktor instead of Retrofit.")

        // Verify formatted context for agent
        val formatted = manager.formatCommentsForAgent(workspace, sessionId)
        assertThat(formatted).contains("=== User Review Comments ===")
        assertThat(formatted).contains("Please use Ktor instead of Retrofit.")
        assertThat(formatted).contains("Add unit tests for error handling.")
        assertThat(formatted).contains("Line 14")

        // Delete comment
        val deleted = manager.deleteComment(workspace, sessionId, comment1.id)
        assertThat(deleted).isTrue()
        assertThat(manager.getComments(workspace, sessionId).size).isEqualTo(1)
    }
}

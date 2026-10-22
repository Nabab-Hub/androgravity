package dev.pocketagent.core.agent

import com.google.common.truth.Truth.assertThat
import dev.pocketagent.core.agent.checkpoint.CheckpointManager
import dev.pocketagent.core.sandbox.DirectBackend
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

import kotlinx.coroutines.runBlocking

class CheckpointManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testGitRepoInitAndCheckpoints() = runBlocking {
        val workspace = tempFolder.newFolder("ws_checkpoint")
        val backend = DirectBackend()
        val manager = CheckpointManager(backend)

        // 1. Ensure git repo initializes
        val created = manager.ensureGitRepo(workspace)
        assertThat(created).isTrue()
        assertThat(File(workspace, ".git").exists()).isTrue()

        // 2. Make a file modification
        val testFile = File(workspace, "index.js")
        testFile.writeText("console.log('v1');")

        val changedBefore = manager.getChangedFiles(workspace)
        assertThat(changedBefore).isNotEmpty()
        assertThat(changedBefore.any { it.path.contains("index.js") }).isTrue()

        // 3. Create checkpoint
        val hash = manager.createCheckpoint(workspace, "session-test", step = 1, message = "Created index.js")
        assertThat(hash).isNotEmpty()

        val changedAfter = manager.getChangedFiles(workspace)
        assertThat(changedAfter).isEmpty()

        // 4. Modify file again and checkpoint
        testFile.writeText("console.log('v2');")
        val hash2 = manager.createCheckpoint(workspace, "session-test", step = 2, message = "Updated to v2")
        assertThat(hash2).isNotEqualTo(hash)

        // 5. Test undoLastStep reverts to v1
        val undoSuccess = manager.undoLastStep(workspace)
        assertThat(undoSuccess).isTrue()
        assertThat(testFile.readText()).isEqualTo("console.log('v1');")
    }
}

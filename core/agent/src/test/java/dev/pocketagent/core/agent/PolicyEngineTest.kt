package dev.pocketagent.core.agent

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.pocketagent.core.agent.policy.AutonomyMode
import dev.pocketagent.core.agent.policy.PolicyDecision
import dev.pocketagent.core.agent.policy.PolicyEngine
import dev.pocketagent.core.model.ToolCall
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PolicyEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testHardcodedDenyListBlocksDangerousCommands() {
        val policy = PolicyEngine(AutonomyMode.FULL_AUTONOMY)
        val workspace = tempFolder.newFolder("ws")

        // 40 distinct dangerous commands testing safety boundaries
        val dangerousCommands = listOf(
            // 1. Root / recursive wipe
            "rm -rf /",
            "rm -rf /*",
            "rm -r /",
            "rm -rf /data",
            "rm -rf /data/local",
            "rm -rf ../outside",
            "rm -r ../parent",
            "rm -rf *",
            // 2. Filesystem formatting & partition tools
            "mkfs /dev/sda",
            "mkfs.ext4 /dev/sdb1",
            "mkfs.vfat /dev/mmcblk0p1",
            "fdisk /dev/nvme0n1",
            "sfdisk /dev/sda",
            "parted /dev/sda mklabel gpt",
            // 3. Raw device access
            "dd if=/dev/zero of=/dev/block/bootdevice",
            "dd if=/dev/urandom of=/dev/sda",
            "dd of=/dev/null if=/dev/mem",
            "cat /dev/block/mmcblk0",
            "cat /dev/kmem",
            "cat /dev/port",
            // 4. Fork bombs
            ":(){ :|:& };:",
            ":(){ :|:&};:",
            // 5. System power & reboot
            "reboot",
            "poweroff",
            "halt",
            "shutdown -h now",
            "init 0",
            "init 6",
            // 6. System binary tampering
            "/system/bin/sh",
            "/vendor/bin/hw/service",
            "/product/bin/app",
            // 7. Privilege escalation
            "su",
            "su -",
            "sudo rm -f test.txt",
            // 8. Destructive git
            "git push origin main --force",
            "git push -f",
            "git clean -fdx",
            // 9. Credential exfiltration
            "cat ~/.ssh/id_rsa",
            "cat ~/.ssh/id_ed25519",
            "cat ~/.aws/credentials",
            "cp /etc/shadow ./shadow.txt",
            "cp release/keystore.jks /tmp",
            "cat ~/.android/debug.keystore"
        )

        for (cmd in dangerousCommands) {
            val call = ToolCall(id = "1", name = "terminal_run", argsJson = """{"command": "$cmd"}""")
            val decision = policy.evaluate(call, workspace, commandText = cmd)
            assertWithMessage("Expected command to be denied: $cmd")
                .that(decision)
                .isInstanceOf(PolicyDecision.Deny::class.java)
        }

        assertThat(dangerousCommands.size).isAtLeast(30)
    }

    @Test
    fun testSafeCommandsAreAllowedUnderFullAutonomy() {
        val policy = PolicyEngine(AutonomyMode.FULL_AUTONOMY)
        val workspace = tempFolder.newFolder("ws")

        val safeCommands = listOf(
            "ls -la",
            "git status",
            "git diff",
            "npm run build",
            "npm install",
            "node index.js",
            "python3 main.py",
            "cargo build",
            "./gradlew test",
            "cat src/main/App.kt"
        )

        for (cmd in safeCommands) {
            val call = ToolCall(id = "s1", name = "terminal_run", argsJson = """{"command": "$cmd"}""")
            val decision = policy.evaluate(call, workspace, commandText = cmd)
            assertWithMessage("Expected safe command to be allowed: $cmd")
                .that(decision)
                .isEqualTo(PolicyDecision.Allow)
        }
    }

    @Test
    fun testPathTraversalIsDenied() {
        val policy = PolicyEngine(AutonomyMode.FULL_AUTONOMY)
        val workspace = tempFolder.newFolder("ws")

        val escapePaths = listOf(
            "../../../etc/shadow",
            "../../secret.txt",
            "/etc/passwd",
            "subdir/../../../../outside"
        )

        for (path in escapePaths) {
            val call = ToolCall(id = "pt", name = "fs_read_file", argsJson = """{"path": "$path"}""")
            val decision = policy.evaluate(call, workspace)
            assertWithMessage("Path traversal should be denied: $path")
                .that(decision)
                .isInstanceOf(PolicyDecision.Deny::class.java)
        }
    }

    @Test
    fun testSensitivePathInWorkspaceIsDenied() {
        val policy = PolicyEngine(AutonomyMode.FULL_AUTONOMY)
        val workspace = tempFolder.newFolder("ws")

        val sensitiveFiles = listOf(
            "id_rsa",
            "keystore.jks",
            ".aws/credentials"
        )

        for (target in sensitiveFiles) {
            val call = ToolCall(id = "sens", name = "fs_read_file", argsJson = """{"path": "$target"}""")
            val decision = policy.evaluate(call, workspace)
            assertWithMessage("Sensitive resource access should be denied: $target")
                .that(decision)
                .isInstanceOf(PolicyDecision.Deny::class.java)
        }
    }

    @Test
    fun testAutonomyModesBehavior() {
        val workspace = tempFolder.newFolder("ws")

        // 1. Semi-Autonomy
        val semiPolicy = PolicyEngine(AutonomyMode.SEMI_AUTONOMY)
        val readCall = ToolCall(id = "r1", name = "fs_read_file", argsJson = """{"path": "index.html"}""")
        val writeCall = ToolCall(id = "w1", name = "fs_write_file", argsJson = """{"path": "index.html", "content": "hello"}""")
        val termCall = ToolCall(id = "t1", name = "terminal_run", argsJson = """{"command": "pytest"}""")

        assertThat(semiPolicy.evaluate(readCall, workspace)).isEqualTo(PolicyDecision.Allow)
        assertThat(semiPolicy.evaluate(writeCall, workspace)).isInstanceOf(PolicyDecision.RequireApproval::class.java)
        assertThat(semiPolicy.evaluate(termCall, workspace)).isInstanceOf(PolicyDecision.RequireApproval::class.java)

        // 2. Full Autonomy
        val fullPolicy = PolicyEngine(AutonomyMode.FULL_AUTONOMY)
        assertThat(fullPolicy.evaluate(writeCall, workspace)).isEqualTo(PolicyDecision.Allow)
        assertThat(fullPolicy.evaluate(termCall, workspace)).isEqualTo(PolicyDecision.Allow)

        // 3. Manual Mode
        val manualPolicy = PolicyEngine(AutonomyMode.MANUAL)
        assertThat(manualPolicy.evaluate(readCall, workspace)).isInstanceOf(PolicyDecision.RequireApproval::class.java)
        assertThat(manualPolicy.evaluate(writeCall, workspace)).isInstanceOf(PolicyDecision.RequireApproval::class.java)
    }

    @Test
    fun testProjectPolicyJsonOverrides() {
        val workspace = tempFolder.newFolder("ws_policy")
        val agentDir = File(workspace, ".agent").apply { mkdirs() }
        val policyJson = File(agentDir, "policy.json")
        policyJson.writeText(
            """
            {
              "allowCommands": ["^npm test", "^gradle check"],
              "denyCommands": ["curl", "wget"]
            }
            """.trimIndent()
        )

        val semiPolicy = PolicyEngine(AutonomyMode.SEMI_AUTONOMY)

        // Allowed by project policy in Semi-Autonomy
        val npmTestCall = ToolCall(id = "p1", name = "terminal_run", argsJson = """{"command": "npm test"}""")
        assertThat(semiPolicy.evaluate(npmTestCall, workspace)).isEqualTo(PolicyDecision.Allow)

        // Denied by project policy
        val curlCall = ToolCall(id = "p2", name = "terminal_run", argsJson = """{"command": "curl http://example.com"}""")
        val curlDecision = semiPolicy.evaluate(curlCall, workspace)
        assertThat(curlDecision).isInstanceOf(PolicyDecision.Deny::class.java)

        // Not allowlisted -> requires approval in Semi-Autonomy
        val otherCall = ToolCall(id = "p3", name = "terminal_run", argsJson = """{"command": "python script.py"}""")
        assertThat(semiPolicy.evaluate(otherCall, workspace)).isInstanceOf(PolicyDecision.RequireApproval::class.java)
    }
}

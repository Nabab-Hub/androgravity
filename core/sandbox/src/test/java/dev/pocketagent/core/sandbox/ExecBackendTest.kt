package dev.pocketagent.core.sandbox

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

class ExecBackendTest {

    @Test
    fun testDirectBackendExecution() = runBlocking {
        val backend = DirectBackend()
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true

        val cmd = if (isWindows) {
            listOf("cmd.exe", "/c", "echo", "PocketAgentTerminalTest")
        } else {
            listOf("sh", "-c", "echo PocketAgentTerminalTest")
        }

        val result = backend.exec(cmd)
        assertThat(result.exitCode).isEqualTo(0)
        assertThat(result.stdout.trim()).contains("PocketAgentTerminalTest")
    }

    @Test
    fun testDirectBackendExitCodeCapture() = runBlocking {
        val backend = DirectBackend()
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true

        val cmd = if (isWindows) {
            listOf("cmd.exe", "/c", "exit 42")
        } else {
            listOf("sh", "-c", "exit 42")
        }

        val result = backend.exec(cmd)
        assertThat(result.exitCode).isEqualTo(42)
    }
}

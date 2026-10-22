package dev.pocketagent.core.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashReportManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private var isCrashReportingEnabled: Boolean = false
    private lateinit var manager: CrashReportManager

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        isCrashReportingEnabled = false
        manager = CrashReportManager(
            filesDir = filesDir,
            isEnabledProvider = { isCrashReportingEnabled }
        )
    }

    @Test
    fun testScrubberRedactsCredentialsAndWorkspacePaths() {
        val rawMessage = "Failed with Gemini key AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6 and Anthropic sk-ant-api03-abcdefghijklmnop12345678"
        val scrubbed = CrashScrubber.scrub(rawMessage)

        assertThat(scrubbed).doesNotContain("AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6")
        assertThat(scrubbed).contains("[REDACTED_GEMINI_KEY]")
        assertThat(scrubbed).doesNotContain("sk-ant-api03-abcdefghijklmnop12345678")
        assertThat(scrubbed).contains("[REDACTED_ANTHROPIC_KEY]")

        val bearerMessage = "Authorization: Bearer secret_session_token_12345"
        val scrubbedBearer = CrashScrubber.scrub(bearerMessage)
        assertThat(scrubbedBearer).doesNotContain("secret_session_token_12345")
        assertThat(scrubbedBearer).contains("Bearer [REDACTED_TOKEN]")

        val pathMessage = "Error opening file at /data/user/0/dev.pocketagent/workspaces/my_project/src/Main.kt"
        val scrubbedPath = CrashScrubber.scrub(pathMessage)
        assertThat(scrubbedPath).doesNotContain("my_project/src/Main.kt")
        assertThat(scrubbedPath).contains("[WORKSPACE_DIR]")
    }

    @Test
    fun testCrashReportManagerObeysOptInSetting() {
        // When disabled (default)
        isCrashReportingEnabled = false
        val reportWhenDisabled = manager.recordException(RuntimeException("Secret key AIzaSy123456789012345678901234567890123"))
        assertThat(reportWhenDisabled).isNull()
        assertThat(manager.getReports()).isEmpty()

        // When enabled
        isCrashReportingEnabled = true
        val reportWhenEnabled = manager.recordException(IllegalArgumentException("Invalid arg with sk-1234567890abcdef12345678"))
        assertThat(reportWhenEnabled).isNotNull()
        assertThat(reportWhenEnabled!!.scrubbedMessage).contains("[REDACTED_OPENAI_KEY]")
        assertThat(reportWhenEnabled.scrubbedMessage).doesNotContain("sk-1234567890abcdef12345678")

        val reports = manager.getReports()
        assertThat(reports).hasSize(1)
        assertThat(reports[0].exceptionClass).isEqualTo("java.lang.IllegalArgumentException")

        // Export text
        val exported = manager.exportReportsAsText()
        assertThat(exported).contains("PocketAgent Scrubbed Diagnostic Logs")
        assertThat(exported).contains("Total Reports: 1")

        // Clear reports
        manager.clearReports()
        assertThat(manager.getReports()).isEmpty()
    }
}

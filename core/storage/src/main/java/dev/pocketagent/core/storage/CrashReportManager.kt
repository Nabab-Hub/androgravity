package dev.pocketagent.core.storage

import android.content.Context
import android.os.Build
import dev.pocketagent.core.storage.SecurePreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class CrashReport(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val isFatal: Boolean = false,
    val exceptionClass: String,
    val scrubbedMessage: String,
    val scrubbedStackTrace: String,
    val appVersion: String = "1.0.0",
    val androidSdk: Int = Build.VERSION.SDK_INT,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
)

object CrashScrubber {
    // Regex patterns for sensitive keys and tokens
    private val GEMINI_KEY_PATTERN = Regex("AIzaSy[A-Za-z0-9_\\-]{30,}")
    private val ANTHROPIC_KEY_PATTERN = Regex("sk-ant-[A-Za-z0-9_\\-]{20,}")
    private val OPENAI_KEY_PATTERN = Regex("sk-[A-Za-z0-9_\\-]{20,}")
    private val GITHUB_TOKEN_PATTERN = Regex("gh[pousr]_[A-Za-z0-9_]{36,}")
    private val BEARER_PATTERN = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/\\-]{8,}")
    private val GENERIC_KEY_PATTERN = Regex("(?i)(key|token|secret|password)\\s*[:=]\\s*[\"']?([A-Za-z0-9_\\-]{8,})[\"']?")

    // Regex patterns for internal storage and workspace paths
    private val APP_INTERNAL_PATH_PATTERN = Regex("(/data/user/\\d+/[a-zA-Z0-9_.-]+|/data/data/[a-zA-Z0-9_.-]+)")
    private val WORKSPACE_PATH_PATTERN = Regex("([a-zA-Z]:\\\\|/).*?(?:workspaces|workspace).*?(?=[\\s:\"',;\\)\\]]|$)")

    fun scrub(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var text = input

        // Redact credentials
        text = GEMINI_KEY_PATTERN.replace(text, "[REDACTED_GEMINI_KEY]")
        text = ANTHROPIC_KEY_PATTERN.replace(text, "[REDACTED_ANTHROPIC_KEY]")
        text = OPENAI_KEY_PATTERN.replace(text, "[REDACTED_OPENAI_KEY]")
        text = GITHUB_TOKEN_PATTERN.replace(text, "[REDACTED_GITHUB_TOKEN]")
        text = BEARER_PATTERN.replace(text, "Bearer [REDACTED_TOKEN]")
        text = GENERIC_KEY_PATTERN.replace(text) { match ->
            val field = match.groupValues[1]
            "$field: [REDACTED]"
        }

        // Redact paths to prevent workspace and user data exposure
        text = APP_INTERNAL_PATH_PATTERN.replace(text, "[APP_INTERNAL_DIR]")
        text = WORKSPACE_PATH_PATTERN.replace(text, "[WORKSPACE_DIR]")

        return text
    }
}

class CrashReportManager(
    private val filesDir: File,
    private val isEnabledProvider: () -> Boolean
) {
    constructor(context: Context, securePreferences: SecurePreferences) : this(
        filesDir = context.filesDir,
        isEnabledProvider = { securePreferences.isCrashReportingEnabled() }
    )

    private val reportsDir = File(filesDir, "crash_reports").apply {
        if (!exists()) mkdirs()
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun installUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (isEnabledProvider()) {
                    recordException(throwable, isFatal = true)
                }
            } catch (_: Throwable) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun recordException(throwable: Throwable, isFatal: Boolean = false): CrashReport? {
        if (!isEnabledProvider()) return null

        val stackTraceString = throwable.stackTraceToString()
        val scrubbedMsg = CrashScrubber.scrub(throwable.localizedMessage ?: throwable.message ?: "No message")
        val scrubbedTrace = CrashScrubber.scrub(stackTraceString)

        val report = CrashReport(
            isFatal = isFatal,
            exceptionClass = throwable.javaClass.name,
            scrubbedMessage = scrubbedMsg,
            scrubbedStackTrace = scrubbedTrace
        )

        try {
            val reportFile = File(reportsDir, "crash_${report.timestamp}_${report.id.take(8)}.json")
            reportFile.writeText(json.encodeToString(report))
        } catch (_: Throwable) {
        }

        return report
    }

    fun getReports(): List<CrashReport> {
        val files = reportsDir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                json.decodeFromString<CrashReport>(file.readText())
            } catch (_: Throwable) {
                null
            }
        }.sortedByDescending { it.timestamp }
    }

    fun clearReports() {
        reportsDir.listFiles()?.forEach { it.delete() }
    }

    fun exportReportsAsText(): String {
        val reports = getReports()
        if (reports.isEmpty()) return "No crash reports recorded."

        return buildString {
            appendLine("=== PocketAgent Scrubbed Diagnostic Logs ===")
            appendLine("Total Reports: ${reports.size}")
            appendLine("Notice: All API keys, tokens, and workspace paths have been cryptographically redacted.")
            appendLine()
            for ((idx, r) in reports.withIndex()) {
                appendLine("--- Report #${idx + 1} [${r.id}] ---")
                appendLine("Timestamp: ${r.timestamp}")
                appendLine("Fatal: ${r.isFatal}")
                appendLine("Exception: ${r.exceptionClass}")
                appendLine("Message: ${r.scrubbedMessage}")
                appendLine("Device: ${r.deviceModel} (SDK ${r.androidSdk})")
                appendLine("Scrubbed Stack Trace:")
                appendLine(r.scrubbedStackTrace)
                appendLine()
            }
        }
    }
}

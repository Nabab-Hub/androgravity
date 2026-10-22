package dev.pocketagent.core.agent

import com.google.common.truth.Truth.assertThat
import dev.pocketagent.core.agent.browser.BrowserController
import dev.pocketagent.core.agent.policy.PolicyDecision
import dev.pocketagent.core.agent.policy.PolicyEngine
import dev.pocketagent.core.agent.tools.BrowserClickTool
import dev.pocketagent.core.agent.tools.BrowserConsoleTool
import dev.pocketagent.core.agent.tools.BrowserEvalTool
import dev.pocketagent.core.agent.tools.BrowserOpenTool
import dev.pocketagent.core.agent.tools.BrowserScreenshotTool
import dev.pocketagent.core.agent.tools.BrowserTypeTool
import dev.pocketagent.core.agent.tools.ToolContext
import dev.pocketagent.core.agent.policy.AutonomyMode
import dev.pocketagent.core.model.BrowserNavigationState
import dev.pocketagent.core.model.ConsoleLogEntry
import dev.pocketagent.core.model.ConsoleLogLevel
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.core.sandbox.DirectBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FakeBrowserController : BrowserController {
    override val navigationState: StateFlow<BrowserNavigationState> = MutableStateFlow(BrowserNavigationState())
    private val _logs = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    override val consoleLogs: StateFlow<List<ConsoleLogEntry>> = _logs

    var lastOpenedUrl: String? = null
    var lastClickedSelector: String? = null
    var lastTypedSelector: String? = null
    var lastTypedText: String? = null
    var lastEvaluatedScript: String? = null

    override suspend fun openUrl(url: String): Boolean {
        lastOpenedUrl = url
        return true
    }

    override suspend fun takeScreenshot(outputFile: File): String {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText("fake png binary data")
        return outputFile.absolutePath
    }

    override suspend fun click(selector: String): Boolean {
        lastClickedSelector = selector
        return selector == "button#submit" || selector.startsWith(".")
    }

    override suspend fun typeText(selector: String, text: String, clearFirst: Boolean): Boolean {
        lastTypedSelector = selector
        lastTypedText = text
        return true
    }

    override suspend fun evaluateJs(script: String): String {
        lastEvaluatedScript = script
        return "window.title = 'Hello World'"
    }

    fun emitConsoleLog(level: ConsoleLogLevel, msg: String) {
        val current = _logs.value.toMutableList()
        current.add(ConsoleLogEntry(level, msg, "index.html", 42))
        _logs.value = current
    }

    override fun getConsoleLogs(): List<ConsoleLogEntry> = _logs.value

    override fun clearConsoleLogs() {
        _logs.value = emptyList()
    }
}

class BrowserToolsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var workspace: File
    private lateinit var fakeBrowser: FakeBrowserController
    private lateinit var context: ToolContext

    @Before
    fun setup() {
        workspace = tempFolder.newFolder("ws_browser")
        fakeBrowser = FakeBrowserController()
        context = ToolContext(
            projectId = "test_p",
            workspaceRoot = workspace,
            execBackend = DirectBackend(),
            sessionId = "session_browser_1",
            browserController = fakeBrowser
        )
    }

    @Test
    fun testBrowserOpenTool() = runBlocking {
        val tool = BrowserOpenTool()
        val res = tool.execute("""{"url": "http://localhost:3000"}""", context)
        assertThat(res).contains("Successfully navigated to http://localhost:3000")
        assertThat(fakeBrowser.lastOpenedUrl).isEqualTo("http://localhost:3000")
    }

    @Test
    fun testBrowserScreenshotToolSavesArtifact() = runBlocking {
        val tool = BrowserScreenshotTool()
        val res = tool.execute("""{"name": "test_app_home"}""", context)
        assertThat(res).contains("Screenshot saved successfully")

        val expectedFile = File(workspace, ".agent/artifacts/session_browser_1/screens/test_app_home.png")
        assertThat(expectedFile.exists()).isTrue()
        assertThat(expectedFile.readText()).isEqualTo("fake png binary data")
    }

    @Test
    fun testBrowserClickAndTypeTools() = runBlocking {
        val clickTool = BrowserClickTool()
        val clickRes = clickTool.execute("""{"selector": "button#submit"}""", context)
        assertThat(clickRes).contains("Successfully clicked element matching 'button#submit'")
        assertThat(fakeBrowser.lastClickedSelector).isEqualTo("button#submit")

        val typeTool = BrowserTypeTool()
        val typeRes = typeTool.execute("""{"selector": "input#query", "text": "mobile dev"}""", context)
        assertThat(typeRes).contains("Successfully typed text")
        assertThat(fakeBrowser.lastTypedSelector).isEqualTo("input#query")
        assertThat(fakeBrowser.lastTypedText).isEqualTo("mobile dev")
    }

    @Test
    fun testBrowserEvalAndConsoleTools() = runBlocking {
        val evalTool = BrowserEvalTool()
        val evalRes = evalTool.execute("""{"script": "document.title"}""", context)
        assertThat(evalRes).contains("Result: window.title = 'Hello World'")

        fakeBrowser.emitConsoleLog(ConsoleLogLevel.ERROR, "Uncaught TypeError: cannot read property of null")
        fakeBrowser.emitConsoleLog(ConsoleLogLevel.LOG, "App mounted on :3000")

        val consoleTool = BrowserConsoleTool()
        val consoleRes = consoleTool.execute("""{"level": "ALL"}""", context)
        assertThat(consoleRes).contains("=== Browser Console Logs (2 entries) ===")
        assertThat(consoleRes).contains("[ERROR]")
        assertThat(consoleRes).contains("Uncaught TypeError")
        assertThat(consoleRes).contains("[LOG]")
    }

    @Test
    fun testBrowserPolicyOnLocalhostVsExternal() {
        val policy = PolicyEngine(AutonomyMode.SEMI_AUTONOMY)

        // Localhost is auto-allowed
        val localCall = ToolCall("1", "browser_open", """{"url": "http://localhost:5173"}""")
        val localDecision = policy.evaluate(localCall, workspace)
        assertThat(localDecision).isInstanceOf(PolicyDecision.Allow::class.java)

        val loopbackCall = ToolCall("2", "browser_open", """{"url": "http://127.0.0.1:8080/app"}""")
        val loopbackDecision = policy.evaluate(loopbackCall, workspace)
        assertThat(loopbackDecision).isInstanceOf(PolicyDecision.Allow::class.java)

        // External URL requires approval in SEMI_AUTONOMY
        val externalCall = ToolCall("3", "browser_open", """{"url": "https://external-website.com"}""")
        val extDecision = policy.evaluate(externalCall, workspace)
        assertThat(extDecision).isInstanceOf(PolicyDecision.RequireApproval::class.java)

        // Screenshots and logs are read-only -> Allowed
        val shotCall = ToolCall("4", "browser_screenshot", "{}")
        assertThat(policy.evaluate(shotCall, workspace)).isInstanceOf(PolicyDecision.Allow::class.java)

        val logCall = ToolCall("5", "browser_console", "{}")
        assertThat(policy.evaluate(logCall, workspace)).isInstanceOf(PolicyDecision.Allow::class.java)
    }
}

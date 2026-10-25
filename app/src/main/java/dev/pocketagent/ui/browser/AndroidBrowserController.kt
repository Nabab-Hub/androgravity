package dev.pocketagent.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.pocketagent.core.agent.browser.BrowserController
import dev.pocketagent.core.model.BrowserNavigationState
import dev.pocketagent.core.model.ConsoleLogEntry
import dev.pocketagent.core.model.ConsoleLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume

class AndroidBrowserController(
    val context: Context
) : BrowserController {

    private val _navigationState = MutableStateFlow(BrowserNavigationState())
    override val navigationState: StateFlow<BrowserNavigationState> = _navigationState.asStateFlow()

    private val _consoleLogs = MutableStateFlow<List<ConsoleLogEntry>>(emptyList())
    override val consoleLogs: StateFlow<List<ConsoleLogEntry>> = _consoleLogs.asStateFlow()

    private val logBuffer = CopyOnWriteArrayList<ConsoleLogEntry>()
    private val maxLogBufferSize = 500

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    /**
     * Attaches an existing WebView (e.g. from Compose AndroidView) or creates a headless one.
     */
    fun attachWebView(view: WebView) {
        this.webView = view
        configureWebView(view)
    }

    fun detachWebView() {
        this.webView = null
    }

    fun getOrCreateWebView(): WebView {
        var view = webView
        if (view == null) {
            view = WebView(context)
            this.webView = view
            configureWebView(view)
        }
        return view
    }

    private fun configureWebView(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    val level = when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> ConsoleLogLevel.ERROR
                        ConsoleMessage.MessageLevel.WARNING -> ConsoleLogLevel.WARN
                        ConsoleMessage.MessageLevel.LOG -> ConsoleLogLevel.LOG
                        ConsoleMessage.MessageLevel.TIP -> ConsoleLogLevel.INFO
                        ConsoleMessage.MessageLevel.DEBUG -> ConsoleLogLevel.DEBUG
                        else -> ConsoleLogLevel.LOG
                    }
                    val entry = ConsoleLogEntry(
                        level = level,
                        message = consoleMessage.message() ?: "",
                        sourceId = consoleMessage.sourceId() ?: "",
                        lineNumber = consoleMessage.lineNumber(),
                        timestamp = System.currentTimeMillis()
                    )
                    addConsoleLog(entry)
                }
                return super.onConsoleMessage(consoleMessage)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                _navigationState.value = _navigationState.value.copy(title = title ?: "")
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                _navigationState.value = _navigationState.value.copy(
                    currentUrl = url ?: "",
                    isLoading = true,
                    canGoBack = view?.canGoBack() ?: false,
                    canGoForward = view?.canGoForward() ?: false
                )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                _navigationState.value = _navigationState.value.copy(
                    currentUrl = url ?: "",
                    isLoading = false,
                    canGoBack = view?.canGoBack() ?: false,
                    canGoForward = view?.canGoForward() ?: false
                )
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // load in webview
            }
        }
    }

    private fun addConsoleLog(entry: ConsoleLogEntry) {
        logBuffer.add(entry)
        if (logBuffer.size > maxLogBufferSize) {
            logBuffer.removeAt(0)
        }
        _consoleLogs.value = logBuffer.toList()
    }

    override suspend fun openUrl(url: String): Boolean = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        wv.loadUrl(url)
        true
    }

    override suspend fun takeScreenshot(outputFile: File): String = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        val width = if (wv.width > 0) wv.width else 1080
        val height = if (wv.height > 0) wv.height else 1920

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        wv.draw(canvas)

        withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        outputFile.absolutePath
    }

    override suspend fun click(selector: String): Boolean = withContext(Dispatchers.Main) {
        val escapedSelector = selector.replace("'", "\\'")
        val js = """
            (function() {
                var el = document.querySelector('$escapedSelector');
                if (el) {
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    el.click();
                    return 'true';
                }
                return 'false';
            })();
        """.trimIndent()

        val res = evaluateJs(js)
        res.trim().replace("\"", "") == "true"
    }

    override suspend fun typeText(selector: String, text: String, clearFirst: Boolean): Boolean = withContext(Dispatchers.Main) {
        val escapedSelector = selector.replace("'", "\\'")
        val escapedText = text.replace("'", "\\'").replace("\n", "\\n")
        val js = """
            (function() {
                var el = document.querySelector('$escapedSelector');
                if (el) {
                    el.focus();
                    if ($clearFirst) {
                        el.value = '';
                    }
                    el.value += '$escapedText';
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    return 'true';
                }
                return 'false';
            })();
        """.trimIndent()

        val res = evaluateJs(js)
        res.trim().replace("\"", "") == "true"
    }

    override suspend fun evaluateJs(script: String): String = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        suspendCancellableCoroutine { continuation ->
            wv.evaluateJavascript(script) { result ->
                continuation.resume(result ?: "null")
            }
        }
    }

    override fun getConsoleLogs(): List<ConsoleLogEntry> = logBuffer.toList()

    override fun clearConsoleLogs() {
        logBuffer.clear()
        _consoleLogs.value = emptyList()
    }
}

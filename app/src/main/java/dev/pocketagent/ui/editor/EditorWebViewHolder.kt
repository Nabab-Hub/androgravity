package dev.pocketagent.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object EditorWebViewHolder {

    private var cachedWebView: WebView? = null
    var isReady: Boolean = false
        private set

    private var cachedHtml: String? = null

    private fun getHtmlContent(context: Context): String {
        cachedHtml?.let { return it }
        return try {
            context.assets.open("monaco/index.html").bufferedReader().use { it.readText() }.also {
                cachedHtml = it
            }
        } catch (_: Throwable) {
            "<!DOCTYPE html><html><body><textarea id=\"code-area\"></textarea></body></html>"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun prewarm(context: Context) {
        if (cachedWebView != null) return
        try {
            val html = getHtmlContent(context)
            val wv = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isReady = true
                    }
                }
                loadDataWithBaseURL("file:///android_asset/monaco/", html, "text/html", "UTF-8", null)
            }
            cachedWebView = wv
        } catch (t: Throwable) {
            android.util.Log.w("EditorWebViewHolder", "Prewarm failed", t)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun acquire(context: Context, bridge: MonacoBridge): WebView {
        var wv = cachedWebView
        if (wv == null) {
            prewarm(context)
            wv = cachedWebView ?: WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
            }
            cachedWebView = wv
        }

        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.removeJavascriptInterface("AndroidBridge")
        wv.addJavascriptInterface(bridge, "AndroidBridge")

        return wv
    }

    fun markReady() {
        isReady = true
    }

    fun clear() {
        try {
            cachedWebView?.destroy()
        } catch (_: Throwable) {}
        cachedWebView = null
        isReady = false
    }
}

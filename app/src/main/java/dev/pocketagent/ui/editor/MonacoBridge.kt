package dev.pocketagent.ui.editor

import android.webkit.JavascriptInterface

class MonacoBridge(
    private val onReady: () -> Unit,
    private val onContentChanged: (String) -> Unit,
    private val onSaveRequested: () -> Unit
) {
    @JavascriptInterface
    fun onEditorReady() {
        onReady()
    }

    @JavascriptInterface
    fun onContentChange(content: String) {
        onContentChanged(content)
    }

    @JavascriptInterface
    fun onSave() {
        onSaveRequested()
    }
}

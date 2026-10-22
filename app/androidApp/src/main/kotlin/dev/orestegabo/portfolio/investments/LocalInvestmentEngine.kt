package dev.orestegabo.portfolio.investments

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Sandboxed local JS algorithms only. All visible product UI is Compose. No remote pages or session cookies. */
@SuppressLint("SetJavaScriptEnabled")
class LocalInvestmentEngine(context: Context) {
    private val assets = context.assets
    private val initialized = CompletableDeferred<Unit>()
    private val pending = mutableMapOf<String, CompletableDeferred<JSONObject>>()
    private var sequence = 0
    var onProgress: (String) -> Unit = {}
    val view = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                val uri = request.url
                if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net" || uri.path.orEmpty().contains("..")) return blocked()
                val name = uri.path.orEmpty().trimStart('/')
                return try {
                    val mime = when (name.substringAfterLast('.')) {
                        "js", "mjs" -> "text/javascript"
                        "html" -> "text/html"
                        "wasm" -> "application/wasm"
                        "json" -> "application/json"
                        else -> "application/octet-stream"
                    }
                    WebResourceResponse(mime, if (mime.startsWith("text/")) "UTF-8" else null, assets.open("engine/$name"))
                } catch (_: Exception) { blocked() }
            }
        }
        addJavascriptInterface(object {
            @JavascriptInterface fun ready() { post { initialized.complete(Unit) } }
            @JavascriptInterface fun progress(message: String) { post { onProgress(message.take(160)) } }
            @JavascriptInterface fun result(id: String, raw: String) { post {
                val result = pending.remove(id) ?: return@post
                runCatching { JSONObject(raw) }.onSuccess { data ->
                    if (data.has("error")) result.completeExceptionally(IllegalArgumentException(data.text("error"))) else result.complete(data)
                }.onFailure { result.completeExceptionally(it) }
            } }
        }, "Gabo")
        loadUrl("https://appassets.androidplatform.net/index.html")
    }

    private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))

    suspend fun run(command: String, payload: JSONObject = JSONObject(), pdf: ByteArray? = null): JSONObject = withContext(Dispatchers.Main) {
        withTimeout(150_000) {
            withTimeout(20_000) { initialized.await() }
            val id = (++sequence).toString()
            val result = CompletableDeferred<JSONObject>()
            pending[id] = result
            try {
                if (pdf != null) {
                    require(pdf.size in 1..10 * 1024 * 1024) { "Choose a PDF of up to 10 MB." }
                    Base64.encodeToString(pdf, Base64.NO_WRAP).chunked(64 * 1024).forEach { chunk ->
                        val sent = CompletableDeferred<Unit>()
                        view.evaluateJavascript("gabo.append(${JSONObject.quote(chunk)})") { sent.complete(Unit) }
                        sent.await()
                    }
                }
                view.evaluateJavascript("gabo.run(${JSONObject.quote(id)},${JSONObject.quote(command)},$payload)", null)
                result.await()
            } finally { pending.remove(id) }
        }
    }

    fun cancel() { view.evaluateJavascript("window.gabo?.cancel()", null) }
    fun destroy() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        view.removeJavascriptInterface("Gabo")
        view.stopLoading()
        view.destroy()
    }
}

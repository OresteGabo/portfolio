package dev.orestegabo.portfolio.investments

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.*
import android.view.ViewGroup
import androidx.compose.runtime.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

/** Sandboxed local JS algorithms only. All visible product UI is Compose. No remote pages or session cookies. */
@SuppressLint("SetJavaScriptEnabled")
class LocalInvestmentEngine(context: Context, private val privateDocuments: Boolean = false) : InvestmentEngine {
    private val assets = context.assets
    private var initialized = CompletableDeferred<Unit>()
    private val pending = mutableMapOf<String, CompletableDeferred<JsonRecord>>()
    private val sends = mutableSetOf<CompletableDeferred<Unit>>()
    private var sequence = 0
    @Volatile private var destroyed = false
    override var onProgress: (String) -> Unit = {}
    private val context = context
    private var activeView: WebView? = null
    var hostRequested by mutableStateOf(false); private set
    var rendererGeneration by mutableIntStateOf(0); private set
    // Do not construct a WebView/JS runtime just to display an offline portfolio.
    val view: WebView get() {
        check(!destroyed) { "The local engine is closed." }
        return activeView ?: createView().also { activeView=it }
    }
    private fun createView(): WebView {
        val ready=initialized
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.blockNetworkLoads = privateDocuments
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (activeView === view) {
                        activeView=null
                    hostRequested=false;rendererGeneration++
                        val failure=IllegalStateException("Local document processing stopped. Retry the PDF or enter the investment manually.")
                        ready.completeExceptionally(failure)
                        pending.values.forEach { it.completeExceptionally(failure) }
                        pending.clear()
                        sends.forEach { it.completeExceptionally(failure) }; sends.clear()
                        initialized=CompletableDeferred()
                    }
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.removeJavascriptInterface("Gabo")
                    view.destroy()
                    return true // A sandbox renderer failure must not terminate the financial app.
                }
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
                        WebResourceResponse(mime, if (mime.startsWith("text/")) "UTF-8" else null, (runCatching { assets.open("engine/$name") }.getOrElse { runBlocking { readSharedAsset("engine/$name") }.inputStream() }))
                    } catch (_: Exception) { blocked() }
                }
            }
            addJavascriptInterface(object {
                @JavascriptInterface fun ready() { post { ready.complete(Unit) } }
                @JavascriptInterface fun progress(message: String) { post { onProgress(message.take(160)) } }
                @JavascriptInterface fun result(id: String, raw: String) { post {
                    val result = pending.remove(id) ?: return@post
                    runCatching { JsonRecord(raw) }.onSuccess { data ->
                        if (data.has("error")) result.completeExceptionally(IllegalArgumentException(data.text("error"))) else result.complete(data)
                    }.onFailure { result.completeExceptionally(it) }
                } }
            }, "Gabo")
            loadUrl("https://appassets.androidplatform.net/index.html")
        }
    }

    private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))

    override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = withContext(Dispatchers.Main) {
        check(!destroyed) { "The local engine is closed." }
        hostRequested=true
        val target=view
        val ready=initialized
        withTimeout(150_000) {
            withTimeout(20_000) { ready.await() }
            val id = (++sequence).toString()
            val result = CompletableDeferred<JsonRecord>()
            pending[id] = result
            try {
                if (pdf != null) {
                    require(pdf.size in 1..MAX_PDF_BYTES) { "Choose a PDF of up to 10 MB." }
                    Base64.encodeToString(pdf, Base64.NO_WRAP).chunked(64 * 1024).forEach { chunk ->
                        val sent = CompletableDeferred<Unit>()
                        check(activeView === target && !destroyed) { "Local document processing stopped. Retry the PDF." }
                        sends.add(sent)
                        try {
                            target.evaluateJavascript("gabo.append(${JsonRecord.quote(chunk)})") { sent.complete(Unit) }
                            sent.await()
                        } finally { sends.remove(sent) }
                    }
                }
                target.evaluateJavascript("gabo.run(${JsonRecord.quote(id)},${JsonRecord.quote(command)},$payload)", null)
                result.await()
            } finally { pending.remove(id) }
        }
    }

    override fun cancel() { activeView?.let { target -> target.post { if (!destroyed && activeView === target) target.evaluateJavascript("window.gabo?.cancel()", null) } } }
    override fun destroy() {
        destroyed = true
        pending.values.forEach { it.cancel() }
        pending.clear()
        sends.forEach { it.cancel() }; sends.clear()
        initialized.cancel()
        activeView?.let { it.removeJavascriptInterface("Gabo"); it.stopLoading(); it.destroy() }
        activeView=null;hostRequested=false
    }
}

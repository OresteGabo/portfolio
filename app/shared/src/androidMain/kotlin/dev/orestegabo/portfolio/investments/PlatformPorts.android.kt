package dev.orestegabo.portfolio.investments

import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import android.content.Context
import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri

@Composable
actual fun rememberServerSettings(): ServerSettings {
    val context = LocalContext.current.applicationContext
    return remember(context) { object : ServerSettings {
        private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
        override fun readImportHintDismissed() = prefs.getBoolean("importHintDismissed", false)
        override fun writeImportHintDismissed(dismissed: Boolean) { prefs.edit().putBoolean("importHintDismissed", dismissed).apply() }
        override fun readOnboardingCompleted() = prefs.getBoolean("onboardingCompleted", false)
        override fun writeOnboardingCompleted(completed: Boolean) { prefs.edit().putBoolean("onboardingCompleted", completed).apply() }
        override fun readScreenCaptureAllowed() = prefs.getBoolean(SCREEN_CAPTURE_ALLOWED_KEY, false)
        override fun writeScreenCaptureAllowed(allowed: Boolean) { prefs.edit().putBoolean(SCREEN_CAPTURE_ALLOWED_KEY, allowed).apply() }
        override fun readShakeToHideEnabled() = prefs.getBoolean("shakeToHideEnabled", true)
        override fun writeShakeToHideEnabled(enabled: Boolean) { prefs.edit().putBoolean("shakeToHideEnabled", enabled).apply() }
        override fun readNumbersHidden() = prefs.getBoolean("numbersHidden", false)
        override fun writeNumbersHidden(hidden: Boolean) { prefs.edit().putBoolean("numbersHidden", hidden).apply() }
        override fun forgetMonthlyTarget(account: String) { prefs.edit().remove("monthlyTarget:$account").apply() }
        override fun readMonthlyTarget(account: String): Long? = if (prefs.contains("monthlyTarget:$account")) prefs.getLong("monthlyTarget:$account", 100_000L) else null
        override fun writeMonthlyTarget(account: String, amount: Long) { prefs.edit().putLong("monthlyTarget:$account", amount).apply() }
        override fun readChartStyle(): String? = prefs.getString("chartStyle", null)
        override fun writeChartStyle(style: String) { prefs.edit().putString("chartStyle", style).apply() }
        override fun readLanguage() = prefs.getString("language", null)
        override fun writeLanguage(language: String) { prefs.edit().putString("language", language).apply() }
        override fun readTheme(): String? = prefs.getString("theme", null)
        override fun writeTheme(theme: String) { prefs.edit().putString("theme", theme).apply() }
        override fun readImportHistory(account: String) = prefs.getString("importHistory:$account", null)
        override fun writeImportHistory(account: String, history: String) { prefs.edit().putString("importHistory:$account", history).apply() }
        override fun readOrigin(): String? = prefs.getString("origin", null)
        override fun writeOrigin(origin: String) { prefs.edit().putString("origin", origin).apply() }
    } }
}
private class AndroidActions(private val context: Context) : NativeActions {
    override val interactingWithSystem get() = picked != null || saved != null || shared != null
    override var pickedPdfName: String? = null
        private set
    var pickLauncher: () -> Unit = {}
    var saveLauncher: (String) -> Unit = {}
    var shareLauncher: (Intent) -> Unit = {}
    var shared: CompletableDeferred<Unit>? = null
    var picked: CompletableDeferred<Uri?>? = null
    var saved: CompletableDeferred<Uri?>? = null
    override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? {
        val request = CompletableDeferred<Uri?>()
        check(picked == null) { "A file picker is already open." }
        picked = request
        pickedPdfName = null
        try {
            pickLauncher()
            val uri = request.await() ?: return null
            onSelected()
            return withContext(Dispatchers.IO) {
                pickedPdfName = runCatching { context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0).take(180) else null
                } }.getOrNull()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_PDF_BYTES) { "Choose a PDF of up to 10 MB." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("Could not open the selected PDF.")
            }
        } finally { picked = null }
    }
    override suspend fun savePdf(fileName: String, bytes: ByteArray): Boolean {
        val request = CompletableDeferred<Uri?>()
        check(saved == null) { "A save dialog is already open." }
        saved = request
        try {
            saveLauncher(fileName)
            val uri = request.await() ?: return false
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Unable to save the document.")
            }
            return true
        } finally { saved = null }
    }
    override suspend fun sharePdf(fileName: String, bytes: ByteArray): Boolean {
        check(shared == null) { "A share sheet is already open." }
        val request = CompletableDeferred<Unit>()
        shared = request
        try {
            val intent = withContext(Dispatchers.IO) { pdfShareIntent(context, fileName, bytes) }
            shareLauncher(Intent.createChooser(intent, localized("Share PDF")))
            request.await()
            // Android does not report whether a recipient actually sent/saved the file.
            return true
        } finally { shared = null }
    }
    override fun openUrl(url: String) {
        require(Uri.parse(url).scheme in listOf("https", "http"))
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    override fun openEmail(address: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)))
        } catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, address),
                localized("Share email address"),
            ))
        }
    }
    override fun shareText(text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), localized("Share invitation")))
    }
}
@Composable
actual fun rememberNativeActions(): NativeActions {
    val context = LocalContext.current
    val actions = remember(context) { AndroidActions(context) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { actions.picked?.complete(it) }
    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { actions.shared?.complete(Unit) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { actions.saved?.complete(it) }
    SideEffect {
        actions.shareLauncher = { share.launch(it) }
        actions.pickLauncher = { pick.launch(arrayOf("application/pdf")) }
        actions.saveLauncher = { save.launch(it) }
    }
    DisposableEffect(actions) { onDispose { actions.picked?.cancel(); actions.saved?.cancel(); actions.shared?.cancel() } }
    return actions
}
@Composable
actual fun rememberInvestmentEngine(): InvestmentEngine {
    val context = LocalContext.current
    return remember(context) { LocalInvestmentEngine(context) }
}
@Composable
actual fun InvestmentEngineHost(engine: InvestmentEngine) {
    if (engine is LocalInvestmentEngine) AndroidView(factory = { engine.view }, modifier = Modifier.size(1.dp))
}
@Composable
actual fun PlatformBackHandler(enabled: Boolean, back: () -> Unit) { BackHandler(enabled, back) }

@Composable
actual fun PlatformSystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    var context = view.context
    while (context is ContextWrapper && context !is Activity) context = context.baseContext
    val activity = context as? Activity ?: return
    SideEffect {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

/** The provider exposes only this private cache subtree, with a per-file temporary read grant. */
fun pdfShareIntent(context: Context, fileName: String, bytes: ByteArray): Intent {
    require(bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-") { "The server did not return a PDF." }
    val root = java.io.File(context.cacheDir, "pdf-share").apply { mkdirs() }
    val expiry = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    root.listFiles()?.filter { it.lastModified() < expiry }?.forEach { it.deleteRecursively() }
    val directory = java.io.File(root, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
    val name = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "investment.pdf" }
        .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
    val file = java.io.File(directory, name)
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.pdfs", file)
    return Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
        .apply { clipData = ClipData.newRawUri("PDF", uri) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

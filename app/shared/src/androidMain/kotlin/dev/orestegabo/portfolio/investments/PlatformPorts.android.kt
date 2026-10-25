package dev.orestegabo.portfolio.investments

import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import android.content.Context
import android.content.Intent
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
        override fun readScreenCaptureAllowed() = prefs.getBoolean(SCREEN_CAPTURE_ALLOWED_KEY, false)
        override fun writeScreenCaptureAllowed(allowed: Boolean) { prefs.edit().putBoolean(SCREEN_CAPTURE_ALLOWED_KEY, allowed).apply() }
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
        override fun readOrigin(): String? = prefs.getString("origin", null)
        override fun writeOrigin(origin: String) { prefs.edit().putString("origin", origin).apply() }
    } }
}
private class AndroidActions(private val context: Context) : NativeActions {
    override val interactingWithSystem get() = picked != null || saved != null
    var pickLauncher: () -> Unit = {}
    var saveLauncher: (String) -> Unit = {}
    var picked: CompletableDeferred<Uri?>? = null
    var saved: CompletableDeferred<Uri?>? = null
    override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? {
        val request = CompletableDeferred<Uri?>()
        check(picked == null) { "A file picker is already open." }
        picked = request
        try {
            pickLauncher()
            val uri = request.await() ?: return null
            onSelected()
            return withContext(Dispatchers.IO) {
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
    override fun openUrl(url: String) {
        require(Uri.parse(url).scheme in listOf("https", "http"))
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { actions.saved?.complete(it) }
    SideEffect {
        actions.pickLauncher = { pick.launch(arrayOf("application/pdf")) }
        actions.saveLauncher = { save.launch(it) }
    }
    DisposableEffect(actions) { onDispose { actions.picked?.cancel(); actions.saved?.cancel() } }
    return actions
}
@Composable
actual fun rememberInvestmentEngine(): InvestmentEngine {
    val context = LocalContext.current
    return remember(context) { LocalInvestmentEngine(context) }
}
@Composable
actual fun InvestmentEngineHost(engine: InvestmentEngine) {
    AndroidView(factory = { (engine as LocalInvestmentEngine).view }, modifier = Modifier.size(1.dp))
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

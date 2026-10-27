package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

@Composable expect fun PlatformSystemBarAppearance(darkTheme: Boolean)

interface ServerSettings {
    fun readLanguage(): String? = null
    fun writeLanguage(language: String) {}
    fun readScreenCaptureAllowed(): Boolean = false
    fun writeScreenCaptureAllowed(allowed: Boolean) {}
    fun readShakeToHideEnabled(): Boolean = true
    fun writeShakeToHideEnabled(enabled: Boolean) {}
    fun readNumbersHidden(): Boolean = false
    fun writeNumbersHidden(hidden: Boolean) {}
    fun forgetMonthlyTarget(account: String) {}
    fun readMonthlyTarget(account: String): Long? = null
    fun writeMonthlyTarget(account: String, amount: Long) {}
    fun readOrigin(): String?
    fun writeOrigin(origin: String)
    fun readChartStyle(): String? = null
    fun writeChartStyle(style: String) {}
    fun readTheme(): String? = null
    fun writeTheme(theme: String) {}
}
interface NativeActions {
    val interactingWithSystem: Boolean get() = false
    suspend fun offerPasswordSave(origin: String, email: String, password: String) {}
    suspend fun pickPdf(onSelected: () -> Unit = {}): ByteArray?
    suspend fun savePdf(fileName: String, bytes: ByteArray): Boolean
    fun openUrl(url: String)
    /** Open a draft in the mail app; platforms without one can share the address. */
    fun openEmail(address: String) { shareText(address) }
    fun shareText(text: String)
}
interface InvestmentEngine {
    var onProgress: (String) -> Unit
    suspend fun run(command: String, payload: JsonRecord = JsonRecord(), pdf: ByteArray? = null): JsonRecord
    fun cancel()
    fun destroy()
}
@Composable expect fun rememberServerSettings(): ServerSettings
@Composable expect fun rememberNativeActions(): NativeActions
@Composable expect fun rememberInvestmentEngine(): InvestmentEngine
@Composable expect fun InvestmentEngineHost(engine: InvestmentEngine)
@Composable expect fun PlatformBackHandler(enabled: Boolean, back: () -> Unit)
val LocalNativeActions = staticCompositionLocalOf<NativeActions> { error("Missing native actions") }

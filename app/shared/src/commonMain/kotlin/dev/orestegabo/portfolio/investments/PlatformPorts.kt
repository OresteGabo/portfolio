package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

interface ServerSettings {
    fun readOrigin(): String?
    fun writeOrigin(origin: String)
    fun readTheme(): String? = null
    fun writeTheme(theme: String) {}
}
interface NativeActions {
    suspend fun pickPdf(): ByteArray?
    suspend fun savePdf(fileName: String, bytes: ByteArray): Boolean
    fun openUrl(url: String)
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

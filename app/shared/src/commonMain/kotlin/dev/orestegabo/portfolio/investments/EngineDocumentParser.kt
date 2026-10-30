package dev.orestegabo.portfolio.investments

/** Compatibility adapter for the existing Android WebView / iPhone PDFKit+JavaScriptCore engines.
 * Engine lifecycle remains owned by the UI host, not by an individual import operation.
 */
internal class EngineDocumentParser(private val engine: InvestmentEngine) : InvestmentDocumentParser {
    override var onProgress: (String) -> Unit
        get() = engine.onProgress
        set(value) { engine.onProgress = value }
    override suspend fun parse(pdf: ByteArray) = engine.run("pdf", pdf = pdf)
    override fun cancel() = engine.cancel()
}

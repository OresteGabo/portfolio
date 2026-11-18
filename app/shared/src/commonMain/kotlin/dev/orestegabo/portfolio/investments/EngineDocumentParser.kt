package dev.orestegabo.portfolio.investments

/** Adapter for native extraction and shared Kotlin investment rules.
 * Engine lifecycle remains owned by the UI host, not by an individual import operation.
 */
internal class EngineDocumentParser(private val engine: InvestmentEngine) : InvestmentDocumentParser {
    override var onProgress: (String) -> Unit
        get() = engine.onProgress
        set(value) { engine.onProgress = value }
    override suspend fun parse(pdf: ByteArray) = engine.run("pdf", pdf = pdf)
    override fun cancel() = engine.cancel()
}

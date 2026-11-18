package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/** Serialized local operations. Both platforms use the same Kotlin rules and simulator. */
open class KotlinInvestmentEngine(
    private val extract: suspend (ByteArray, (String) -> Unit) -> Pair<String, Boolean>,
) : InvestmentEngine {
    override var onProgress: (String) -> Unit = {}
    @Volatile private var destroyed = false
    @Volatile private var job: Job? = null
    private val mutex = Mutex()
    override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = mutex.withLock {
        check(!destroyed) { "The local engine is closed." }
        withContext(Dispatchers.Default) {
            job = currentCoroutineContext()[Job]
            try {
                withTimeout(150_000) {
                    when (command) {
                        "projection" -> InvestmentProjection.project(payload)
                        "pdf" -> {
                            val (text, ocr) = extract(requireNotNull(pdf)) { onProgress(it) }
                            ensureActive()
                            try { InvestmentDocumentRules.parse(text, ocr) }
                            catch (_: DocumentParseException) { error("Could not recognize this PDF. Use a BNR bond result, share purchase contract or BK Capital statement.") }
                        }
                        else -> error("Unknown investment operation.")
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            finally { job = null }
        }
    }
    override fun cancel() { job?.cancel() }
    override fun destroy() { destroyed = true; cancel() }
}

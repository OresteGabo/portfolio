@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.JavaScriptCore.JSContext

/** Owns serialized local operations and JavaScriptCore; PDF extraction has a separate native owner. */
class IosInvestmentEngine : InvestmentEngine {
    override var onProgress: (String) -> Unit = {}
    private var context: JSContext? = null
    private val pdfExtractor = IosPdfTextExtractor()
    private var destroyed = false
    private val mutex = Mutex()
    private var job: Job? = null
    override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = mutex.withLock {
        check(!destroyed) { "The local engine is closed." }
        coroutineScope {
            job = coroutineContext[Job]
            try {
                withTimeout(150_000) {
                    if (command == "pdf") {
                        val (text, ocr) = pdfExtractor.extract(requireNotNull(pdf)) { onProgress(it) }
                        evaluate("PortfolioLogic.parse(${JsonRecord.quote(text)},$ocr)", pdfImport = true)
                    } else {
                        require(command == "projection") { "Unknown investment operation." }
                        evaluate("PortfolioLogic.project($payload)")
                    }
                }
            } finally { job = null }
        }
    }
    private suspend fun evaluate(expression: String, pdfImport: Boolean = false): JsonRecord = withContext(Dispatchers.Main) {
        val js = context ?: JSContext().also {
            it.evaluateScript(readSharedAsset("engine/logic.js").decodeToString())
            context = it
        }
        js.exception = null
        // Keep only our typed error code, never JavaScript errors containing recognized document text.
        val value = js.evaluateScript("""(function(){try{return JSON.stringify({result:($expression)})}catch(e){return JSON.stringify({errorCode:typeof e.code==='string'?e.code:'operation_failed'})}})()""")
        val generic = if (pdfImport) "Could not read this purchase PDF. Try a clearer copy."
            else "The investment operation could not be completed. Check the input and try again."
        check(js.exception == null && value != null && !value.isUndefined()) { generic }
        val envelope = JsonRecord(value.toString())
        envelope.optJSONObject("result") ?: error(if (pdfImport && envelope.text("errorCode") == "not_bond_result")
            "Could not recognize this PDF. Use a BNR bond result, share purchase contract or BK Capital statement." else generic)
    }
    override fun cancel() { job?.cancel() }
    override fun destroy() { destroyed = true; cancel(); context = null }
}

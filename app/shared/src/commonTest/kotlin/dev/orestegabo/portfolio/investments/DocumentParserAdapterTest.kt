@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class DocumentParserAdapterTest {
    private val pdf = "%PDF-1.7 synthetic parser fixture\n%%EOF".encodeToByteArray()
    private fun actions() = object : NativeActions {
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return pdf }
        override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
        override fun openUrl(url: String) {}
        override fun shareText(text: String) {}
    }
    @Test fun aParserAdapterCanRunTheImportPipelineWithoutASimulationEngine() = runTest {
        val parsed = JsonRecord().put("kind", "equity")
        val stages = mutableListOf<PdfImportStage>()
        var progress = ""
        val parser = object : InvestmentDocumentParser {
            override var onProgress: (String) -> Unit = {}
            override suspend fun parse(pdf: ByteArray): JsonRecord {
                assertContentEquals(this@DocumentParserAdapterTest.pdf, pdf)
                onProgress("Reading page 1 of 1")
                return parsed
            }
            override fun cancel() = error("Successful import must not cancel")
        }
        assertSame(parsed, readPurchasePdf(actions(), parser, progress = {
            it?.let { stages += it.stage; if (it.detail.isNotBlank()) progress = it.detail }
        }))
        assertEquals("Reading page 1 of 1", progress)
        assertTrue(PdfImportStage.EXTRACTING in stages && PdfImportStage.CHECKING in stages)
    }
    @Test fun cancellingThePipelineCancelsTheParserAndClearsProgress() = runTest {
        var cancelled = false
        var stage: PdfImportProgress? = null
        val entered = CompletableDeferred<Unit>()
        val parser = object : InvestmentDocumentParser {
            override var onProgress: (String) -> Unit = {}
            override suspend fun parse(pdf: ByteArray): JsonRecord { entered.complete(Unit); awaitCancellation() }
            override fun cancel() { cancelled = true }
        }
        val job = launch { readPurchasePdf(actions(), parser, progress = { stage = it }) }
        entered.await(); job.cancelAndJoin()
        assertTrue(cancelled); assertNull(stage)
    }
    @Test fun legacyEngineAdapterOnlyDispatchesThePdfCommandAndForwardsCancellation() = runTest {
        var cancelled = false
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord {
                this@DocumentParserAdapterTest.pdf.let { assertContentEquals(it, pdf) }
                assertEquals("pdf", command)
                return JsonRecord().put("kind", "statement")
            }
            override fun cancel() { cancelled = true }
            override fun destroy() = error("Adapter must not destroy a UI-owned engine")
        }
        val adapter = EngineDocumentParser(engine)
        assertEquals("statement", adapter.parse(pdf).text("kind"))
        adapter.cancel(); assertTrue(cancelled)
    }
}

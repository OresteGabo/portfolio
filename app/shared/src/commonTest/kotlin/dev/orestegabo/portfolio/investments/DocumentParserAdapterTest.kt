@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class DocumentParserAdapterTest {
    private val pdf = "%PDF-1.7 synthetic parser fixture\n%%EOF".encodeToByteArray()
    private fun actions() = object : PdfPicker {
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return pdf }
    }
    @Test fun cancellingSelectionNeedsNoOtherDeviceCapabilitiesAndNeverParses() = runTest {
        val picker = object : PdfPicker {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
        }
        val parser = object : InvestmentDocumentParser {
            override var onProgress: (String) -> Unit = {}
            override suspend fun parse(pdf: ByteArray): JsonRecord = error("No document selected")
            override fun cancel() = error("Selection cancellation is not parser cancellation")
        }
        val progress = mutableListOf<PdfImportProgress?>()
        assertNull(readPurchasePdf(picker, parser, progress = progress::add))
        assertEquals(listOf<PdfImportProgress?>(null), progress)
    }
    @Test fun parserFailureReleasesCallbacksThroughTheSelectionContract() = runTest {
        val failure = IllegalArgumentException("Unreadable document")
        val progress = mutableListOf<PdfImportProgress?>()
        val parser = object : InvestmentDocumentParser {
            override var onProgress: (String) -> Unit = {}
            override suspend fun parse(pdf: ByteArray): JsonRecord = throw failure
            override fun cancel() = error("Failure is not cancellation")
        }
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            readPurchasePdf(actions(), parser, progress = progress::add)
        })
        assertNull(progress.last())
        val completed = progress.size
        parser.onProgress("Late progress")
        assertEquals(completed, progress.size)
    }
    @Test fun injectedMonotonicTimeControlsTheMinimumFeedbackDuration() = runTest {
        var completed = false
        val parser = object : InvestmentDocumentParser {
            override var onProgress: (String) -> Unit = {}
            override suspend fun parse(pdf: ByteArray) = JsonRecord().put("kind", "bond")
            override fun cancel() = error("Successful import must not cancel")
        }
        val job = launch {
            readPurchasePdf(actions(), parser, timeSource = testScheduler.timeSource)
            completed = true
        }
        runCurrent()
        advanceTimeBy(MIN_PDF_PROGRESS_MILLIS - 1); runCurrent()
        assertFalse(completed)
        advanceTimeBy(1); runCurrent()
        assertTrue(completed)
        job.join()
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

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/** Tests the proposal/byte-publication boundary without native extraction or persistence. */
class PdfPipelineBoundaryRegressionTest {
    private fun picker() = object : PdfPicker {
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray {
            onSelected(); return "%PDF-1.7 synthetic\n%%EOF".encodeToByteArray()
        }
    }
    private class Parser(val read: suspend () -> JsonRecord) : InvestmentDocumentParser {
        override var onProgress: (String) -> Unit = {}
        var calls = 0
        var cancelled = false
        override suspend fun parse(pdf: ByteArray): JsonRecord { calls++; return read() }
        override fun cancel() { cancelled = true }
    }

    @Test fun dismissingPickerClearsFeedbackWithoutParsingOrPublishingBytes() = runTest {
        val parser = Parser { error("Dismissed selection must not be parsed") }
        val stages = mutableListOf<PdfImportProgress?>()
        var published = false
        val result = readInvestmentPdf(object : PdfPicker {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
        }, parser, selectedPdf = { published = true }, progress = stages::add, timeSource = testScheduler.timeSource)
        assertNull(result); assertEquals(0, parser.calls); assertFalse(published)
        assertEquals(listOf<PdfImportProgress?>(null), stages)
    }

    @Test fun acquisitionCallbackFailurePreservesCauseAndNeverStartsParsing() = runTest {
        val failure = IllegalStateException("synthetic acquisition failure")
        val parser = Parser { JsonRecord().put("kind", "bond") }
        val stages = mutableListOf<PdfImportProgress?>()
        var published = false
        val thrown = assertFailsWith<IllegalStateException> {
            readInvestmentPdf(picker(), parser, openedPdf = { throw failure }, selectedPdf = { published = true },
                progress = stages::add, timeSource = testScheduler.timeSource)
        }
        assertSame(failure, thrown); assertEquals(0, parser.calls); assertFalse(published)
        assertNull(stages.last())
    }

    @Test fun parserFailureCannotPublishBytesAndLateProgressCannotReopenFeedback() = runTest {
        val failure = IllegalArgumentException("synthetic malformed document")
        val parser = Parser { throw failure }
        val stages = mutableListOf<PdfImportProgress?>()
        var published = false
        val thrown = assertFailsWith<IllegalArgumentException> {
            readInvestmentPdf(picker(), parser, selectedPdf = { published = true }, progress = stages::add,
                timeSource = testScheduler.timeSource)
        }
        assertSame(failure, thrown); assertEquals(1, parser.calls); assertFalse(published)
        assertNull(stages.last())
        val count = stages.size
        parser.onProgress("late native callback")
        assertEquals(count, stages.size)
    }

    @Test fun cancelledNonCooperativeParserCannotPublishItsLateResult() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val parser = Parser {
            entered.complete(Unit)
            withContext(NonCancellable) { release.await(); JsonRecord().put("kind", "bond") }
        }
        val stages = mutableListOf<PdfImportProgress?>()
        var published = false
        var validated = false
        val job = launch {
            readInvestmentPdf(picker(), parser, selectedPdf = { published = true }, progress = stages::add,
                timeSource = testScheduler.timeSource, validateReview = { validated = true })
        }
        entered.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertTrue(job.isCancelled); assertTrue(parser.cancelled)
        assertFalse(validated); assertFalse(published); assertNull(stages.last())
        val count = stages.size
        parser.onProgress("late callback after cancellation")
        assertEquals(count, stages.size)
    }
}

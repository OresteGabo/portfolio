@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class PdfPipelineCancellationTest {
    private val bytes = "%PDF-1.7 synthetic\n%%EOF".encodeToByteArray()
    private class Parser(private val result: suspend () -> JsonRecord) : InvestmentDocumentParser {
        override var onProgress: (String) -> Unit = {}
        var cancelled = false
        override suspend fun parse(pdf: ByteArray) = result()
        override fun cancel() { cancelled = true }
    }
    private fun picker() = object : PdfPicker {
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return bytes }
    }

    @Test fun cancelledParserCompletionCannotValidatePublishOrReturnAReview() = runTest {
        val reply = CompletableDeferred<JsonRecord>()
        val parser = Parser { withContext(NonCancellable) { reply.await() } }
        val calls = mutableListOf<String>()
        val stages = mutableListOf<PdfImportProgress?>()
        val request = launch {
            readInvestmentPdf(picker(), parser, selectedPdf = { calls += "selected" },
                validateReview = { calls += "validated" }, progress = stages::add, timeSource = testScheduler.timeSource)
            calls += "returned"
        }
        runCurrent(); advanceTimeBy(100); runCurrent()
        val beforeCancellation = stages.size
        request.cancel(); parser.onProgress("Already cancelled")
        val afterLateProgress = stages.size
        reply.complete(JsonRecord().put("kind", "bond")); request.join()
        assertTrue(request.isCancelled)
        assertTrue(calls.isEmpty(), "Cancelled work reached: $calls")
        assertEquals(beforeCancellation, afterLateProgress)
        assertTrue(parser.cancelled); assertNull(stages.last())
        val finished = stages.size; parser.onProgress("Late progress"); assertEquals(finished, stages.size)
    }

    @Test fun cancellationDuringValidationPreventsSelectedBytesAndReturn() = runTest {
        val parser = Parser { JsonRecord().put("kind", "bond") }
        var selected = false; var returned = false
        val request = launch {
            val owner = currentCoroutineContext().job
            readInvestmentPdf(picker(), parser, selectedPdf = { selected = true },
                validateReview = { owner.cancel() }, timeSource = testScheduler.timeSource)
            returned = true
        }
        request.join()
        assertTrue(request.isCancelled); assertTrue(parser.cancelled)
        assertFalse(selected); assertFalse(returned)
    }

    @Test fun cancelledOpeningCallbackPreventsParserInvocation() = runTest {
        var parsed = false
        val parser = Parser { parsed = true; JsonRecord() }
        val request = launch {
            val owner = currentCoroutineContext().job
            readInvestmentPdf(picker(), parser, openedPdf = { owner.cancel() }, timeSource = testScheduler.timeSource)
        }
        request.join()
        assertTrue(request.isCancelled); assertFalse(parsed); assertTrue(parser.cancelled)
    }

    @Test fun cancellationInTheSelectedCallbackCannotReturnAReview() = runTest {
        val parser = Parser { JsonRecord().put("kind", "bond") }
        var returned = false
        val request = launch {
            val owner = currentCoroutineContext().job
            readInvestmentPdf(picker(), parser, selectedPdf = { owner.cancel() }, timeSource = testScheduler.timeSource)
            returned = true
        }
        request.join()
        assertTrue(request.isCancelled); assertTrue(parser.cancelled); assertFalse(returned)
    }

    @Test fun cancellationDoesNotAffectAnIndependentConcurrentImport() = runTest {
        val firstReply = CompletableDeferred<JsonRecord>(); val secondReply = CompletableDeferred<JsonRecord>()
        val first = Parser { firstReply.await() }; val second = Parser { secondReply.await() }
        val firstStages = mutableListOf<PdfImportProgress?>(); val secondStages = mutableListOf<PdfImportProgress?>()
        var selected = false
        val cancelled = launch { readInvestmentPdf(picker(), first, progress = firstStages::add, timeSource = testScheduler.timeSource) }
        val successful = async { readInvestmentPdf(picker(), second, selectedPdf = { selected = true }, progress = secondStages::add, timeSource = testScheduler.timeSource) }
        runCurrent(); advanceTimeBy(100); runCurrent()
        cancelled.cancelAndJoin()
        secondReply.complete(JsonRecord().put("kind", "bond"))
        assertEquals("bond", successful.await()?.text("kind"))
        assertTrue(first.cancelled); assertFalse(second.cancelled); assertTrue(selected)
        assertNull(firstStages.last()); assertNull(secondStages.last())
    }


    @Test fun latePickerSelectionDoesNotPublishOpeningProgressOrInvokeTheParser() = runTest {
        val reply = CompletableDeferred<Unit>()
        val picker = object : PdfPicker {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray {
                withContext(NonCancellable) { reply.await() }
                onSelected()
                return bytes
            }
        }
        var parsed = false; var opened = false
        val parser = Parser { parsed = true; JsonRecord() }
        val stages = mutableListOf<PdfImportProgress?>()
        val request = launch {
            readInvestmentPdf(picker, parser, openedPdf = { opened = true }, progress = stages::add, timeSource = testScheduler.timeSource)
        }
        runCurrent(); request.cancel(); reply.complete(Unit); request.join()
        assertTrue(request.isCancelled); assertFalse(parsed); assertFalse(opened)
        assertEquals(listOf<PdfImportProgress?>(null), stages)
    }

}

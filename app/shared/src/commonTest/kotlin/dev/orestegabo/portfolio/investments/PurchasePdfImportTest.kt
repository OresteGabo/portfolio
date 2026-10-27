package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PurchasePdfImportTest {
    private class Actions(private val bytes: ByteArray?) : NativeActions {
        var picks = 0
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? { picks++; if (bytes != null) onSelected(); return bytes }
        override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
        override fun openUrl(url: String) {}
        override fun shareText(text: String) {}
    }
    private class Engine(private val review: JsonRecord) : InvestmentEngine {
        override var onProgress: (String) -> Unit = {}
        var reads = 0
        override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord {
            assertEquals("pdf", command)
            reads++
            onProgress("Reading PDF…")
            return review
        }
        override fun cancel() {}
        override fun destroy() {}
    }
    @Test fun cancelReturnsToTheCallingScreenWithoutParsingOrCreatingAReview() = runTest {
        val actions = Actions(null)
        val engine = Engine(JsonRecord().put("kind", "bond"))
        assertNull(readPurchasePdf(actions, engine, bondOnly = true))
        assertEquals(1, actions.picks)
        assertEquals(0, engine.reads)
    }
    @Test fun onePickerSelectionDirectlyProducesTheConfirmationReviewAndReleasesProgress() = runTest {
        val review = JsonRecord().put("kind", "bond")
        val actions = Actions(byteArrayOf(1))
        val engine = Engine(review)
        val progress = mutableListOf<PdfImportProgress?>()
        assertSame(review, readPurchasePdf(actions, engine, bondOnly = true, progress = progress::add))
        assertEquals(1, actions.picks)
        assertEquals(1, engine.reads)
        assertEquals(listOf(PdfImportStage.OPENING, PdfImportStage.EXTRACTING, PdfImportStage.CHECKING), progress.filterNotNull().map { it.stage }.distinct())
        assertNull(progress.last())
        val before = progress.size
        engine.onProgress("Late callback")
        assertEquals(before, progress.size)
    }
    @Test fun fastCompletionKeepsTheSpinnerVisibleBeforeDeliveringTheReview() = runTest {
        val progress = mutableListOf<PdfImportProgress?>()
        var reviewed = false
        val job = launch {
            readPurchasePdf(Actions(byteArrayOf(1)), Engine(JsonRecord().put("kind", "bond")), progress = progress::add)
            reviewed = true
        }
        runCurrent()
        assertEquals(PdfImportStage.OPENING, progress.last()?.stage)
        advanceTimeBy(200); runCurrent()
        assertEquals(PdfImportStage.CHECKING, progress.last()?.stage)
        assertFalse(reviewed)
        advanceTimeBy(600); runCurrent()
        assertFalse(reviewed)
        assertNotNull(progress.last())
        advanceUntilIdle(); job.join()
        assertTrue(reviewed)
        assertNull(progress.last())
    }
    @Test fun pendingUploadAcceptsPublicResultsWithoutTreatingThemAsAPersonalConfirmation() = runTest {
        val review = JsonRecord().put("kind", "bond-issuance")
        assertSame(review, readPurchasePdf(Actions(byteArrayOf(1)), Engine(review), bondOnly = true))
        assertSame(review, readPurchasePdf(Actions(byteArrayOf(1)), Engine(review), bondOnly = true,
            expectedStatement = JsonRecord().put("kind", "bond")))
        assertFailsWith<IllegalArgumentException> {
            readPurchasePdf(Actions(byteArrayOf(1)), Engine(review), expectedStatement = JsonRecord().put("kind", "equity"))
        }
    }
    @Test fun pendingUploadStillRejectsShareDocumentsAndCleansUpProgress() = runTest {
        val engine = Engine(JsonRecord().put("kind", "equity"))
        val progress = mutableListOf<PdfImportProgress?>()
        assertFailsWith<IllegalArgumentException> {
            readPurchasePdf(Actions(byteArrayOf(1)), engine, bondOnly = true, progress = progress::add)
        }
        assertNull(progress.last())
        val before = progress.size
        engine.onProgress("Late callback")
        assertEquals(before, progress.size)
    }
}

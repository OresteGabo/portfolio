@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.*
import kotlin.test.*

class PdfImportPipelineTest {
    private val bytes = "%PDF-1.7 synthetic\n%%EOF".encodeToByteArray()
    private fun picker() = object : PdfPicker {
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return bytes }
    }
    private class Parser(private val review: JsonRecord) : InvestmentDocumentParser {
        override var onProgress: (String) -> Unit = {}
        var cancelled = false
        override suspend fun parse(pdf: ByteArray): JsonRecord = review
        override fun cancel() { cancelled = true }
    }

    @Test fun theSamePipelineRunsEachSupportedAccountVariantWithItsSuppliedPolicy() = runTest {
        for (kind in listOf("bond", "equity", "statement", "bond-issuance")) {
            val review = JsonRecord().put("kind", kind)
            val calls = mutableListOf<String>()
            val result = readInvestmentPdf(picker(), Parser(review), openedPdf = { calls += "opened" },
                selectedPdf = { assertSame(bytes, it); calls += "selected" }, timeSource = testScheduler.timeSource,
                validateReview = { assertSame(review, it); validatePurchasePdfReview(it, false, null); calls += "validated" })
            assertSame(review, result)
            assertEquals(listOf("opened", "validated", "selected"), calls)
        }
    }

    @Test fun policyRejectionPreventsSelectedBytePublicationAndReleasesParserProgress() = runTest {
        val parser = Parser(JsonRecord().put("kind", "equity"))
        val stages = mutableListOf<PdfImportProgress?>()
        var selected = false
        assertFailsWith<IllegalArgumentException> {
            readInvestmentPdf(picker(), parser, selectedPdf = { selected = true }, progress = stages::add,
                timeSource = testScheduler.timeSource, validateReview = { validatePurchasePdfReview(it, true, null) })
        }
        assertFalse(selected); assertFalse(parser.cancelled)
        assertTrue(stages.any { it?.stage == PdfImportStage.CHECKING })
        assertNull(stages.last())
        val finished = stages.size
        parser.onProgress("Late progress")
        assertEquals(finished, stages.size)
    }

    @Test fun rawGuestProposalCanBeValidatedAfterTheExistingOwnershipBoundary() = runTest {
        val review = JsonRecord().put("kind", "statement")
        var selected = false
        val stages = mutableListOf<PdfImportProgress?>()
        assertSame(review, readInvestmentPdf(picker(), Parser(review), selectedPdf = { selected = true },
            progress = stages::add, timeSource = testScheduler.timeSource))
        assertTrue(selected); assertNull(stages.last())
        assertFailsWith<IllegalArgumentException> { validateGuestPdfReview(review) }
    }

    @Test fun cancellationInThePolicyStillCancelsTheParserAndNeverPublishesBytes() = runTest {
        val parser = Parser(JsonRecord().put("kind", "bond"))
        var selected = false
        var progress: PdfImportProgress? = null
        assertFailsWith<CancellationException> {
            readInvestmentPdf(picker(), parser, selectedPdf = { selected = true }, progress = { progress = it },
                timeSource = testScheduler.timeSource, validateReview = { throw CancellationException("Cancelled") })
        }
        assertTrue(parser.cancelled); assertFalse(selected); assertNull(progress)
    }
}

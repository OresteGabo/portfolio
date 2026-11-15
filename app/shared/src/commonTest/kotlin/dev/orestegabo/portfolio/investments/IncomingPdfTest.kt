package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class IncomingPdfTest {
    private val bytes = "%PDF-1.7 synthetic\n%%EOF".encodeToByteArray()

    @Test fun nativeFacadePreservesMailboxPreparationDeliveryAndErrorBehavior() {
        NativePdfInbox.dismiss(); NativePdfInbox.dismissError()
        try {
            assertTrue(NativePdfInbox.offer(bytes, "composition-prepare", "allocation.pdf", true))
            val first = assertNotNull(NativePdfInbox.pending)
            assertEquals("allocation.pdf", first.fileName)
            assertTrue(NativePdfInbox.requiresPreparation)
            assertNull(NativePdfInbox.take(first.id))
            NativePdfInbox.continueImport(first.id + 1)
            assertTrue(NativePdfInbox.requiresPreparation)
            assertFalse(NativePdfInbox.offer(bytes, "composition-blocked"))
            assertNotNull(NativePdfInbox.error)
            NativePdfInbox.dismissError(); assertNull(NativePdfInbox.error)
            NativePdfInbox.continueImport(first.id)
            assertSame(first, NativePdfInbox.take(first.id))
            assertNull(NativePdfInbox.pending)
            assertFalse(NativePdfInbox.offer(bytes, "composition-prepare"))
            assertTrue(NativePdfInbox.offer(bytes, "composition-defaults"))
            assertEquals("Shared PDF", NativePdfInbox.pending?.fileName)
            assertFalse(NativePdfInbox.requiresPreparation)
            NativePdfInbox.dismiss()
            assertNull(NativePdfInbox.pending)
            assertFailsWith<IllegalArgumentException> { NativePdfInbox.offer(byteArrayOf(), "composition-invalid") }
            NativePdfInbox.reject("private native error")
            assertEquals(GENERAL_ERROR_MESSAGE, NativePdfInbox.error)
        } finally {
            NativePdfInbox.dismiss(); NativePdfInbox.dismissError()
        }
    }

    @Test fun oneFileSurvivesUntilItIsTakenAndCannotBeOverwritten() {
        val inbox = PdfImportInbox()
        assertTrue(inbox.offer(bytes, "first-delivery"))
        val first = assertNotNull(inbox.pending)
        assertNull(inbox.take(first.id + 1))
        assertFalse(inbox.offer(bytes + byteArrayOf(1), "second-delivery"))
        assertSame(first, inbox.pending)
        assertEquals("Finish the waiting PDF before sharing another one.", inbox.error)
        assertContentEquals(bytes, assertNotNull(inbox.take(first.id)).bytes)
        assertNull(inbox.pending)
        assertFalse(inbox.offer(bytes, "first-delivery")) // Activity recreation is not a new share.
        assertTrue(inbox.offer(bytes, "new-delivery")) // Explicitly sharing again remains possible.
        assertNull(inbox.error)
        inbox.dismiss()
        assertNull(inbox.pending)
    }

    @Test fun sharedPdfWaitsForExplicitContinuationAndCancelClearsIt() {
        val inbox = PdfImportInbox()
        assertTrue(inbox.offer(bytes, "prepare", prepareFirst = true))
        val pdf = assertNotNull(inbox.pending)
        assertTrue(inbox.requiresPreparation)
        assertNull(inbox.take(pdf.id))
        inbox.continueImport(pdf.id + 1)
        assertTrue(inbox.requiresPreparation)
        inbox.continueImport(pdf.id)
        assertContentEquals(bytes, assertNotNull(inbox.take(pdf.id)).bytes)
        assertTrue(inbox.offer(bytes, "cancel", prepareFirst = true))
        inbox.dismiss()
        assertNull(inbox.pending)
        assertFalse(inbox.requiresPreparation)
    }

    @Test fun emptyOversizedAndNonPdfFilesAreRejectedBeforeParsing() {
        val inbox = PdfImportInbox()
        for (bad in listOf(byteArrayOf(), "not a PDF".encodeToByteArray(), ByteArray(MAX_PDF_BYTES + 1))) {
            assertFailsWith<IllegalArgumentException> { inbox.offer(bad, "bad") }
            assertNull(inbox.pending)
        }
        inbox.reject("private raw platform exception")
        assertEquals(GENERAL_ERROR_MESSAGE, inbox.error)
    }

    @Test fun everySupportedTypeUsesTheExistingParserWithoutOpeningThePickerAgain() = runTest {
        val original = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = error("Do not reopen the picker")
            override suspend fun savePdf(fileName: String, bytes: ByteArray): Boolean = error("Do not upload or export during parsing")
            override fun openUrl(url: String) = error("No remote URL")
            override fun shareText(text: String) = error("No sharing during parsing")
        }
        for (kind in listOf("bond", "equity", "statement", "bond-issuance")) {
            var reads = 0
            val engine = object : InvestmentEngine {
                override var onProgress: (String) -> Unit = {}
                override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord {
                    assertEquals("pdf", command)
                    assertContentEquals(bytes, pdf)
                    reads++
                    return JsonRecord().put("kind", kind)
                }
                override fun cancel() {}
                override fun destroy() {}
            }
            val result = readPurchasePdf(ReceivedPdfActions(original, bytes), engine)
            assertEquals(kind, assertNotNull(result).text("kind"))
            assertEquals(1, reads)
        }
    }
}

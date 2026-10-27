package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class IncomingPdfTest {
    private val bytes = "%PDF-1.7 synthetic\n%%EOF".encodeToByteArray()

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

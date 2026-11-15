package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PdfPickerContractTest {
    private val bytes = "%PDF-1.7 synthetic\n%%EOF".encodeToByteArray()
    private class Host : NativeActions {
        var shares = 0
        override val interactingWithSystem = true
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = error("Never reopen selection")
        override suspend fun pickPrivatePdf(onSelected: () -> Unit): ByteArray? = error("Never reopen private selection")
        override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
        override fun openUrl(url: String) {}
        override fun shareText(text: String) { shares++ }
    }

    private suspend fun assertSelectedDocumentContract(picker: PdfPicker) {
        for (privateSelection in listOf(false, true)) {
            var callbacks = 0
            val result = if (privateSelection) picker.pickPrivatePdf { callbacks++ } else picker.pickPdf { callbacks++ }
            assertContentEquals(bytes, result)
            assertEquals(1, callbacks)
            assertEquals("contract.pdf", picker.pickedPdfName)
        }
    }

    @Test fun defaultPrivateSelectionAndReceivedDocumentAdapterSatisfyTheSameSelectionContract() = runTest {
        val direct = object : PdfPicker {
            override val pickedPdfName = "contract.pdf"
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return bytes }
        }
        assertSelectedDocumentContract(direct)
        assertSelectedDocumentContract(ReceivedPdfActions(Host(), bytes, "contract.pdf"))
    }

    @Test fun cancelledSelectionReturnsNullWithoutReportingASelectedFile() = runTest {
        val picker = object : PdfPicker {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
        }
        var callbacks = 0
        assertNull(picker.pickPdf { callbacks++ })
        assertNull(picker.pickPrivatePdf { callbacks++ })
        assertEquals(0, callbacks)
        assertNull(picker.pickedPdfName)
    }

    @Test fun receivedAdapterKeepsUnrelatedHostCapabilitiesAndTheirResults() = runTest {
        val host = Host()
        val adapter: NativeActions = ReceivedPdfActions(host, bytes, "contract.pdf")
        assertTrue(adapter.interactingWithSystem)
        assertFalse(adapter.savePdf("report.pdf", bytes))
        adapter.shareText("text")
        assertEquals(1, host.shares)
    }
}

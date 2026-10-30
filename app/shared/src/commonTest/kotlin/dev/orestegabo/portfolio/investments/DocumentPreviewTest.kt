@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlin.test.*

class DocumentPreviewTest {
    private class Fixture {
        val store = ViewModelStore()
        var status = HttpStatusCode.OK
        var delayed = false
        var reads = 0
        var writes = 0
        val pdf = "%PDF-1.7 exact original bytes\n%%EOF".encodeToByteArray()
        val document = JsonRecord().put("id", "private-pdf").put("label", "Original contract")
            .put("originalFileName", "contract.pdf").put("downloadUrl", "/api/bonds/documents/private-pdf")
        val history = mutableMapOf<String, String>()
    }
    private fun TestScope.model(f: Fixture): PortfolioModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return PortfolioModel(object : ServerSettings {
            override fun readOrigin() = "https://reader.example.test"
            override fun writeOrigin(origin: String) {}
            override fun writeImportHistory(account: String, history: String) { f.history[account] = history }
        }, reportWorkDispatcher = dispatcher, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request ->
                    if (request.method != HttpMethod.Get) f.writes++
                    when (request.url.encodedPath) {
                        "/api/bonds/auth/session" -> respond("""{"authenticated":true,"user":{"id":"reader-user","email":"fixture@example.test"}}""")
                        "/api/bonds/mobile-overview" -> respond("{}", HttpStatusCode.NotFound)
                        "/api/bonds/documents" -> respond(JsonRecord().put("documents", JsonList(listOf(f.document))).toString())
                        "/api/bonds/documents/private-pdf" -> {
                            f.reads++
                            if (f.delayed) delay(60_000)
                            respond(f.pdf, f.status)
                        }
                        "/api/bonds/purchases" -> respond("""{"purchases":[]}""")
                        "/api/bonds/equities" -> respond("""{"holdings":[]}""")
                        else -> respond("{}")
                    }
                }
            }))
        }).also { f.store.put("model", it) }
    }
    @Test fun openingDownloadsExactPrivateBytesWithoutWritingAnything() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle(); m.openDocumentPreview(f.document); advanceUntilIdle()
            assertContentEquals(f.pdf, m.documentPreview?.bytes)
            assertEquals("contract.pdf", m.documentPreview?.fileName)
            assertEquals(1, f.reads); assertEquals(0, f.writes)
            m.closeDocumentPreview(); assertNull(m.documentPreview)
            assertTrue(f.history.isEmpty())
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun explicitInvestmentProofUsesTheExistingPrivateViewerWithoutLibraryMetadata() = runTest {
        val f = Fixture(); val model = model(f)
        try {
            advanceUntilIdle()
            val evidence = investmentEvidence(JsonRecord().put("contractDocumentId", "private-pdf"), emptyList()).single()
            model.openDocumentPreview(evidence.document); advanceUntilIdle()
            assertContentEquals(f.pdf, model.documentPreview?.bytes)
            assertEquals(1, f.reads); assertEquals(0, f.writes)
            model.closeDocumentPreview(); assertNull(model.documentPreview)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun remoteOrTraversalUrlsNeverLeaveTheAuthenticatedApi() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle()
            for (url in listOf("https://other.example.test/private.pdf", "/api/bonds/documents/../auth/session", "/api/bonds/documents/a?token=secret")) {
                m.openDocumentPreview(JsonRecord(f.document.toString()).put("downloadUrl", url)); advanceUntilIdle()
                assertNull(m.documentPreview?.bytes); assertNotNull(m.error)
                m.closeDocumentPreview()
            }
            assertEquals(0, f.reads); assertEquals(0, f.writes)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun expiredSessionDropsThePreviewAndPrivateAccount() = runTest {
        val f = Fixture().apply { status = HttpStatusCode.Unauthorized }; val m = model(f)
        try {
            advanceUntilIdle(); m.openDocumentPreview(f.document); advanceUntilIdle()
            assertNull(m.documentPreview); assertNull(m.user); assertNotNull(m.error)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun backCancelsAnUnfinishedDownloadAndCannotReopenItLater() = runTest {
        val f = Fixture().apply { delayed = true }; val m = model(f)
        try {
            advanceUntilIdle(); m.openDocumentPreview(f.document); runCurrent()
            assertNotNull(m.documentPreview); assertTrue(m.busy)
            m.closeDocumentPreview(); advanceUntilIdle()
            assertNull(m.documentPreview); assertFalse(m.busy); assertEquals(0, f.writes)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun aFailedDownloadCanBeRetriedWithoutCreatingADocument() = runTest {
        val f = Fixture().apply { status = HttpStatusCode.ServiceUnavailable }; val m = model(f)
        try {
            advanceUntilIdle(); m.openDocumentPreview(f.document); advanceUntilIdle()
            assertNotNull(m.error); assertNull(m.documentPreview?.bytes)
            f.status = HttpStatusCode.OK
            m.openDocumentPreview(f.document); advanceUntilIdle()
            assertContentEquals(f.pdf, m.documentPreview?.bytes)
            assertNull(m.error); assertEquals(2, f.reads); assertEquals(0, f.writes)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun generatedAnnualPreviewIsARealPdfWithoutAStoredDocumentOrUpload() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle()
            m.openDocumentPreview(JsonRecord().put("id", "generated-annual-2025").put("annualReportYear", 2025)
                .put("originalFileName", "Struct-Capital-Annual-2025.pdf")); advanceUntilIdle()
            val bytes = assertNotNull(m.documentPreview?.bytes)
            assertTrue(bytes.decodeToString().startsWith("%PDF-"))
            assertTrue(bytes.decodeToString().contains("2025-01-01"))
            assertTrue(bytes.decodeToString().contains("2025-12-31"))
            assertEquals(0, f.reads); assertEquals(0, f.writes)
            assertNull(m.annualReportYear); assertNull(m.reportProgress)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun zoomRegionsUseTheReaderViewportAndExpandedPageHeight() {
        assertEquals(PdfPageRegion(0.0, .0, 1.0, .5), pdfViewportRegion(1f, 0f, 1000, 1500f, 0, 750))
        assertEquals(PdfPageRegion(.375, .25, .25, .125), pdfViewportRegion(4f, 0f, 1000, 1500f, 1500, 750))
        assertEquals(PdfPageRegion(0.0, .875, .25, .125), pdfViewportRegion(4f, 1500f, 1000, 1500f, 5250, 750))
        // A short landscape page grows into the unused reader area instead of remaining a short box.
        assertEquals(PdfPageRegion(.25, 0.0, .5, 1.0), pdfViewportRegion(2f, 0f, 1000, 500f, 0, 1500))
        assertFails { PdfPageRegion(.9, .0, .5, 1.0) }
        assertFails { PdfPageRegion(Double.NaN) }
    }
    @Test fun extremePageSizesCannotRequestUnboundedBitmaps() {
        for ((w, h) in listOf(1.0 to 100000.0, 100000.0 to 1.0, 595.0 to 842.0)) {
            val size = pdfPageRenderSize(Int.MAX_VALUE, w, h)
            assertTrue(size.first in 1..4096 && size.second in 1..4096)
            assertTrue(size.first.toLong() * size.second <= 3_000_000)
        }
        assertFails { pdfPageRenderSize(1000, 0.0, 1000.0) }
        assertFails { pdfPageRenderSize(1000, Double.NaN, 1000.0) }
    }
}

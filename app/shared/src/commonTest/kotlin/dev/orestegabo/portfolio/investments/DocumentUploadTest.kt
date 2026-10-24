package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.io.encoding.Base64
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DocumentUploadTest {
    @Test fun confirmationWaitsForPdfUploadAndRetryKeepsOnePendingPurchase() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        var failUpload = true
        var failConfirmation = false
        var uploaded = false
        var saved = false
        var uploads = 0
        val events = mutableListOf<String>()
        val bytes = "%PDF-1.7 synthetic\n%%EOF".encodeToByteArray()
        val review = JsonRecord().put("kind", "bond")
        val model = PortfolioModel(object : ServerSettings {
            override fun readOrigin() = "https://synthetic.example.test"
            override fun writeOrigin(origin: String) {}
        }, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request -> when (request.url.encodedPath) {
                    "/api/bonds/auth/session" -> respond("""{"authenticated":true,"user":{"id":"test"}}""")
                    "/api/bonds/mobile-overview" -> respond("{}", HttpStatusCode.NotFound)
                    "/api/bonds/purchases" -> respond("""{"purchases":[]}""")
                    "/api/bonds/equities" -> respond("""{"holdings":[]}""")
                    "/api/bonds/documents" -> if (request.method == HttpMethod.Post) {
                        uploads++; events += "upload"
                        val body = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        assertContentEquals(bytes, Base64.decode(body.text("content")))
                        if (failUpload) respond("""{"error":"Upload failed"}""", HttpStatusCode.ServiceUnavailable)
                        else { uploaded = true; respond("""{"documentId":"synthetic-document"}""") }
                    } else respond(if (uploaded) """{"documents":[{"id":"synthetic-document"}]}""" else """{"documents":[]}""")
                    "/api/bonds/purchases/pending-id" -> {
                        assertTrue(uploaded)
                        assertEquals(HttpMethod.Put, request.method)
                        events += "confirm"
                        if (failConfirmation) respond("""{"error":"Save failed"}""", HttpStatusCode.ServiceUnavailable)
                        else { saved = true; respond("{}") }
                    }
                    else -> respond("{}")
                } }
            }))
        })
        store.put("model", model)
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return bytes }
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) {}
            override fun shareText(text: String) {}
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?) = review
            override fun cancel() {}
            override fun destroy() {}
        }
        val input = JsonRecord().put("bondName", "Synthetic").put("issuer", "BNR").put("purchaseDate", "2026-10-23")
        try {
            advanceUntilIdle()
            model.importPurchasePdf(actions, engine) { assertSame(review, it) }
            advanceUntilIdle()
            var closed = false
            model.saveImportedPurchase(review, input, "pending-id") { closed = true }
            advanceUntilIdle()
            assertFalse(saved); assertFalse(closed); assertNotNull(model.error); assertNull(model.pdfImportProgress)
            assertEquals(listOf("upload"), events)
            failUpload = false; failConfirmation = true
            model.saveImportedPurchase(review, input, "pending-id") { closed = true }
            advanceUntilIdle()
            assertFalse(saved); assertFalse(closed)
            assertEquals(2, uploads)
            failConfirmation = false
            model.saveImportedPurchase(review, input, "pending-id") { closed = true }
            advanceUntilIdle()
            assertTrue(saved); assertTrue(closed)
            assertEquals(2, uploads) // Retry confirmation reuses the stored PDF.
            assertEquals("synthetic-document", model.documents.single().text("id"))
            assertNull(model.pdfImportProgress)
            model.saveImportedPurchase(review, input, "pending-id") {}
            advanceUntilIdle()
            assertEquals(2, uploads) // The completed import no longer retains private PDF bytes.
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}

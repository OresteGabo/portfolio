@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

class ImportConversationModelTest {
    private class Settings : ServerSettings {
        val history = mutableMapOf<String, String>()
        override fun readOrigin() = "https://imports.example.test"
        override fun writeOrigin(origin: String) {}
        override fun readImportHistory(account: String) = history[account]
        override fun writeImportHistory(account: String, history: String) { this.history[account] = history }
    }
    private class Fixture {
        val settings = Settings()
        val store = ViewModelStore()
        var account = "alice"
        var uploads = 0
        var writes = 0
        var lostResponse = false
        var failParse = false
        var failStatement = false
        var statementWrites = 0
        var savedPurchase: JsonRecord? = null
        val bytes = "%PDF-1.7 synthetic import\n%%EOF".encodeToByteArray()
        val fields = JsonRecord().put("issuer", "Government of Rwanda").put("bondName", "Test bond").put("isin", "RWTEST000001/MN")
            .put("currency", "RWF").put("purchaseDate", "2026-10-23").put("settlementDate", "2026-10-23")
            .put("maturityDate", "2046-09-28").put("firstCouponDate", "2027-04-23").put("tenorYears", 20)
            .put("faceValue", 1000000).put("pricePercent", 100).put("couponRate", .12).put("feesPaid", 11000)
        val review = JsonRecord().put("kind", "bond").put("fields", fields).put("missingFields", JsonList()).put("warnings", JsonList())
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord {
                if (failParse) throw IllegalArgumentException("Could not read this PDF.")
                return review
            }
            override fun cancel() {}
            override fun destroy() {}
        }
        var picks = 0
        fun actions(data: ByteArray = bytes) = object : NativeActions {
            override val pickedPdfName = "receipt.pdf"
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { picks++; onSelected(); return data }
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) {}
            override fun shareText(text: String) {}
        }
    }
    private fun TestScope.model(f: Fixture): PortfolioModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return PortfolioModel(f.settings, importWorkDispatcher = dispatcher, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request ->
                    fun body() = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                    when (request.url.encodedPath) {
                        "/api/bonds/auth/session" -> respond("""{"authenticated":true,"user":{"id":"${f.account}","email":"demo@example.test"}}""")
                        "/api/bonds/mobile-overview" -> respond("{}", HttpStatusCode.NotFound)
                        "/api/bonds/purchases" -> if (request.method == HttpMethod.Post) {
                            f.writes++
                            f.savedPurchase = body().put("id", "purchase-id")
                            respond("""{"purchase":{"id":"purchase-id"}}""", if (f.lostResponse) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK)
                        } else respond(JsonRecord().put("purchases", JsonList(listOfNotNull(f.savedPurchase))).toString())
                        "/api/bonds/documents" -> if (request.method == HttpMethod.Post) {
                            f.uploads++
                            respond("""{"documentId":"doc-id"}""")
                        } else respond("""{"documents":[{"id":"doc-id","category":"Bond confirmation"}]}""")
                        "/api/bonds/statement-items" -> if (request.method == HttpMethod.Post) {
                            f.statementWrites++
                            respond("""{"rows":[{"state":"recorded"},{"state":"missing"}]}""", if (f.failStatement) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK)
                        } else respond("""{"items":[]}""")
                        else -> respond("{}")
                    }
                }
            }))
        }).also { f.store.put("model", it) }
    }
    @Test fun aLostSaveResponseRetriesTheSameDocumentWithoutCreatingAnotherPurchase() = runTest {
        val f = Fixture().apply { lostResponse = true }
        val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            assertEquals(ImportState.REVIEW, m.importHistory.single().state)
            val input = importedPurchaseInput(f.review)
            m.saveImportedPurchase(f.review, input, null) {}; advanceUntilIdle()
            assertEquals(ImportState.FAILED, m.importHistory.single().state)
            assertEquals("doc-id", m.importHistory.single().documentId)
            assertEquals(1, f.writes)
            f.lostResponse = false
            m.saveImportedPurchase(f.review, input, null) {}; advanceUntilIdle()
            assertEquals(1, f.uploads); assertEquals(1, f.writes)
            val receipt = m.importHistory.single()
            assertEquals(ImportState.SAVED, receipt.state)
            assertEquals("purchase-id", receipt.purchaseId)
            assertEquals(2, receipt.attempts)
            assertEquals(ImportState.FAILED, receipt.events.single().state)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun historySurvivesReopeningAndIsSeparatedByAccount() = runTest {
        val f = Fixture()
        var m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            m.saveImportedPurchase(f.review, importedPurchaseInput(f.review), null) {}; advanceUntilIdle()
            f.store.clear()
            m = model(f); advanceUntilIdle()
            assertEquals(ImportState.SAVED, m.importHistory.single().state)
            assertEquals("doc-id", m.importHistory.single().documentId)
            f.store.clear(); f.account = "bob"
            m = model(f); advanceUntilIdle()
            assertTrue(m.importHistory.isEmpty())
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun failedParsingKeepsItsErrorAndRetriesBytesWithoutAnotherPicker() = runTest {
        val f = Fixture().apply { failParse = true }
        val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            val id = m.importHistory.single().id
            m.error = null
            assertEquals("Could not read this PDF.", m.importHistory.single().error)
            f.failParse = false
            m.retryImport(id, f.actions(), f.engine) {}; advanceUntilIdle()
            assertEquals(1, f.picks)
            assertEquals(id, m.importHistory.single().id)
            assertEquals(ImportState.REVIEW, m.importHistory.single().state)
            assertEquals(1, m.importHistory.single().events.size)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun resumedImportRejectsADifferentFileBeforeAnyUpload() = runTest {
        val f = Fixture().apply { failParse = true }
        var m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            val id = m.importHistory.single().id
            f.store.clear()
            m = model(f); advanceUntilIdle()
            f.failParse = false
            m.retryImport(id, f.actions(f.bytes + byteArrayOf(1)), f.engine) {}; advanceUntilIdle()
            assertEquals(0, f.uploads)
            assertEquals("This is a different PDF. Start a new import for this file.", m.importHistory.single().error)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun statementRetryReusesItsPdfAndReconcilesRemindersWithoutCreatingPurchases() = runTest {
        val f = Fixture().apply { failStatement = true }
        f.review.put("kind", "statement").put("toDate", "2026-10-23").put("rows", JsonList(listOf(JsonRecord().put("kind", "bond"), JsonRecord().put("kind", "equity"))))
        val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            m.saveImportedStatement(f.review) {}; advanceUntilIdle()
            assertEquals(ImportState.FAILED, m.importHistory.single().state)
            f.failStatement = false
            m.saveImportedStatement(f.review) {}; advanceUntilIdle()
            assertEquals(1, f.uploads); assertEquals(2, f.statementWrites); assertEquals(0, f.writes)
            assertEquals(ImportOutcome.STATEMENT, m.importHistory.single().outcome)
            assertEquals(1, m.importHistory.single().recorded); assertEquals(1, m.importHistory.single().missing)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
}

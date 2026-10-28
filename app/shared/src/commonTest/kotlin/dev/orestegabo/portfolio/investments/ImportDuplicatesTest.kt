@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

class ImportDuplicatesTest {
    private class Fixture {
        val files = linkedMapOf<String, ByteArray>()
        val history = mutableMapOf<String, String>()
        val settings = object : ServerSettings {
            override fun readOrigin() = "https://duplicates.example.test"
            override fun writeOrigin(origin: String) {}
            override fun readImportHistory(account: String) = history[account]
            override fun writeImportHistory(account: String, history: String) { this@Fixture.history[account] = history }
        }
        val store = ViewModelStore()
        var account = "alice"
        var uploads = 0
        var writes = 0
        var reads = 0
        var picks = 0
        var failList = false
        var category = "Bond confirmation"
        var purchase: JsonRecord? = null
        val bytes = "%PDF-1.7 synthetic exact duplicate\n%%EOF".encodeToByteArray()
        val fields = JsonRecord().put("bondName", "Test Treasury bond").put("issuer", "BNR")
            .put("purchaseDate", "2026-10-23").put("settlementDate", "2026-10-23")
            .put("maturityDate", "2046-09-28").put("isin", "RWTEST000001/MN")
            .put("faceValue", 1_000_000).put("currency", "RWF").put("pricePercent", 100)
            .put("couponRate", .12).put("feesPaid", 11000).put("status", "active")
        val review = JsonRecord().put("kind", "bond").put("fields", fields)
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?) = review
            override fun cancel() {}
            override fun destroy() {}
        }
        fun actions(data: ByteArray = bytes, name: String = "confirmation.pdf") = object : NativeActions {
            override val pickedPdfName = name
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { picks++; onSelected(); return data }
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) {}
            override fun shareText(text: String) {}
        }
        fun document(id: String) = JsonRecord().put("id", id).put("category", category)
            .put("instrumentName", "Test Treasury bond").put("documentDate", "2026-10-23")
            .put("originalFileName", "original-name.pdf").put("downloadUrl", "/api/bonds/documents/$id")
    }
    private fun TestScope.model(f: Fixture): PortfolioModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return PortfolioModel(f.settings, importWorkDispatcher = dispatcher, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/auth/session") -> respond("""{"authenticated":true,"user":{"id":"${f.account}"}}""")
                        path.endsWith("/mobile-overview") -> respond("{}", HttpStatusCode.NotFound)
                        path.endsWith("/documents") && request.method == HttpMethod.Post -> {
                            val id = "uploaded-${++f.uploads}"
                            f.files[id] = f.bytes
                            respond(JsonRecord().put("documentId", id).toString())
                        }
                        path.endsWith("/documents") -> if (f.failList) respond("{}", HttpStatusCode.ServiceUnavailable)
                            else respond(JsonRecord().put("documents", JsonList(f.files.keys.map(f::document))).toString())
                        path.contains("/documents/") -> { f.reads++; respond(f.files[path.substringAfterLast('/')] ?: byteArrayOf(),
                            if (f.files.containsKey(path.substringAfterLast('/'))) HttpStatusCode.OK else HttpStatusCode.NotFound) }
                        (path.endsWith("/purchases") || path.endsWith("/purchases/pending")) && request.method != HttpMethod.Get -> {
                            f.writes++
                            f.purchase = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).put("id", "pending")
                            respond("""{"purchase":{"id":"pending"}}""")
                        }
                        path.endsWith("/purchases") -> respond(JsonRecord().put("purchases", JsonList(listOfNotNull(f.purchase))).toString())
                        path.endsWith("/equities") -> respond("""{"holdings":[]}""")
                        else -> respond("{}")
                    }
                }
            }))
        }).also { f.store.put("model", it) }
    }
    @Test fun selectingTheSameSavedPdfAgainLinksTheExistingDocumentWithoutAnyWrite() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            m.saveImportedPurchase(f.review, f.fields, null) {}; advanceUntilIdle()
            var reviewed = false
            m.importPurchasePdf(f.actions(name = "renamed.pdf"), f.engine) { reviewed = true }; advanceUntilIdle()
            assertFalse(reviewed)
            val entry = m.importHistory.last()
            assertEquals(ImportState.EXISTS, entry.state); assertEquals("uploaded-1", entry.documentId)
            assertEquals(1, f.uploads); assertEquals(1, f.writes); assertEquals(0, f.reads)
            assertNull(m.currentImportReview())
            assertEquals(ImportState.EXISTS, decodeImportHistory(f.history.values.single()).last().state)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun anOlderCloudDocumentIsVerifiedByBytesEvenWhenItsNameDiffers() = runTest {
        val f = Fixture().apply { files["cloud-document"] = bytes }; val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(name = "new-name.pdf"), f.engine) {}; advanceUntilIdle()
            assertEquals(ImportState.EXISTS, m.importHistory.single().state)
            assertEquals("cloud-document", m.importHistory.single().documentId)
            assertEquals(1, f.reads); assertEquals(0, f.uploads); assertEquals(0, f.writes)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun contractsStatementsAndIssuanceReportsAlsoRecognizeExistingPdfBytes() = runTest {
        for ((kind, category) in listOf("equity" to "Share contract", "statement" to "Statement", "bond-issuance" to "Bond issuance")) {
            val f = Fixture().apply { review.put("kind", kind); this.category = category; files["cloud-document"] = bytes }
            val m = model(f)
            try {
                advanceUntilIdle()
                m.importPurchasePdf(f.actions(name = "renamed.pdf"), f.engine) {}; advanceUntilIdle()
                assertEquals(ImportState.EXISTS, m.importHistory.single().state, kind)
                assertEquals(0, f.uploads); assertEquals(0, f.writes)
            } finally { f.store.clear(); Dispatchers.resetMain() }
        }
    }
    @Test fun matchingMetadataWithDifferentPdfContentsIsNotADuplicate() = runTest {
        val f = Fixture().apply { files["other-document"] = bytes + byteArrayOf(1) }; val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(name = "original-name.pdf"), f.engine) {}; advanceUntilIdle()
            assertEquals(ImportState.REVIEW, m.importHistory.single().state)
            m.saveImportedPurchase(f.review, f.fields, null) {}; advanceUntilIdle()
            assertEquals(1, f.uploads); assertEquals(1, f.writes); assertEquals(1, f.reads)
            assertEquals(ImportState.SAVED, m.importHistory.single().state)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun deletingASavedFileDoesNotLeaveAStaleDuplicateBlock() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            m.saveImportedPurchase(f.review, f.fields, null) {}; advanceUntilIdle()
            f.files.clear()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            assertEquals(ImportState.REVIEW, m.importHistory.last().state)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun anAlreadyConfirmedTargetShowsTheSavedResultWithoutAnotherReviewOrWrite() = runTest {
        val f = Fixture().apply {
            files["cloud-document"] = bytes
            purchase = JsonRecord(fields.toString()).put("id", "pending").put("confirmationDocumentId", "cloud-document")
        }
        val m = model(f)
        try {
            advanceUntilIdle()
            var reviewed = false
            m.importPurchasePdf(f.actions(), f.engine, targetPurchaseId = "pending") { reviewed = true }; advanceUntilIdle()
            assertFalse(reviewed); assertNull(m.currentImportReview())
            assertEquals(ImportState.EXISTS, m.importHistory.single().state)
            assertEquals(ImportOutcome.PURCHASE, m.importHistory.single().outcome)
            assertEquals("pending", m.importHistory.single().purchaseId)
            assertEquals(0, f.uploads); assertEquals(0, f.writes)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun aConfirmedRecordForADifferentTargetDoesNotConfirmTheRequestedPurchase() = runTest {
        val f = Fixture().apply {
            files["cloud-document"] = bytes
            purchase = JsonRecord(fields.toString()).put("id", "other").put("confirmationDocumentId", "cloud-document")
        }
        val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine, targetPurchaseId = "pending") {}; advanceUntilIdle()
            assertEquals(ImportState.REVIEW, m.importHistory.single().state)
            assertEquals("cloud-document", m.importHistory.single().documentId)
            assertEquals(0, f.uploads); assertEquals(0, f.writes)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun aLinkedFileDoesNotMakePendingCancelledOrUnknownStatusACompletedPurchase() {
        val document = JsonRecord().put("id", "doc")
        val review = JsonRecord().put("kind", "bond")
        for (status in listOf("submitted", "pending", "cancelled", "rejected", "")) {
            val record = JsonRecord().put("id", "purchase").put("status", status).put("confirmationDocumentId", "doc")
            assertNull(completedImportPurchase(document, review, listOf(record), null), status)
        }
    }
    @Test fun aConcurrentFileLinkedToAnotherPurchaseDoesNotSkipTheSelectedPendingConfirmation() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            f.files["concurrent"] = f.bytes
            f.purchase = JsonRecord(f.fields.toString()).put("id", "other").put("confirmationDocumentId", "concurrent")
            m.saveImportedPurchase(f.review, f.fields, "pending") {}; advanceUntilIdle()
            assertEquals(0, f.uploads); assertEquals(1, f.writes)
            assertEquals("pending", m.importHistory.single().purchaseId)
            assertEquals(ImportState.SAVED, m.importHistory.single().state)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun firstAddedUsesTheOriginalServerOrSuccessfulLocalSaveTimeNeverTheIssueDate() {
        val doc = JsonRecord().put("id", "doc").put("documentDate", "2020-01-01")
        val saved = ImportReceipt("first", "a.pdf", "2026-10-20T08:00:00Z", ImportState.SAVED,
            documentId = "doc", updatedAt = "2026-10-20T08:01:00Z")
        val repeated = saved.copy(id = "repeat", state = ImportState.EXISTS, updatedAt = "2026-10-28T08:00:00Z")
        assertNull(firstImportAddedTime(doc, listOf(repeated)))
        assertEquals(ImportAddedTime("2026-10-20T08:01:00Z", true), firstImportAddedTime(doc, listOf(saved, repeated)))
        doc.put("createdAt", "2026-10-19T07:00:00Z")
        assertEquals(ImportAddedTime("2026-10-19T07:00:00Z", false), firstImportAddedTime(doc, listOf(saved, repeated)))
        doc.put("createdAt", "invalid")
        assertNull(firstImportAddedTime(doc, emptyList()))
    }
    @Test fun aPendingConfirmationReusesTheExistingPdfWithoutPretendingItIsAlreadyConfirmed() = runTest {
        val f = Fixture().apply { files["cloud-document"] = bytes }; val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine, targetPurchaseId = "pending") {}; advanceUntilIdle()
            assertEquals(ImportState.REVIEW, m.importHistory.single().state)
            assertEquals("cloud-document", m.importHistory.single().documentId)
            assertEquals(0, f.writes)
            m.saveImportedPurchase(f.review, f.fields, "pending") {}; advanceUntilIdle()
            assertEquals(0, f.uploads); assertEquals(1, f.writes)
            assertEquals("cloud-document", f.purchase?.text("confirmationDocumentId"))
            assertEquals(ImportState.SAVED, m.importHistory.single().state)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun aDocumentSavedWhileReviewWasOpenIsDetectedBeforeUpload() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            f.files["concurrent-document"] = f.bytes
            m.saveImportedPurchase(f.review, f.fields, null) {}; advanceUntilIdle()
            assertEquals(ImportState.EXISTS, m.importHistory.single().state)
            assertEquals("concurrent-document", m.importHistory.single().documentId)
            assertEquals(0, f.uploads); assertEquals(0, f.writes)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun anotherAccountCannotSeeTheFirstAccountsImportReceipt() = runTest {
        val f = Fixture(); var m = model(f)
        try {
            advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            m.saveImportedPurchase(f.review, f.fields, null) {}; advanceUntilIdle()
            f.store.clear(); f.account = "bob"; f.files.clear(); f.purchase = null
            m = model(f); advanceUntilIdle()
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            assertEquals(ImportState.REVIEW, m.importHistory.single().state)
            assertEquals("", m.importHistory.single().documentId)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
    @Test fun anUnavailableDuplicateCheckDoesNotUploadOrConfirmAnything() = runTest {
        val f = Fixture(); val m = model(f)
        try {
            advanceUntilIdle(); f.failList = true
            m.importPurchasePdf(f.actions(), f.engine) {}; advanceUntilIdle()
            assertEquals(ImportState.FAILED, m.importHistory.single().state)
            assertEquals(0, f.uploads); assertEquals(0, f.writes)
        } finally { f.store.clear(); Dispatchers.resetMain() }
    }
}

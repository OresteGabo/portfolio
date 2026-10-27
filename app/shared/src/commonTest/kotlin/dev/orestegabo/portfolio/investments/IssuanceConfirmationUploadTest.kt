package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.io.encoding.Base64
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IssuanceConfirmationUploadTest {
    private fun review() = JsonRecord().put("kind", "bond-issuance").put("missingFields", JsonList())
        .put("fields", JsonRecord().put("bondName", "FXD5/2026/20YR").put("isin", "RWBO20Y000686/MN")
            .put("issuer", "Government of Rwanda").put("issuanceDate", "2026-10-21")
            .put("settlementDate", "2026-10-23").put("maturityDate", "2046-09-28").put("firstCouponDate", "2027-04-23")
            .put("couponDates", JsonList(listOf("2027-04-23", "2046-09-28"))).put("tenorYears", 20)
            .put("pricePercent", 100).put("couponRate", .1315).put("yieldRate", .1315)
            .put("amountOffered", 10_000_000_000.0).put("appliedAmount", 45_106_600_000.0)
            .put("amountSold", 10_000_000_000.0).put("bidCount", 101))
    private fun pending() = pendingBondInput(JsonRecord().put("title", "October bond").put("isin", "RWBO20Y000686/MN")
        .put("tenorYears", 20).put("settlementDate", "2026-10-23").put("maturityDate", "2046-09-28"),
        1_000_000.0, LocalDate.parse("2026-10-22")).put("id", "pending-id")

    @Test fun targetedReviewUsesRecordedAllocationAndRejectsWrongSecurityOrInconsistentCash() {
        val preview = pendingIssuanceConfirmation(review(), pending())
        assertEquals(1_000_000.0, preview.optDouble("faceValue"))
        assertEquals(11_000.0, preview.optDouble("feesPaid"))
        assertEquals(1_011_000.0, preview.optDouble("amountInvested"))
        assertEquals(.1315, preview.optDouble("couponRate"))
        for (invalid in listOf(pending().put("isin", "RWOTHER000001/MN"), pending().put("settlementDate", "2026-11-23"),
            pending().put("maturityDate", "2047-09-28"), pending().put("amountInvested", 900_000), pending().put("status", "active"))) {
            assertFailsWith<IllegalArgumentException> { pendingIssuanceConfirmation(review(), invalid) }
        }
        val premium = review().apply { put("fields", getJSONObject("fields").put("pricePercent", 102)) }
        assertFailsWith<IllegalArgumentException> { pendingIssuanceConfirmation(premium, pending()) }
    }

    @Test fun targetedUploadConfirmsSamePurchaseAndRetriesWithoutUploadingAgain() = runTest {
        exercise(failUploadFirst = true, failConfirmFirst = true)
    }
    @Test fun lostConfirmationResponseDoesNotConfirmTwice() = runTest {
        exercise(lostResponse = true)
    }
    @Test fun generalDocumentUploadDoesNotConfirmAnyPurchase() = runTest {
        exercise(targeted = false)
    }
    @Test fun changedAllocationCannotBeConfirmedWithoutANewReview() = runTest {
        exercise(changeAllocation = true)
    }

    @Test fun serverRequiringPersonalProofKeepsThePurchasePending() = runTest {
        exercise(eligible = false)
    }

    private suspend fun TestScope.exercise(failUploadFirst: Boolean = false, failConfirmFirst: Boolean = false,
        lostResponse: Boolean = false, targeted: Boolean = true, changeAllocation: Boolean = false, eligible: Boolean = true) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val original = pending()
        var current = JsonRecord(original.toString())
        val parsed = review()
        val bytes = "%PDF-1.7 synthetic issuance\n%%EOF".encodeToByteArray()
        var failUpload = failUploadFirst
        var failConfirm = failConfirmFirst
        var uploads = 0
        var confirmations = 0
        var uploaded = false
        var closed = false
        val model = PortfolioModel(object : ServerSettings {
            override fun readOrigin() = "https://synthetic.example.test"
            override fun writeOrigin(origin: String) {}
        }, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request -> when (request.url.encodedPath) {
                    "/api/bonds/auth/session" -> respond("""{"authenticated":true,"user":{"id":"test"}}""")
                    "/api/bonds/mobile-overview" -> respond("{}", HttpStatusCode.NotFound)
                    "/api/bonds/purchases" -> {
                        assertEquals(HttpMethod.Get, request.method) // Never create a second transaction.
                        respond(JsonRecord().put("purchases", JsonList(listOf(current))).toString())
                    }
                    "/api/bonds/documents" -> if (request.method == HttpMethod.Post) {
                        uploads++
                        val body = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        assertContentEquals(bytes, Base64.decode(body.text("content")))
                        assertEquals("Bond issuance", body.getJSONObject("metadata").text("category"))
                        if (failUpload) respond("{}", HttpStatusCode.ServiceUnavailable) else {
                            uploaded = true
                            current = JsonRecord(current.toString()).put("issuanceDocumentId", "shared-report").put("canConfirmWithoutPdf", eligible)
                            if (changeAllocation) current.put("faceValue", 900_000)
                            respond("""{"documentId":"shared-report"}""")
                        }
                    } else respond(JsonRecord().put("documents", JsonList(if (uploaded) listOf(JsonRecord().put("id", "shared-report")) else emptyList())).toString())
                    "/api/bonds/purchases/pending-id/confirm" -> {
                        assertTrue(uploaded)
                        assertEquals(HttpMethod.Post, request.method)
                        val body = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        assertTrue(body.optBoolean("confirmed"))
                        assertEquals("shared-report", body.text("issuanceDocumentId"))
                        assertFalse(body.has("confirmationDocumentId")) // A public report is not a personal proof.
                        confirmations++
                        if (failConfirm) respond("{}", HttpStatusCode.ServiceUnavailable) else {
                            current = JsonRecord(current.toString()).put("status", "active").put("confirmationMethod", "manual")
                                .put("pricePercent", 100).put("couponRate", .1315)
                            respond("{}", if (lostResponse) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK)
                        }
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
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?) = parsed
            override fun cancel() {}
            override fun destroy() {}
        }
        fun save() { model.saveImportedIssuance(parsed, if (targeted) original else null) { closed = true } }
        try {
            advanceUntilIdle()
            model.importPurchasePdf(actions, engine, bondOnly = true) {}
            advanceUntilIdle()
            save(); advanceUntilIdle()
            if (failUploadFirst) {
                assertFalse(closed); assertEquals(0, confirmations)
                failUpload = false
                save(); advanceUntilIdle()
            }
            if (failConfirmFirst || lostResponse) {
                assertFalse(closed); assertNotNull(model.error)
                val storedUploads = uploads
                failConfirm = false
                save(); advanceUntilIdle()
                assertEquals(storedUploads, uploads)
            }
            if (changeAllocation || !eligible) {
                assertFalse(closed); assertEquals(0, confirmations); assertNotNull(model.error)
                assertEquals("submitted", current.text("status"))
                if (!eligible) assertEquals("Use your personal PDF to confirm the final allocation and price.", model.error)
            } else {
                assertTrue(closed)
                assertEquals(if (targeted) "active" else "submitted", current.text("status"))
                assertEquals(if (!targeted) 0 else if (failConfirmFirst) 2 else 1, confirmations)
                assertEquals(1_000_000.0, current.optDouble("faceValue"))
                assertEquals(11_000.0, current.optDouble("feesPaid"))
                assertEquals(current.text("status"), model.purchases.single { it.text("id") == "pending-id" }.text("status"))
            }
            assertNull(model.pdfImportProgress)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}

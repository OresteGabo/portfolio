@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

class PendingPurchasesTest {
    private val today = LocalDate(2026, 10, 24)
    private fun issue() = JsonRecord("""{"id":"october","title":"New 20-year bond","tenorYears":20,"settlementDate":"2026-10-23","maturityDate":"2046-09-28"}""")
    private fun confirmation() = JsonRecord("""{"kind":"bond","missingFields":[],"warnings":[],"fields":{"bondName":"FXD5/2026/20Y","isin":"RW0000000123","issuer":"Government of Rwanda","currency":"RWF","tenorYears":20,"faceValue":900000,"pricePercent":101.5,"couponRate":0.12,"purchaseDate":"2026-10-23","settlementDate":"2026-10-23","maturityDate":"2046-09-28","firstCouponDate":"2027-03-28","couponDates":["2027-03-28"],"couponFrequency":2,"scheduleConfidence":"confirmed"}}""")
    @Test fun pendingNeedsOnlyPublishedBondAndAmountAndNeverChangesActiveTotals() {
        val pending = pendingBondInput(issue(), 1_000_000.0, today)
        assertEquals("submitted", pending.text("status"))
        assertEquals(1_000_000.0, pending.optDouble("faceValue"))
        assertEquals(11_000.0, pending.optDouble("feesPaid"))
        val snapshot = PortfolioSnapshot(purchases = listOf(pending))
        assertEquals(0.0, snapshot.summary(today).single().investedCash)
        assertEquals(0.0, snapshot.summary(today).single().netAnnualCoupon)
        assertEquals(1, snapshot.recentBonds(today).size)
        assertTrue(investmentCalendarEvents(emptyList(), snapshot).isEmpty())
    }
    @Test fun invalidAmountsAndIncompleteOrMaturedBondsAreRejected() {
        for (amount in listOf(0.0, -100000.0, 99000.0, 150000.0, Double.NaN)) {
            assertFailsWith<IllegalArgumentException> { pendingBondInput(issue(), amount, today) }
        }
        assertFailsWith<IllegalArgumentException> { pendingBondInput(issue().put("maturityDate", "2020-01-01"), 100000.0, today) }
        assertFailsWith<IllegalStateException> { pendingBondInput(issue().put("settlementDate", ""), 100000.0, today) }
    }
    @Test fun confirmationCanReduceAllocationButCannotMatchOtherBondsOrLargerAmounts() {
        val pending = pendingBondInput(issue(), 1_000_000.0, today)
        val confirmed = importedPurchaseInput(confirmation())
        assertEquals("active", confirmed.text("status"))
        assertTrue(pendingMatchesConfirmation(pending, confirmed))
        assertFalse(pendingMatchesConfirmation(pending, JsonRecord(confirmed.toString()).put("faceValue", 1_100_000)))
        assertFalse(pendingMatchesConfirmation(pending, JsonRecord(confirmed.toString()).put("maturityDate", "2047-09-28")))
        assertFalse(pendingMatchesConfirmation(pending, JsonRecord(confirmed.toString()).put("settlementDate", "2026-11-13")))
        assertFalse(pendingMatchesConfirmation(pending.put("status", "active"), confirmed))
    }
    @Test fun manuallyConfirmedPurchaseCanReceiveItsPersonalPdfWithoutAnotherPurchase() {
        val purchase=pendingBondInput(issue(),1_000_000.0,today).put("status","active").put("confirmationMethod","manual")
        assertTrue(needsPersonalConfirmation(purchase))
        assertTrue(pendingMatchesConfirmation(purchase,importedPurchaseInput(confirmation())))
        assertEquals(11_000.0,completePendingPurchase(confirmation(),purchase).optDouble("feesPaid"))
        purchase.put("confirmationDocumentId","stored-pdf")
        assertFalse(needsPersonalConfirmation(purchase))
        assertFalse(pendingMatchesConfirmation(purchase,importedPurchaseInput(confirmation())))
    }
    @Test fun completingPendingKeepsRecordedCommissionOnceAndUsesActualAllocationAndPrice() {
        val pending = pendingBondInput(issue(), 1_000_000.0, today)
        val review = confirmation()
        // Parser defaults must not erase a commission already recorded with the application.
        review.put("fields", review.optJSONObject("fields")!!.put("feesPaid", 0))
        val confirmed = completePendingPurchase(review, pending)
        assertEquals(11_000.0, confirmed.optDouble("feesPaid"))
        assertEquals(900_000.0, confirmed.optDouble("faceValue"))
        assertEquals(101.5, confirmed.optDouble("pricePercent"))
        assertEquals(0.0, completePendingPurchase(review, null).optDouble("feesPaid"))
        assertFailsWith<IllegalArgumentException> { completePendingPurchase(review, pending.put("maturityDate", "2040-01-01")) }
    }
    @Test fun unreadableOrInconsistentPdfsCannotFallBackToManualOrInventedDetails() {
        assertFailsWith<IllegalArgumentException> { importedPurchaseInput(confirmation().put("missingFields", JsonList("[\"Executed price\"]"))) }
        assertFailsWith<IllegalArgumentException> { importedPurchaseInput(confirmation().put("warnings", JsonList("[\"Settlement amount differs from principal × price.\"]"))) }
        assertFailsWith<IllegalArgumentException> { importedPurchaseInput(confirmation().put("warnings", JsonList("[\"Some coupon dates were invalid, duplicated, or outside the bond term.\"]"))) }
        val noPrice = confirmation();noPrice.put("fields", noPrice.optJSONObject("fields")!!.put("pricePercent", 0))
        assertFailsWith<IllegalArgumentException> { importedPurchaseInput(noPrice) }
        val wrongIssuer = confirmation();wrongIssuer.put("fields", wrongIssuer.optJSONObject("fields")!!.put("issuer", "Unknown issuer"))
        assertFailsWith<IllegalArgumentException> { importedPurchaseInput(wrongIssuer) }
    }
    @Test fun confirmingUpdatesTheSameRecordAndPublishesOnlyActualAllocatedValue() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        var saved = pendingBondInput(issue(), 1_000_000.0, today).put("id", "pending-id")
        val writes = mutableListOf<String>()
        val model = PortfolioModel(object : ServerSettings {
            override fun readOrigin() = "https://synthetic.example.test"
            override fun writeOrigin(origin: String) {}
        }, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request -> when (request.url.encodedPath) {
                    "/api/bonds/auth/session" -> respond("""{"authenticated":true,"user":{"id":"test","email":"test@example.test"}}""")
                    "/api/bonds/mobile-overview" -> respond("{}", HttpStatusCode.NotFound)
                    "/api/bonds/purchases" -> respond("""{"purchases":[$saved]}""")
                    "/api/bonds/equities" -> respond("""{"holdings":[]}""")
                    "/api/bonds/documents" -> respond("""{"documents":[]}""")
                    "/api/bonds/purchases/pending-id" -> {
                        assertEquals(HttpMethod.Put, request.method)
                        writes += request.url.encodedPath
                        saved = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).put("id", "pending-id")
                        respond("""{"purchase":$saved}""")
                    }
                    else -> respond("{}")
                } }
            }))
        })
        store.put("model", model)
        try {
            advanceUntilIdle()
            var closed = false
            model.savePurchase(completePendingPurchase(confirmation(), saved), "pending-id") { closed = true }
            advanceUntilIdle()
            assertTrue(closed)
            assertEquals(listOf("/api/bonds/purchases/pending-id"), writes)
            assertEquals(1, model.purchases.size)
            assertEquals("pending-id", model.purchases.single().text("id"))
            assertEquals("active", model.purchases.single().text("status"))
            assertEquals(900000.0, model.portfolio.summary(today).single().bondFaceValue)
            assertEquals(924500.0, model.portfolio.summary(today).single().investedCash)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun aPersonalConfirmationPreservesTheConfiguredWithholdingRate() {
        for (tax in listOf(0.0, .10)) {
            val pending = pendingBondInput(issue(), 1_000_000.0, today).put("withholdingTaxRate", tax)
            assertEquals(tax, completePendingPurchase(confirmation(), pending).optDouble("withholdingTaxRate"))
        }
        val review = confirmation()
        review.put("fields", review.getJSONObject("fields").put("withholdingTaxRate", .10))
        assertEquals(.10, importedPurchaseInput(review).optDouble("withholdingTaxRate"))
    }

}

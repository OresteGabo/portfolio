package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class YearlyCouponsTest {
    private val today = LocalDate(2026, 11, 1)
    private fun bond(id: String = "bond", currency: String = "RWF", status: String = "active") = JsonRecord("""{
        "id":"$id","bondName":"Test bond","currency":"$currency","status":"$status",
        "purchaseDate":"2025-12-01","maturityDate":"2027-12-31","faceValue":2200000,"couponRate":0.115,
        "withholdingTaxRate":0.05,"couponFrequency":2,"scheduleConfidence":"confirmed",
        "couponDates":["2025-12-31","2026-01-01","2026-07-01","2026-12-31","2027-01-01"]
    }""")
    private fun receipt(id: String = "bond", date: String = "2026-01-01", method: String = "manual", currency: String = "RWF", amount: Double = 120000.15) =
        JsonRecord().put("purchaseId", id).put("dueDate", date).put("method", method).put("currency", currency).put("amount", amount).put("received", true)
    private fun list(purchases: List<JsonRecord>, receipts: List<JsonRecord> = emptyList(), available: Boolean = true) = yearlyCoupons(purchases, receipts, 2026, today, available)

    @Test fun annualBoundsAndOfficialDatesArePreservedWithoutPrincipal() {
        val rows = list(listOf(bond())).rows
        assertEquals(listOf("2026-01-01", "2026-07-01", "2026-12-31"), rows.map { it.date.toString() })
        assertTrue(rows.none { it.estimatedDate }); assertTrue(rows.all { it.expectedNet == 120175.0 })
        assertEquals(360525.0, yearlyCouponTotals(rows, "RWF", true).scheduledNet)
        assertTrue(yearlyCoupons(listOf(bond()), emptyList(), 2030, today, true).rows.isEmpty())
    }
    @Test fun referenceBondsUseExistingTaxAndCouponCalculations() {
        val seven = bond().put("couponDates", JsonList(listOf("2026-01-01", "2026-07-01")))
        val ten = bond("ten").put("faceValue", 1000000).put("couponRate", .12).put("couponDates", JsonList(listOf("2026-02-01", "2026-08-01")))
        val data = list(listOf(seven, ten))
        assertEquals(listOf(120175.0, 57000.0, 120175.0, 57000.0), data.rows.map { it.expectedNet })
        assertEquals(354350.0, yearlyCouponTotals(data.rows, "RWF", true).scheduledNet)
    }
    @Test fun generatedDatesAreExplicitlyEstimated() {
        val p = bond().put("couponDates", JsonList()).put("firstCouponDate", "2026-03-31")
        val rows = list(listOf(p)).rows
        assertEquals(listOf("2026-03-31", "2026-09-30"), rows.map { it.date.toString() })
        assertTrue(rows.all { it.estimatedDate })
    }
    @Test fun receiptsNeverUseExpectedAmountsOrFutureEvidence() {
        val receipts = listOf(receipt(), receipt(date = "2026-07-01", method = "automatic", amount = 119999.85), receipt(date = "2026-12-31"))
        val rows = list(listOf(bond()), receipts).rows
        assertEquals(listOf(CouponReceiptState.CONFIRMED, CouponReceiptState.AUTOMATIC, CouponReceiptState.UPCOMING), rows.map { it.state })
        assertNull(rows.last().recordedAmount)
        val totals = yearlyCouponTotals(rows, "RWF", true)
        assertEquals(120000.15, totals.confirmed); assertEquals(119999.85, totals.automatic)
        assertEquals(360525.0, totals.scheduledNet)
        assertTrue(list(listOf(bond()), receipts, false).rows.filter { it.date <= today }.all { it.state == CouponReceiptState.UNAVAILABLE && it.recordedAmount == null })
        assertNull(yearlyCouponTotals(rows, "RWF", false).confirmed)
    }
    @Test fun receiptAndScheduleDeduplicationRetainsDistinctPurchases() {
        val p = bond().put("couponDates", JsonList(listOf("2026-01-01", "2026-01-01")))
        val evidence = receipt()
        val rows = list(listOf(p, p, bond("another").put("couponDates", JsonList(listOf("2026-01-01")))), listOf(evidence, evidence)).rows
        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.state == CouponReceiptState.CONFIRMED })
    }
    @Test fun currenciesAndPurchaseOwnershipRemainSeparate() {
        val rows = list(listOf(bond(), bond("usd", "USD")), listOf(receipt(currency = "USD"), receipt("usd", currency = "USD", amount = .25), receipt("unknown"))).rows
        assertEquals(0.0, yearlyCouponTotals(rows, "RWF", true).confirmed)
        assertEquals(.25, yearlyCouponTotals(rows, "USD", true).confirmed)
        assertTrue(rows.none { it.purchase.text("id") == "unknown" })
    }
    @Test fun missingTermsDoNotBecomeZeroAndRecordedEvidenceStaysVisible() {
        val p = bond().put("withholdingTaxRate", null)
        val data = list(listOf(p), listOf(receipt()))
        assertEquals(listOf(p), data.incomplete); assertEquals(1, data.rows.size)
        assertNull(data.rows.single().expectedNet)
        assertEquals(120000.15, yearlyCouponTotals(data.rows, "RWF", true).confirmed)
        assertNull(yearlyCouponTotals(data.rows, "RWF", true).scheduledNet)
        val badAmount = list(listOf(bond()), listOf(receipt().put("amount", "unknown"))).rows
        assertNull(yearlyCouponTotals(badAmount, "RWF", true).confirmed)
    }
    @Test fun submittedCancelledAndFuturePurchasesCannotProduceCouponIncome() {
        val rows = list(listOf(bond(status = "submitted"), bond("cancelled", status = "cancelled"), bond("future").put("purchaseDate", "2027-01-01")), listOf(receipt())).rows
        assertTrue(rows.isEmpty())
    }
    @Test fun soldBondsOnlyShowRecordedEntriesAndMaturedBondsHaveNoFutureForecast() {
        val sold = list(listOf(bond(status = "sold")), listOf(receipt())).rows
        assertEquals(1, sold.size); assertNull(sold.single().expectedNet)
        val matured = list(listOf(bond(status = "matured"))).rows
        assertTrue(matured.all { it.date <= today })
    }
    @Test fun missingFrequencyAndUnsupportedGeneratedSchedulesRequireAttention() {
        assertEquals(1, list(listOf(bond().put("couponFrequency", null))).incomplete.size)
        assertEquals(1, list(listOf(bond().put("couponFrequency", 7).put("couponDates", JsonList()))).incomplete.size)
        assertTrue(list(listOf(bond().put("couponRate", 0))).rows.isEmpty())
    }
    @Test fun totalsKeepDecimalCentsAndDoNotInventUnrecordedIncome() {
        val rows = listOf(.1, .2).mapIndexed { index, amount ->
            YearCoupon(bond("$index"), LocalDate(2026, 1, 1), amount, amount, CouponReceiptState.CONFIRMED, false)
        }
        assertEquals(YearCouponTotals(.3, .3, 0.0), yearlyCouponTotals(rows, "RWF", true))
        assertEquals(.01, sumMoney(listOf(.005))); assertEquals(0.0, sumMoney(emptyList()))
        val notReceived = list(listOf(bond()), listOf(receipt().put("received", false))).rows.first()
        assertEquals(CouponReceiptState.NOT_RECEIVED, notReceived.state); assertNull(notReceived.recordedAmount)
    }
}

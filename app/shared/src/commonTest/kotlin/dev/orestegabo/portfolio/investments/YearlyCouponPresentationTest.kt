package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class YearlyCouponPresentationTest {
    private val today = LocalDate(2026, 11, 1)
    private fun bond(id: String, currency: String = "RWF") = JsonRecord().put("id", id).put("bondName", "Bond $id")
        .put("currency", currency).put("status", "active").put("purchaseDate", "2025-12-01")
        .put("maturityDate", "2027-12-31").put("faceValue", 1_000_000).put("couponRate", .12)
        .put("withholdingTaxRate", .05).put("couponFrequency", 2).put("scheduleConfidence", "confirmed")
        .put("couponDates", JsonList(listOf("2026-01-01", "2026-07-01", "2027-01-01")))

    @Test fun currenciesStaySeparateAndKeepRowOrderAndRecordedAmounts() {
        val purchases = listOf(bond("usd", "USD"), bond("rwf"))
        val receipts = listOf(JsonRecord().put("purchaseId", "rwf").put("dueDate", "2026-01-01")
            .put("currency", "RWF").put("method", "manual").put("received", true).put("amount", 56_000))
        val originals = purchases.map { it.toString() }

        val page = couponYearPage(purchases, receipts, 2026, today, true)

        assertEquals(2026, page.year)
        assertEquals(listOf("RWF", "USD"), page.currencies)
        assertEquals(listOf("rwf:2026-01-01", "rwf:2026-07-01"), page.byCurrency.getValue("RWF").rows.map { it.key })
        assertEquals(YearCouponTotals(114_000.0, 56_000.0, 0.0), page.byCurrency.getValue("RWF").totals)
        assertEquals(YearCouponTotals(114_000.0, 0.0, 0.0), page.byCurrency.getValue("USD").totals)
        assertNull(page.next)
        assertEquals(originals, purchases.map { it.toString() })
    }

    @Test fun unavailableReceiptsAndIncompleteTermsAreNotTurnedIntoKnownZeroes() {
        val page = couponYearPage(listOf(bond("valid"), bond("missing").put("couponFrequency", null)), emptyList(), 2026, today, false)
        assertEquals(listOf("missing"), page.data.incomplete.map { it.text("id") })
        assertEquals(YearCouponTotals(114_000.0, null, null), page.byCurrency.getValue("RWF").totals)
    }

    @Test fun emptyYearKeepsTheExistingNextYearPreviewWithoutAddingACurrency() {
        val page = couponYearPage(listOf(bond("future").put("couponDates", JsonList(listOf("2027-01-01")))), emptyList(), 2026, today, true)
        assertTrue(page.data.rows.isEmpty())
        assertTrue(page.currencies.isEmpty())
        assertTrue(page.byCurrency.isEmpty())
        assertEquals(2027, page.next?.year)
        assertEquals(listOf(57_000.0), page.next?.coupons?.map { it.expectedNet })
    }
}

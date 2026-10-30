package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class MonthlyInvestmentGoalTest {
    private val today = LocalDate(2026, 10, 24)
    private fun bond(date: String = "2026-10-01", status: String = "active", currency: String = "RWF") = JsonRecord()
        .put("purchaseDate", date).put("status", status).put("currency", currency).put("faceValue", 900000)
        .put("pricePercent", 101.5).put("feesPaid", 11000).put("accruedInterestPaid", 0)
    @Test fun confirmedCashIncludesFeesAndSoldPurchasesButNotPendingOrForeignOrFuture() {
        val snapshot = PortfolioSnapshot(purchases = listOf(bond(), bond(status = "matured"), bond(status = "sold"),
            bond(status = "submitted"), bond(currency = "USD"), bond(date = "2026-10-25"), bond(date = "2026-09-01")),
            holdings = listOf(JsonRecord().put("tradeDate", "2026-10-22").put("status", "sold").put("currency", "RWF").put("netAmountPayable", 36500)))
        val progress = assertNotNull(monthlyInvestmentProgress(snapshot, today, 3_000_000, today))
        assertEquals(3 * 924500.0 + 36500, progress.confirmed)
        assertEquals(924500.0, progress.pending)
        assertEquals(190000.0, progress.remaining)
        assertFalse(progress.achieved)
    }
    @Test fun aggregatesAreAuthoritativeBeyondThePreviewAndAReachedGoalDoesNotGoNegative() {
        val snapshot = PortfolioSnapshot(purchases = listOf(bond()), recentPurchaseIds = setOf("preview"),
            monthlyInvestmentTotals = listOf(JsonRecord().put("month", "2026-10").put("confirmed", 600000).put("pending", 111000)))
        val progress = assertNotNull(monthlyInvestmentProgress(snapshot, today, 300000, today))
        assertEquals(600000.0, progress.confirmed)
        assertEquals(111000.0, progress.pending)
        assertEquals(1f, progress.fraction); assertEquals(0.0, progress.remaining); assertTrue(progress.achieved)
        assertEquals(0.0, monthlyInvestmentProgress(snapshot, LocalDate(2026, 9, 1), 300000, today)?.confirmed)
    }
    @Test fun oldCompactPreviewsNeverPretendToBeCompleteAndInvalidTargetsAreRejected() {
        assertNull(monthlyInvestmentProgress(PortfolioSnapshot(purchases = listOf(bond()), recentPurchaseIds = setOf("preview")), today, 100000, today))
        for (amount in listOf(0L, -1L, Long.MAX_VALUE)) assertFailsWith<IllegalArgumentException> {
            monthlyInvestmentProgress(PortfolioSnapshot(), today, amount, today)
        }
        assertEquals(0f, monthlyInvestmentProgress(PortfolioSnapshot(), today, 100000, today)?.fraction)
    }
}

package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class AnnualShareClosingRegressionTest {
    private fun holding() = JsonRecord().put("id", "synthetic-lot").put("ticker", "TEST").put("currency", "RWF")
        .put("tradeDate", "2024-01-01").put("status", "active").put("shares", 100).put("netAmountPayable", 1000)
    private fun sale(date: String, shares: Int, cost: Int) = JsonRecord().put("kind", "sale")
        .put("holdingId", "synthetic-lot").put("date", date).put("shares", shares).put("allocatedCost", cost)

    @Test fun closingSharesUseOnlySalesThroughYearEndIncludingTheBoundaryDay() {
        val lot = holding(); val before = lot.toString()
        val data = reportFixture().copy(snapshot = PortfolioSnapshot(holdings = listOf(lot)),
            ledgerEntries = listOf(sale("2025-12-30", 20, 200), sale("2025-12-31", 30, 300), sale("2026-01-01", 50, 500)))
        val closing = annualReportScope(data, 2025).closingShares.single()
        assertEquals(50.0, closing.getDouble("shares"))
        assertEquals(500.0, closing.getDouble("netAmountPayable"))
        assertTrue(annualReportScope(data, 2026).closingShares.isEmpty())
        assertEquals(before, lot.toString())
    }

    @Test fun currentYearClosingDoesNotApplyTomorrowSaleOrIncludeTomorrowPurchase() {
        val future = holding().put("id", "future").put("tradeDate", "2026-11-16")
        val data = reportFixture().copy(today = LocalDate(2026, 11, 15),
            snapshot = PortfolioSnapshot(holdings = listOf(holding(), future)),
            ledgerEntries = listOf(sale("2026-11-15", 20, 200), sale("2026-11-16", 80, 800)))
        val scope = annualReportScope(data, 2026)
        assertEquals(LocalDate(2026, 11, 15), scope.cutoff)
        val closing = scope.closingShares.single()
        assertEquals("synthetic-lot", closing.text("id"))
        assertEquals(80.0, closing.getDouble("shares"))
        assertEquals(800.0, closing.getDouble("netAmountPayable"))
    }
}

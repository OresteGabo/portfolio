package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class ShareCompanyRowsTest {
    private val today = LocalDate(2026, 11, 14)
    private fun lot(id: String, shares: Int = 100, cost: Double = 73231.2,
                    ticker: String = "BOK", currency: String = "RWF", date: String = "2026-11-12") =
        JsonRecord().put("id", id).put("ticker", ticker).put("securityName", "Bank of Kigali")
            .put("currency", currency).put("status", "active").put("shares", shares)
            .put("netAmountPayable", cost).put("tradeDate", date).put("documentId", "pdf:$id")

    @Test fun fivePurchasesBecomeOneCompanyRowWithoutChangingSourceRecords() {
        val lots = (1..5).map { lot("$it") }
        val originals = lots.map { it.toString() }
        val row = shareCompanyRows(lots).single()
        assertEquals("BOK", row.text("ticker"))
        assertEquals(500.0, row.optDouble("shares"))
        assertEquals(366156.0, row.optDouble("netAmountPayable"))
        assertFalse(row.has("documentId"))
        assertEquals(originals, lots.map { it.toString() })
    }

    @Test fun remainingSharesAndTheirCostAreUsedAfterSales() {
        val sold = lot("sold").put("remainingShares", 0).put("remainingCost", 0)
        val partial = lot("partial").put("remainingShares", 40).put("remainingCost", 29292.48)
        val row = shareCompanyRows(listOf(sold, partial, lot("new"))).single()
        assertEquals(140.0, row.optDouble("shares"))
        assertEquals(102523.68, row.optDouble("netAmountPayable"))
    }

    @Test fun companiesCurrenciesAndUnconfirmedOrdersStaySeparate() {
        val pending = lot("order").put("status", "pending")
        val rows = shareCompanyRows(listOf(lot("a"), lot("b"), lot("c", ticker = "BLR"),
            lot("d", currency = "USD"), pending))
        assertEquals(4, rows.size)
        assertSame(pending, rows.single { it.text("status") == "pending" })
    }

    @Test fun recentCompanyIncludesOlderPurchasesAndCompactOverviewBalances() {
        val recent = lot("recent")
        val older = lot("older", 200, 100000.0, date = "2024-01-01")
        val future = lot("future", date = "2027-01-01")
        val snapshot = PortfolioSnapshot(holdings = listOf(recent, older, future), recentHoldingIds = setOf("recent"))
        val row = snapshot.recentShareCompanies(today).single()
        assertEquals(300.0, row.optDouble("shares"))
        assertEquals(173231.2, row.optDouble("netAmountPayable"))
        val compact = snapshot.copy(holdings = listOf(recent,
            lot("aggregate:BOK", 200, 100000.0, date = "2024-01-01").put("lotCount", 2)))
        assertEquals(row.toString(), compact.recentShareCompanies(today).single().toString())
        assertTrue(snapshot.copy(recentHoldingIds = emptySet()).recentShareCompanies(today).isEmpty())
    }

    @Test fun confirmingPendingOrderAddsToCompanyInsteadOfAddingAnotherTile() {
        val existing = lot("existing")
        val order = lot("order", 1000).put("status", "pending")
        val confirmed = lot("confirmed", 1000)
        val before = PortfolioSnapshot(holdings = listOf(existing), shareOrders = listOf(order))
        val after = before.copy(holdings = listOf(existing, confirmed), shareOrders = emptyList())
        assertEquals(100.0, before.recentShareCompanies(today).single().optDouble("shares"))
        assertEquals(1100.0, after.recentShareCompanies(today).single().optDouble("shares"))
        assertEquals(2, after.holdings.size)
    }
}

package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class PendingInvestmentStatisticsTest {
    private val today = LocalDate(2026, 11, 13)
    private fun order(status: String = "pending") = JsonRecord().put("id", "order").put("status", status)
        .put("ticker", "BOK").put("currency", "RWF").put("shares", 1000).put("pricePerShare", 720)
        .put("requestDate", "2026-11-13").put("grossConsideration", 720000)
        .put("estimatedCommission", 12312).put("estimatedTotal", 732312)
    private fun bond() = JsonRecord().put("id", "bond").put("status", "submitted").put("currency", "RWF")
        .put("purchaseDate", "2026-11-16").put("maturityDate", "2033-11-16").put("faceValue", 100000)
        .put("pricePercent", 100).put("feesPaid", 11000).put("couponRate", .1).put("withholdingTaxRate", .05)
    private val quotes = JsonRecord().put("source", JsonRecord().put("publishedAt", "2026-11-13"))
        .put("quotes", JsonList(listOf(JsonRecord().put("ticker", "BOK").put("closing", 730))))

    @Test fun pendingRequestsAffectWorthCostAllocationAndAnnualCouponWithoutBecomingSellable() {
        val snapshot = PortfolioSnapshot(purchases = listOf(bond()), shareOrders = listOf(order(), order("cancelled"), order("executed")))
        val value = snapshot.valuation(today, quotes).single()
        assertEquals(100000.0, value.cost.bondFaceValue)
        assertEquals(111000.0, value.cost.bondCashCost)
        assertEquals(732312.0, value.cost.shareCashCost)
        assertEquals(843312.0, value.cost.investedCash)
        assertEquals(730000.0, value.shareValue)
        assertEquals(830000.0, value.estimatedNetWorth)
        assertEquals(9500.0, value.cost.netAnnualCoupon)
        assertEquals(1, value.cost.bondCount); assertEquals(1, value.cost.shareCount)
        assertEquals(-2312.0, value.shares.single().gainLoss)
        assertTrue(snapshot.activeShares(today).isEmpty())
        assertTrue(snapshot.holdings.isEmpty())
    }

    @Test fun confirmingOrderReplacesPendingEstimateWithoutDoublingStatsOrGoalProgress() {
        val pending = PortfolioSnapshot(shareOrders = listOf(order()))
        val executed = JsonRecord(order().toString()).put("id", "purchase").put("status", "active")
            .put("tradeDate", today.toString()).put("netAmountPayable", 732312)
        val confirmed = PortfolioSnapshot(holdings = listOf(executed), shareOrders = listOf(order("executed")))
        assertEquals(pending.summary(today), confirmed.summary(today))
        assertEquals(pending.valuation(today, quotes), confirmed.valuation(today, quotes))
        val before = assertNotNull(monthlyInvestmentProgress(pending, today, 800000, today))
        val after = assertNotNull(monthlyInvestmentProgress(confirmed, today, 800000, today))
        assertEquals(732312.0, before.pending); assertEquals(0.0, before.confirmed)
        assertEquals(0.0, after.pending); assertEquals(732312.0, after.confirmed)
        assertEquals(before.invested, after.invested); assertEquals(before.fraction, after.fraction)
        assertEquals(67688.0, before.remaining)
    }

    @Test fun compactMonthlyTotalsAddPendingSharesOnceAndKeepOtherCurrenciesSeparate() {
        val snapshot = PortfolioSnapshot(shareOrders = listOf(order()), recentHoldingIds = emptySet(),
            monthlyInvestmentTotals = listOf(JsonRecord().put("month", "2026-11").put("confirmed", 100000).put("pending", 111000)))
        val progress = assertNotNull(monthlyInvestmentProgress(snapshot, today, 1000000, today))
        assertEquals(843312.0, progress.pending); assertEquals(943312.0, progress.invested)
        val foreign = JsonRecord(order().toString()).put("currency", "USD")
        assertEquals(setOf("USD", "RWF"), snapshot.copy(shareOrders = listOf(order(), foreign)).summary(today).map { it.currency }.toSet())
        val fees = snapshot.copy(recentHoldingIds = null).purchaseFeeAnalysis(today).currencies.single()
        assertEquals(12312.0, fees.fees)
        assertEquals(732312.0, fees.cashCost)
    }
}

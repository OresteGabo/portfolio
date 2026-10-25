package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class PortfolioValuationTest {
    private val today = LocalDate(2026, 10, 23)
    private fun lot(ticker: String = "BOK", currency: String = "RWF", status: String = "active", date: String = "2026-01-01", cost: Double = 36500.0) =
        JsonRecord().put("ticker", ticker).put("currency", currency).put("status", status).put("tradeDate", date)
            .put("shares", 100).put("netAmountPayable", cost)
    private fun quotes(price: Double, date: String = "2026-10-23", ticker: String = "BOK") =
        JsonRecord().put("source", JsonRecord().put("publishedAt", date)).put("quotes", JsonList(listOf(
            JsonRecord().put("ticker", ticker).put("closing", if (price.isFinite()) price else price.toString()))))

    @Test fun priceChangesUpdateValueWithoutChangingCashCostAndMultipleLotsAreCountedOnce() {
        val snapshot = PortfolioSnapshot(holdings = listOf(lot(), lot(cost = 40000.0)))
        val rising = snapshot.valuation(today, quotes(500.0)).single()
        assertEquals(100000.0, rising.estimatedNetWorth)
        assertEquals(76500.0, rising.cost.investedCash)
        assertEquals(23500.0, rising.shares.single().gainLoss)
        assertEquals(23500.0 / 76500.0, rising.shares.single().gainLossRate)
        assertEquals(1, rising.pricedShares)
        val falling = snapshot.valuation(today, quotes(300.0)).single()
        assertEquals(60000.0, falling.estimatedNetWorth)
        assertEquals(-16500.0, falling.shares.single().gainLoss)
        assertEquals(-16500.0 / 76500.0, falling.shares.single().gainLossRate)
    }

    @Test fun estimateUsesBondPrincipalAndNeverAddsAnnualIncomeOrPurchaseFeesToMarketValue() {
        val bond = JsonRecord().put("status", "active").put("currency", "RWF").put("purchaseDate", "2026-01-01")
            .put("maturityDate", "2027-01-01").put("faceValue", 100000).put("pricePercent", 98)
            .put("feesPaid", 1000).put("accruedInterestPaid", 500).put("couponRate", .1).put("withholdingTaxRate", .05)
        val total = PortfolioSnapshot(listOf(bond), listOf(lot())).valuation(today, quotes(400.0)).single()
        assertEquals(140000.0, total.estimatedNetWorth)
        assertEquals(136000.0, total.cost.investedCash)
        assertEquals(9500.0, total.cost.netAnnualCoupon)
    }

    @Test fun unquotedSharesUseCostAndDoNotInventZeroGains() {
        val total = PortfolioSnapshot(holdings = listOf(lot(), lot("UNKNOWN"))).valuation(today, quotes(400.0)).single()
        assertEquals(76500.0, total.estimatedNetWorth)
        assertEquals(1, total.unpricedShares)
        assertNull(total.shares.single { it.position.ticker == "UNKNOWN" }.gainLoss)
        assertNull(total.shares.single { it.position.ticker == "UNKNOWN" }.gainLossRate)
        assertEquals(36500.0, PortfolioSnapshot(holdings = listOf(lot())).valuation(today, null).single().estimatedNetWorth)
    }

    @Test fun rwfQuotesCannotValueOtherCurrenciesAndInactiveOrFuturePurchasesAreExcluded() {
        val snapshot = PortfolioSnapshot(holdings = listOf(lot(), lot(currency = "USD", cost = 500.0),
            lot(status = "sold"), lot(status = "submitted"), lot(date = "2027-01-01")))
        val totals = snapshot.valuation(today, quotes(400.0)).associateBy { it.cost.currency }
        assertEquals(40000.0, totals.getValue("RWF").estimatedNetWorth)
        assertEquals(500.0, totals.getValue("USD").estimatedNetWorth)
        assertEquals(1, totals.getValue("USD").unpricedShares)
    }

    @Test fun invalidAndFuturePricesFallBackToCostWhileKnownOlderClosingPricesRemainUsable() {
        val snapshot = PortfolioSnapshot(holdings = listOf(lot()))
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { price ->
            assertEquals(1, snapshot.valuation(today, quotes(price)).single().unpricedShares)
        }
        assertEquals(1, snapshot.valuation(today, quotes(400.0, "2026-10-24")).single().unpricedShares)
        assertEquals(40000.0, snapshot.valuation(today, quotes(400.0, "2026-10-22", " bok ")).single().estimatedNetWorth)
    }
    @Test fun breakEvenReturnIsZeroAndMissingCostDoesNotInventAPercentage() {
        val equal = ValuedSharePosition(SharePosition("IMR", "RWF", 100.0, 9000.0), 90.0)
        assertEquals(0.0, equal.gainLossRate)
        val noCost = ValuedSharePosition(SharePosition("IMR", "RWF", 100.0, 0.0), 115.0)
        assertNull(noCost.gainLossRate)
        val feesIncluded = ValuedSharePosition(SharePosition("IMR", "RWF", 100.0, 9150.0), 90.0)
        assertEquals(-150.0, feesIncluded.gainLoss)
        assertEquals(-150.0 / 9150.0, feesIncluded.gainLossRate)
    }

}

package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class PortfolioSnapshotTest {
    private val today = LocalDate(2026, 10, 23)
    private fun share(ticker: String, cost: Double, currency: String = "RWF", status: String = "active", date: String = "2026-01-01") =
        JsonRecord().put("ticker", ticker).put("netAmountPayable", cost).put("currency", currency)
            .put("status", status).put("tradeDate", date).put("shares", 100)

    @Test fun sharesAlonePopulateOverviewAndMultipleLotsAggregateWithoutDoubleCounting() {
        val snapshot = PortfolioSnapshot(holdings = listOf(share("BOK", 36500.0), share("BOK", 40000.0)))
        val total = snapshot.summary(today).single()
        assertEquals(76500.0, total.investedCash)
        assertEquals(0.0, total.bondCashCost)
        assertEquals(2, total.shareCount)
        val position = snapshot.sharePositions(today).single()
        assertEquals(200.0, position.shares)
        assertEquals(total.shareCashCost, position.cashCost)
    }

    @Test fun mixedPortfolioUsesCashCostAndKeepsCurrenciesSeparate() {
        val bond = JsonRecord().put("currency", "RWF").put("status", "active").put("purchaseDate", "2026-01-01")
            .put("faceValue", 100000).put("pricePercent", 98).put("feesPaid", 1000).put("accruedInterestPaid", 500)
        val snapshot = PortfolioSnapshot(listOf(bond), listOf(share("BOK", 36500.0), share("OTHER", 500.0, "USD")))
        val totals = snapshot.summary(today).associateBy { it.currency }
        assertEquals(136000.0, totals.getValue("RWF").investedCash)
        assertEquals(100000.0, totals.getValue("RWF").bondFaceValue)
        assertEquals(500.0, totals.getValue("USD").investedCash)
    }

    @Test fun soldSubmittedFutureAndInvalidDatedSharesRemainRecordedButAreNotOwnedYet() {
        val snapshot = PortfolioSnapshot(holdings = listOf(share("BOK", 1.0), share("BOK", 2.0, status = "sold"),
            share("BOK", 3.0, status = "submitted"), share("BOK", 4.0, date = "2027-01-01"), share("BOK", 5.0, date = "invalid")))
        assertEquals(5, snapshot.holdings.size)
        assertEquals(1.0, snapshot.summary(today).single().investedCash)
        assertEquals(100.0, snapshot.sharePositions(today).single().shares)
    }

    @Test fun annualNetCouponUsesSavedTermsForCurrentActiveBondsWithoutMixingCurrencies() {
        fun bond(currency: String, status: String = "active", purchase: String = "2026-01-01", maturity: String = "2027-01-01") =
            JsonRecord().put("currency", currency).put("status", status).put("purchaseDate", purchase)
                .put("maturityDate", maturity).put("faceValue", 100000).put("couponRate", 0.1).put("withholdingTaxRate", 0.05)
        val snapshot = PortfolioSnapshot(purchases = listOf(bond("RWF"), bond("USD"), bond("RWF", "sold"),
            bond("RWF", "submitted"), bond("RWF", purchase = "2027-01-01"), bond("RWF", maturity = today.toString()),
            bond("RWF", maturity = "invalid")))
        val totals = snapshot.summary(today).associateBy { it.currency }
        assertEquals(9500.0, totals.getValue("RWF").netAnnualCoupon)
        assertEquals(9500.0, totals.getValue("USD").netAnnualCoupon)
        assertEquals(0.0, PortfolioSnapshot(holdings = listOf(share("BOK", 100.0))).summary(today).single().netAnnualCoupon)
    }
}

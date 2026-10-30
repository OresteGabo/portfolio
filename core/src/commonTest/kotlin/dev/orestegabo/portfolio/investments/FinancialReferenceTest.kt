package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

/** Synthetic records only. Reference maturities specify a month; the day here is a test anchor,
 * not an invented official BNR date. A separate test verifies supplied official-date precedence.
 */
class FinancialReferenceTest {
    private fun bond(principal: Double, rate: Double, maturity: String, tax: Double = .05) =
        BondTerms(LocalDate(2026, 7, 1), LocalDate.parse(maturity), principal, rate, tax, 2)

    @Test fun bnrSevenAndTenYearReferenceCouponsSeparateGrossNetAndPrincipal() {
        for ((terms, gross, net) in listOf(
            Triple(bond(2_200_000.0, .115, "2033-07-31"), 126_500.0, 120_175.0),
            Triple(bond(1_000_000.0, .12, "2036-08-31"), 60_000.0, 57_000.0))) {
            assertEquals(gross, terms.grossCouponPayment())
            assertEquals(net, terms.netCouponPayment())
            val first = couponSchedule(terms).first()
            val tracking = trackBond(terms, first)
            assertEquals(net, tracking.netPerPayment)
            assertEquals(net, tracking.scheduledCouponsToDate)
            assertEquals(0.0, tracking.accruedCoupon)
            val mature = trackBond(terms, terms.maturityDate)
            assertNull(mature.nextCoupon)
            assertEquals(0.0, mature.remainingCoupons)
            assertEquals(0.0, mature.accruedCoupon)
            assertEquals(mature, trackBond(terms, LocalDate(2040, 1, 1)))
        }
    }
    @Test fun withholdingIsConfigurableAndNeverChangesTheGrossCoupon() {
        val terms = bond(2_200_000.0, .115, "2033-07-31")
        assertEquals(126_500.0, terms.copy(taxRate = 0.0).netCouponPayment())
        assertEquals(113_850.0, terms.copy(taxRate = .10).netCouponPayment())
        assertEquals(0.0, terms.copy(taxRate = 1.0).netCouponPayment())
        assertEquals(126_500.0, terms.copy(taxRate = .10).grossCouponPayment())
    }
    @Test fun explicitDatesWinAndGeneratedDatesRetainTheirOriginalAnchor() {
        val terms = bond(1_000_000.0, .12, "2036-08-31")
        val actual = listOf(LocalDate(2027, 2, 19), LocalDate(2027, 8, 20), LocalDate(2036, 8, 22))
        assertEquals(actual, couponSchedule(terms.copy(confirmedDates = actual)))
        val inferred = couponSchedule(terms)
        assertTrue(LocalDate(2035, 8, 31) in inferred) // February must not move August to the 28th.
        assertTrue(LocalDate(2032, 2, 29) in inferred)
        val forward = couponSchedule(terms.copy(firstCoupon = LocalDate(2026, 8, 31)))
        assertTrue(LocalDate(2027, 8, 31) in forward)
    }
    @Test fun monetaryRoundingIsDecimalHalfUpOnceAtTheBoundary() {
        assertEquals(1.01, roundedMoney(1.005))
        assertEquals(-1.01, roundedMoney(-1.005))
        assertEquals(.3, decimalAmount(emptyList(), addends = listOf(.1, .2)))
        assertEquals(57_001.0, bond(1_000_017.54, .12, "2036-08-31").netCouponPayment())
        // Tax the unrounded gross: rounding gross first would incorrectly produce .01 here.
        assertEquals(0.0, decimalAmount(listOf(.01, 1.0), 2, .05))
    }
    private fun equity(ticker: String, shares: Int, price: Double, fees: Double, payable: Double) =
        JsonRecord().put("ticker", ticker).put("currency", "RWF").put("status", "active").put("tradeDate", "2026-09-24")
            .put("shares", shares).put("pricePerShare", price).put("totalCharges", fees).put("netAmountPayable", payable)
    @Test fun equityReferencesUsePayableAsCostAndNeverAddFeesTwice() {
        val imr = equity("IMR", 400, 90.0, 615.60, 36_615.60)
        val bok = equity("BOK", 500, 670.0, 5_728.50, 340_728.50)
        val quotes = JsonRecord().put("source", JsonRecord().put("publishedAt", "2026-10-30"))
            .put("quotes", JsonList(listOf(JsonRecord().put("ticker", "IMR").put("closing", 90), JsonRecord().put("ticker", "BOK").put("closing", 670))))
        val values = PortfolioSnapshot(holdings = listOf(imr, bok)).valuation(LocalDate(2026, 10, 30), quotes).single()
        assertEquals(377_344.10, values.cost.investedCash)
        assertEquals(371_000.0, values.shareValue)
        assertEquals(-615.60, values.shares.single { it.position.ticker == "IMR" }.gainLoss)
        assertEquals(-5_728.50, values.shares.single { it.position.ticker == "BOK" }.gainLoss)
        assertEquals(371_000.0, values.estimatedNetWorth) // No hypothetical coupon/dividend cash added.
        val noQuote = PortfolioSnapshot(holdings = listOf(imr, bok)).valuation(LocalDate(2026, 10, 30), null).single()
        assertEquals(377_344.10, noQuote.shareValue)
        assertTrue(noQuote.shares.all { it.gainLoss == null })
    }
}

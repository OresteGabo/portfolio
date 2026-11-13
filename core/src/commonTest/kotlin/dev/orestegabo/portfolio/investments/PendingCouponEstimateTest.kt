package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class PendingCouponEstimateTest {
    private val today = LocalDate(2026, 11, 13)
    private fun bond(tenor: Int = 10, rate: Double = 0.0, status: String = "submitted") = JsonRecord()
        .put("id", "bond").put("issuer", "Government of Rwanda").put("currency", "RWF").put("status", status)
        .put("purchaseDate", "2026-11-13").put("maturityDate", "2036-11-13").put("tenorYears", tenor)
        .put("faceValue", 1000000).put("couponRate", rate).put("withholdingTaxRate", .05)
    private fun reference(tenor: Int, rate: Double, isin: String = "RWREF$tenor") = bond(tenor, rate, "active").put("isin", isin)

    @Test fun publishedTenorCurveProvidesAfterTaxAnnualEstimatesWithoutChangingSavedTerms() {
        val expected = listOf(3 to 99750.0, 5 to 104500.0, 7 to 109250.0, 10 to 114000.0, 15 to 122550.0, 20 to 124925.0)
        expected.forEach { (tenor, annual) ->
            val p = bond(tenor); val before = p.toString()
            val estimate = pendingCouponEstimate(p, emptyList(), today)
            assertEquals(annual, estimate.netAnnual); assertTrue(estimate.assumed)
            assertEquals(before, p.toString())
        }
    }

    @Test fun savedAndMatchingSecurityRatesOverrideTheTenorAssumption() {
        val p = bond(rate = .135).put("isin", "RWSECURITY/MN")
        assertEquals(.135, pendingCouponEstimate(p, listOf(reference(10,.14,"RWSECURITY")), today).rate)
        assertFalse(pendingCouponEstimate(p, emptyList(), today).assumed)
        val missing = bond().put("isin", "RWSECURITY/MN")
        val exact = pendingCouponEstimate(missing, listOf(reference(10,.14,"RWSECURITY")), today)
        assertEquals(133000.0, exact.netAnnual); assertFalse(exact.assumed)
        val zero = bond().put("publishedTermsAvailable", true)
        assertEquals(0.0, pendingCouponEstimate(zero, emptyList(), today).netAnnual)
        assertFalse(pendingCouponEstimate(zero, emptyList(), today).assumed)
    }

    @Test fun publicTenorRatesUseUniqueSecurityMedianAndInterpolateWithoutExtrapolation() {
        val a = reference(7,.11); val b = reference(7,.12,"RWOTHER")
        val terms = listOf(a,a,a,b,reference(10,.13))
        assertEquals(.115, pendingCouponEstimate(bond(7), terms, today).rate!!, .0000001)
        assertEquals(.12, pendingCouponEstimate(bond(8), terms, today).rate!!, .0000001)
        assertEquals(.105, pendingCouponEstimate(bond(1), emptyList(), today).rate)
        assertEquals(.1315, pendingCouponEstimate(bond(30), emptyList(), today).rate)
    }

    @Test fun missingTaxInvalidTenorAndOtherMarketsDoNotReceiveInventedRates() {
        for (p in listOf(bond().put("withholdingTaxRate", null), bond().put("withholdingTaxRate", 2),
            bond().put("currency", "USD"), bond().put("issuer", "Corporate issuer"), bond().put("tenorYears", 0))) {
            assertNull(pendingCouponEstimate(p, emptyList(), today).netAnnual)
        }
        assertEquals(120000.0, pendingCouponEstimate(bond().put("withholdingTaxRate", 0), emptyList(), today).netAnnual)
        assertNull(pendingCouponEstimate(bond(), emptyList(), LocalDate(2024,1,1)).netAnnual)
        val badReferences = listOf(reference(10,.9).put("currency", "USD"),reference(10,.12).put("couponRate","unknown"),reference(10,.8).put("issuer","Corporate"))
        assertEquals(.12, pendingCouponEstimate(bond(), badReferences, today).rate)
    }

    @Test fun summarySeparatesConfirmedAndPendingAndConfirmationReplacesTheEstimate() {
        val confirmed = bond(rate = .1,status = "active")
        val pending = bond().put("id", "pending")
        val summary = PortfolioSnapshot(listOf(confirmed,pending)).summary(today).single()
        assertEquals(95000.0, summary.confirmedAnnualCoupon)
        assertEquals(114000.0, summary.pendingAnnualCoupon)
        assertEquals(209000.0, summary.netAnnualCoupon)
        assertEquals(1, summary.estimatedPendingCouponCount)
        val filled = JsonRecord(pending.toString()).put("status", "active").put("couponRate", .125)
        val after = PortfolioSnapshot(listOf(confirmed,filled)).summary(today).single()
        assertEquals(213750.0, after.netAnnualCoupon)
        assertEquals(0.0, after.pendingAnnualCoupon); assertEquals(0, after.estimatedPendingCouponCount)
        val unknown = PortfolioSnapshot(listOf(bond().put("withholdingTaxRate", null))).summary(today).single()
        assertEquals(1, unknown.unavailablePendingCouponCount)
        assertEquals(0.0, unknown.netAnnualCoupon)
    }
}

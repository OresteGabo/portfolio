package dev.orestegabo.portfolio.investments

import kotlin.test.*
import kotlinx.datetime.LocalDate

class BondMathTest {
    private val terms = BondTerms(LocalDate.parse("2026-01-01"), LocalDate.parse("2028-01-01"), 1_000_000.0, .12, .05, 2)
    @Test fun couponsIncludeMaturityAndDeductTax() {
        val tracking = trackBond(terms, LocalDate.parse("2026-07-01"))
        assertEquals(4, tracking.schedule.size)
        assertEquals(57_000.0, tracking.netPerPayment, .001)
        assertEquals(57_000.0, tracking.couponsPaid, .001)
        assertEquals(0.0, tracking.accruedCoupon, .001)
        assertEquals(LocalDate.parse("2027-01-01"), tracking.nextCoupon)
    }
    @Test fun beforePurchaseHasNoIncomeOrAccrual() {
        val tracking = trackBond(terms, LocalDate.parse("2025-12-31"))
        assertEquals(0.0, tracking.couponsPaid, .001)
        assertEquals(0.0, tracking.accruedCoupon, .001)
        assertEquals(0f, tracking.progress)
    }
    @Test fun atMaturityNoFutureIncomeIsInvented() {
        val tracking = trackBond(terms, LocalDate.parse("2030-01-01"))
        assertEquals(228_000.0, tracking.couponsPaid, .001)
        assertEquals(0.0, tracking.remainingCoupons, .001)
        assertNull(tracking.nextCoupon)
        assertEquals(1f, tracking.progress)
    }
    @Test fun explicitScheduleTakesPrecedenceAndFiltersOutOfTermDates() {
        val dates = listOf("2025-01-01", "2026-09-01", "2027-03-01", "2029-01-01").map(LocalDate::parse)
        assertEquals(dates.subList(1,3), couponSchedule(terms.copy(confirmedDates = dates)))
    }
    @Test fun monthEndSchedulesClampInsteadOfSkippingMonths() {
        val endOfMonth = terms.copy(maturityDate = LocalDate.parse("2028-08-31"))
        val dates = couponSchedule(endOfMonth)
        assertTrue(dates.contains(LocalDate.parse("2028-02-29")))
        assertEquals(LocalDate.parse("2028-08-31"), dates.last())
    }
    @Test fun malformedTermsCannotCreateANonAdvancingOrUnboundedSchedule() {
        for (frequency in listOf(-1, 0, 13, 25, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { terms.copy(frequency = frequency) }
        }
        assertFailsWith<IllegalArgumentException> { terms.copy(maturityDate = terms.purchaseDate) }
        assertFailsWith<IllegalArgumentException> { terms.copy(maturityDate = LocalDate(2127, 1, 1)) }
        assertFailsWith<IllegalArgumentException> { terms.copy(firstCoupon = LocalDate(1000, 1, 1)) }
        assertFailsWith<IllegalArgumentException> { terms.copy(faceValue = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { terms.copy(couponRate = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { terms.copy(taxRate = 1.1) }
        // Unsupported inferred spacing is unavailable; published dates remain usable.
        assertTrue(couponSchedule(terms.copy(frequency = 7)).isEmpty())
        assertEquals(listOf(LocalDate(2027, 1, 1)), couponSchedule(terms.copy(frequency = 7, confirmedDates = listOf(LocalDate(2027, 1, 1)))))
    }
    @Test fun scheduledIncomeDoesNotClaimPaymentVerification() {
        val tracking = trackBond(terms, LocalDate(2026, 7, 1))
        assertEquals(tracking.couponsPaid, tracking.scheduledCouponsToDate)
        assertEquals(57_000.0, tracking.scheduledCouponsToDate, .001)
    }
    @Test fun theJsonBoundaryRejectsMalformedFrequencyInsteadOfTruncatingIt() {
        fun record() = JsonRecord().put("purchaseDate", "2026-01-01").put("maturityDate", "2028-01-01")
            .put("faceValue", 1000000).put("couponRate", .12).put("withholdingTaxRate", .05)
        assertNotNull(record().terms())
        for (frequency in listOf(0.0, 2.5, 25.0)) assertNull(record().put("couponFrequency", frequency).terms())
        assertNull(record().put("couponFrequency", "not a frequency").terms())
        assertNull(record().put("couponFrequency", null).terms())
        assertNull(record().put("maturityDate", "2400-01-01").terms())
    }
}

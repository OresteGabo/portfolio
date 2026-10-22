package dev.orestegabo.portfolio.investments

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

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
}

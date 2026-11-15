package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class CouponFrequencyTest {
    @Test fun validCountsHaveValueEqualityAndExplicitCalendarIntervals() {
        val intervals = mapOf(1 to 12, 2 to 6, 3 to 4, 4 to 3, 6 to 2, 12 to 1)
        for (count in 1..12) {
            val frequency = CouponFrequency(count)
            assertEquals(frequency, CouponFrequency.fromNumber(count.toDouble()))
            assertEquals(count, frequency.paymentsPerYear)
            assertEquals(intervals[count], frequency.calendarIntervalMonths)
        }
    }

    @Test fun invalidCountsCannotCreateAValueAndNumericInputIsNotRounded() {
        for (count in listOf(Int.MIN_VALUE, -1, 0, 13, Int.MAX_VALUE)) {
            assertEquals("Coupon frequency must be between 1 and 12.", assertFailsWith<IllegalArgumentException> { CouponFrequency(count) }.message)
        }
        for (number in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.0, -1.0, 1.5, 12.00000001, 13.0, 1e100)) {
            assertNull(CouponFrequency.fromNumber(number))
        }
    }

    @Test fun bondCopiesRevalidateFrequencyWithoutChangingThePublicRepresentation() {
        val terms = BondTerms(LocalDate(2026, 1, 1), LocalDate(2028, 1, 1), 1_000_000.0, .12, .05, 2)
        val quarterly = terms.copy(frequency = 4)
        assertEquals(2, terms.frequency)
        assertEquals(CouponFrequency(2), terms.couponFrequency)
        assertEquals(4, quarterly.frequency)
        assertEquals(CouponFrequency(4), quarterly.couponFrequency)
        assertEquals(terms.copy(), terms)
        assertFailsWith<IllegalArgumentException> { terms.copy(frequency = 0) }
    }
}

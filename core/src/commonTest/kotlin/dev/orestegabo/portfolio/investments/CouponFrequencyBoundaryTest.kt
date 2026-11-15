package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

/** Characterizes the existing model and record boundary before introducing the value object. */
class CouponFrequencyBoundaryTest {
    private fun terms(frequency: Int = 2) = BondTerms(LocalDate(2026, 1, 1), LocalDate(2028, 1, 1), 1_000_000.0, .12, .05, frequency)
    private fun record() = JsonRecord().put("purchaseDate", "2026-01-01").put("maturityDate", "2028-01-01")
        .put("faceValue", 1_000_000).put("couponRate", .12).put("withholdingTaxRate", .05)

    @Test fun recordedFrequencyIsAWholeCountAndMissingStillDefaultsToSemiannual() {
        assertEquals(2, record().terms()?.frequency)
        for (frequency in 1..12) assertEquals(frequency, record().put("couponFrequency", frequency).terms()?.frequency)
        for (raw in listOf("0", "13", "2.5", "NaN", "Infinity", "not a frequency", "1e100")) {
            assertNull(record().put("couponFrequency", raw).terms())
        }
    }

    @Test fun invalidFrequencyIsRejectedBeforeOtherInvalidTermsWithTheExistingMessage() {
        val failure = assertFailsWith<IllegalArgumentException> {
            terms().copy(frequency = 0, maturityDate = LocalDate(2025, 1, 1), faceValue = -1.0)
        }
        assertEquals("Coupon frequency must be between 1 and 12.", failure.message)
    }

    @Test fun publishedDatesRemainUsableWhenFrequencyCannotBeInferredInCalendarMonths() {
        val date = LocalDate(2027, 1, 1)
        assertTrue(couponSchedule(terms(7)).isEmpty())
        assertEquals(listOf(date), couponSchedule(terms(7).copy(confirmedDates = listOf(date, date))))
        assertEquals(57_000.0, terms().netCouponPayment())
        assertEquals(60_000.0, terms().grossCouponPayment())
    }
}

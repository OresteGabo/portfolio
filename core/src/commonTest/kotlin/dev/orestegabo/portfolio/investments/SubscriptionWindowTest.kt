package dev.orestegabo.portfolio.investments

import kotlinx.datetime.*
import kotlin.test.*

class SubscriptionWindowTest {
    @Test fun statusUsesInclusiveDateBoundariesAndSingleDayIsClosingToday() {
        val window = SubscriptionWindow(LocalDate(2026, 12, 30), LocalDate(2027, 1, 2))
        assertEquals(SubscriptionStatus.UPCOMING, window.statusOn(LocalDate(2026, 12, 29)))
        assertEquals(SubscriptionStatus.OPEN, window.statusOn(window.start))
        assertEquals(SubscriptionStatus.OPEN, window.statusOn(LocalDate(2027, 1, 1)))
        assertEquals(SubscriptionStatus.CLOSING_TODAY, window.statusOn(window.end))
        assertEquals(SubscriptionStatus.CLOSED, window.statusOn(LocalDate(2027, 1, 3)))
        assertEquals(SubscriptionStatus.CLOSING_TODAY, SubscriptionWindow(window.start, window.start).statusOn(window.start))
    }

    @Test fun interiorExpansionKeepsOriginalTwoToThirtyOneDayLimit() {
        val first = LocalDate(2026, 1, 1)
        for ((length, count) in listOf(-1 to 0, 0 to 0, 1 to 0, 2 to 1, 31 to 30, 32 to 0)) {
            val dates = SubscriptionWindow(first, first.plus(DatePeriod(days = length))).intermediateOpenDays()
            assertEquals(count, dates.size)
            assertTrue(dates.all { it > first && it < first.plus(DatePeriod(days = length)) })
        }
    }

    @Test fun monthOverlapIncludesEndpointsAndRespectsLeapYearAndYearBoundary() {
        val window = SubscriptionWindow(LocalDate(2023, 12, 31), LocalDate(2024, 2, 29))
        assertTrue(window.overlapsMonth(LocalDate(2023, 12, 1)))
        assertTrue(window.overlapsMonth(LocalDate(2024, 1, 17)))
        assertTrue(window.overlapsMonth(LocalDate(2024, 2, 1)))
        assertFalse(window.overlapsMonth(LocalDate(2024, 3, 1)))
        assertFalse(window.overlapsMonth(LocalDate(2023, 11, 1)))
    }
}

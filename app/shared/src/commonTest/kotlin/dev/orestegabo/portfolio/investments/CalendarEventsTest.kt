package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class CalendarEventsTest {
    @Test fun mondayFirstGridIncludesLeapDayAndYearBoundary() {
        val february = calendarMonthDays(LocalDate(2024, 2, 15))
        assertEquals(42, february.size)
        assertEquals(LocalDate(2024, 1, 29), february.first())
        assertTrue(LocalDate(2024, 2, 29) in february)
        assertEquals(29, february.count { it.month == kotlinx.datetime.Month.FEBRUARY })
        val january = calendarMonthDays(LocalDate(2027, 1, 1))
        assertEquals(LocalDate(2026, 12, 28), january.first())
        assertEquals(42, january.distinct().size)
    }

    @Test fun monthNavigationClampsEndOfMonthAndMovesAcrossYears() {
        assertEquals(LocalDate(2024, 2, 29), shiftCalendarMonth(LocalDate(2024, 1, 31), 1))
        assertEquals(LocalDate(2027, 1, 23), shiftCalendarMonth(LocalDate(2026, 12, 23), 1))
        assertEquals(LocalDate(2026, 12, 23), shiftCalendarMonth(LocalDate(2027, 1, 23), -1))
    }

    @Test fun issuanceMilestonesAndOpenWindowAppearOnTheirActualDates() {
        val issuance = JsonRecord().put("id", "october").put("title", "20-year bond")
            .put("announcementDate", "2026-10-01").put("openBookDate", "2026-10-19")
            .put("closingBookDate", "2026-10-21").put("settlementDate", "2026-10-23")
        val events = investmentCalendarEvents(listOf(issuance), PortfolioSnapshot())
        assertEquals(listOf("Announcement", "Book opens", "Book open", "Book closes", "Settlement"), events.map { it.label })
        assertEquals(LocalDate(2026, 10, 20), events.single { it.label == "Book open" }.date)
        assertTrue(events.all { it.matches(CalendarFilter.ISSUANCES) })
        assertTrue(events.none { it.matches(CalendarFilter.PAYMENTS) })
    }

    @Test fun paymentsUseActivePurchasesAndIncludeFinalCouponAndPrincipalOnMaturity() {
        fun purchase(id: String, status: String) = JsonRecord().put("id", id).put("status", status)
            .put("bondName", "Test bond").put("purchaseDate", "2026-01-01").put("maturityDate", "2027-01-01")
            .put("faceValue", 100000).put("couponRate", 0.1).put("withholdingTaxRate", 0.05)
            .put("couponFrequency", 2).put("currency", "RWF")
        val active = purchase("active", "active")
        val events = investmentCalendarEvents(emptyList(), PortfolioSnapshot(purchases = listOf(active, purchase("sold", "sold"))))
        assertEquals(3, events.size)
        assertTrue(events.all { it.record === active && it.matches(CalendarFilter.PAYMENTS) })
        assertEquals(4750.0, events.first().amount)
        val maturity = events.filter { it.date == LocalDate(2027, 1, 1) }
        assertEquals(setOf(CalendarCategory.COUPON, CalendarCategory.MATURITY), maturity.map { it.category }.toSet())
        assertEquals(100000.0, maturity.single { it.category == CalendarCategory.MATURITY }.amount)
    }

    @Test fun invalidIssuanceDatesAreSkippedWithoutBreakingTheCalendar() {
        val events = investmentCalendarEvents(listOf(JsonRecord().put("id", "bad").put("openBookDate", "bad")
            .put("closingBookDate", "2026-10-21")), PortfolioSnapshot())
        assertEquals(1, events.size)
        assertEquals("Book closes", events.single().label)
    }

    @Test fun subscriptionRangesUseInclusiveRealDatesAndCrossMonthBoundaries() {
        fun row(id: String, start: String, end: String) = JsonRecord().put("id", id)
            .put("openBookDate", start).put("closingBookDate", end)
        val ranges = calendarSubscriptionRanges(listOf(
            row("cross", "2026-12-30", "2027-01-04"),
            row("single", "2027-01-12", "2027-01-12"),
            row("reversed", "2027-01-20", "2027-01-15"),
            row("invalid", "bad", "2027-01-15")))
        assertEquals(listOf("cross", "single"), ranges.map { it.id })
        assertTrue(LocalDate(2026, 12, 30) in ranges.first())
        assertTrue(LocalDate(2027, 1, 4) in ranges.first())
        assertFalse(LocalDate(2027, 1, 5) in ranges.first())
        assertEquals(listOf("cross"), subscriptionRangesInMonth(ranges, LocalDate(2026, 12, 1)).map { it.id })
        assertEquals(2, subscriptionRangesInMonth(ranges, LocalDate(2027, 1, 1)).size)
        assertTrue(subscriptionRangesInMonth(ranges, LocalDate(2027, 2, 1)).isEmpty())
    }

    @Test fun automaticRangeFocusChoosesCurrentThenUpcomingThenMostRecent() {
        val ranges = calendarSubscriptionRanges(listOf(
            JsonRecord().put("id", "early").put("openBookDate", "2026-10-05").put("closingBookDate", "2026-10-07"),
            JsonRecord().put("id", "late").put("openBookDate", "2026-10-19").put("closingBookDate", "2026-10-21")))
        assertEquals("early", preferredSubscriptionRange(ranges, LocalDate(2026, 10, 1))?.id)
        assertEquals("early", preferredSubscriptionRange(ranges, LocalDate(2026, 10, 6))?.id)
        assertEquals("late", preferredSubscriptionRange(ranges, LocalDate(2026, 10, 10))?.id)
        assertEquals("late", preferredSubscriptionRange(ranges, LocalDate(2026, 10, 23))?.id)
        assertNull(preferredSubscriptionRange(emptyList(), LocalDate(2026, 10, 23)))
    }
    @Test fun monthlyAgendaIncludesAllDatesWithoutRepeatingEveryOpenBookDay() {
        val issuance = JsonRecord().put("id", "october").put("title", "20-year bond")
            .put("announcementDate", "2026-10-01").put("openBookDate", "2026-10-19")
            .put("closingBookDate", "2026-10-21").put("settlementDate", "2026-10-23")
        val events = investmentCalendarEvents(listOf(issuance), PortfolioSnapshot())
        val agenda = calendarMonthEvents(events, LocalDate(2026, 10, 24))
        assertEquals(listOf("Announcement", "Book opens", "Book closes", "Settlement"), agenda.map { it.label })
        assertEquals(agenda, calendarMonthEvents(events, LocalDate(2026, 10, 1)))
        assertTrue(calendarMonthEvents(events, LocalDate(2026, 11, 1)).isEmpty())
        assertEquals(listOf(1, 19, 21, 23), agenda.map { it.date.day })
    }

}

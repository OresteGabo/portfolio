package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

/** Public calendar contracts remain stable when business rules move out of presentation. */
class CalendarDomainBoundaryTest {
    private fun bond(id: String, status: String = "active", rate: Double = .1) = JsonRecord()
        .put("id", id).put("status", status).put("bondName", "Test bond")
        .put("purchaseDate", "2026-01-01").put("maturityDate", "2027-01-01")
        .put("faceValue", 100000).put("couponRate", rate).put("withholdingTaxRate", .05)
        .put("couponFrequency", 2).put("couponDates", JsonList(listOf("2026-07-01", "2027-01-01")))

    @Test fun cashFlowAdapterPreservesAmountsIdsLabelsOrderingAndOriginalEvidence() {
        val active = bond("active")
        val pending = bond("pending", "submitted", 0.0)
        val events = investmentCalendarEvents(emptyList(), PortfolioSnapshot(listOf(active, pending, bond("sold", "sold"))))
        assertEquals(listOf("active:coupon:2026-07-01", "active:coupon:2027-01-01", "active:maturity", "pending:maturity"), events.map { it.id })
        assertEquals(listOf("Expected coupon", "Expected coupon", "Principal due", "Principal due"), events.map { it.label })
        assertEquals(listOf(4750.0, 4750.0, 100000.0, 100000.0), events.map { it.amount })
        assertTrue(events.take(3).all { it.record === active })
        assertSame(pending, events.last().record)
        assertEquals(listOf(CalendarCategory.COUPON, CalendarCategory.COUPON, CalendarCategory.MATURITY, CalendarCategory.MATURITY), events.map { it.category })
    }

    @Test fun subscriptionAdapterPreservesInteriorDaysAgendaAndClosingDayCopy() {
        val issue = JsonRecord().put("id", "year-end").put("title", "Year-end issue")
            .put("openBookDate", "2026-12-30").put("closingBookDate", "2027-01-02")
        val events = investmentCalendarEvents(listOf(issue), PortfolioSnapshot())
        assertEquals(listOf("year-end:openBookDate", "year-end:open:2026-12-31", "year-end:open:2027-01-01", "year-end:closingBookDate"), events.map { it.id })
        assertTrue(events.all { it.record === issue })
        assertEquals(listOf("Book closes"), calendarMonthEvents(events, LocalDate(2027, 1, 1)).map { it.label })
        val range = calendarSubscriptionRanges(listOf(issue)).single()
        assertEquals("Upcoming", range.statusOn(LocalDate(2026, 12, 29)).label)
        assertEquals("Subscriptions open", range.statusOn(LocalDate(2027, 1, 1)).label)
        assertEquals("Closing today", range.statusOn(LocalDate(2027, 1, 2)).label)
        assertEquals("Subscriptions closed", range.statusOn(LocalDate(2027, 1, 3)).label)
        assertEquals(listOf(range), subscriptionRangesInMonth(listOf(range), LocalDate(2027, 1, 17)))
    }
}

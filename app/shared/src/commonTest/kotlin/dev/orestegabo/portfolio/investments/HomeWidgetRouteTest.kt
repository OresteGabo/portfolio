package dev.orestegabo.portfolio.investments

import kotlin.test.*

class HomeWidgetRouteTest {
    @Test fun aHandledCalendarRequestIsConsumedOnlyOnce() {
        try {
            HomeWidgetBridge.openCalendar("2030-02-18")
            val revision = HomeWidgetBridge.routeRevision
            assertEquals("2030-02-18", HomeWidgetBridge.consumeCalendarRequest(revision))
            assertNull(HomeWidgetBridge.requestedDate)
            assertNull(HomeWidgetBridge.consumeCalendarRequest(revision))
        } finally { HomeWidgetBridge.consumeCalendarRequest(HomeWidgetBridge.routeRevision) }
    }

    @Test fun aStaleConsumerCannotClearANewerRequestAndInvalidDatesCannotReplaceIt() {
        try {
            HomeWidgetBridge.openCalendar("2030-02-18")
            val oldRevision = HomeWidgetBridge.routeRevision
            HomeWidgetBridge.openCalendar("2030-03-19")
            HomeWidgetBridge.openCalendar("not-a-date")
            assertNull(HomeWidgetBridge.consumeCalendarRequest(oldRevision))
            assertEquals("2030-03-19", HomeWidgetBridge.requestedDate)
            assertEquals("2030-03-19", HomeWidgetBridge.consumeCalendarRequest(HomeWidgetBridge.routeRevision))
        } finally { HomeWidgetBridge.consumeCalendarRequest(HomeWidgetBridge.routeRevision) }
    }
}

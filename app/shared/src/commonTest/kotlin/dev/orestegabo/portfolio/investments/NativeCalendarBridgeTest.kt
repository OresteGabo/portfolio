package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class NativeCalendarBridgeTest {
    @Test fun personalPaymentExportsOnlyGenericLabelAndDate() {
        val private = JsonRecord().put("broker","Private broker").put("faceValue",123456789)
        for (category in listOf(CalendarCategory.COUPON, CalendarCategory.MATURITY)) {
            val draft = phoneCalendarEvent(InvestmentCalendarEvent("private-id", LocalDate(2028,2,29),
                "Private label", "Private bond name",category,private,123456789.0))
            assertEquals("2028-02-29", draft.date)
            assertFalse((draft.title+draft.notes).contains("Private"))
            assertFalse((draft.title+draft.notes).contains("123456789"))
            assertTrue(draft.notes.contains("structcapital://calendar?date=2028-02-29"))
        }
    }
    @Test fun publicIssueKeepsItsMilestone() {
        val draft = phoneCalendarEvent(InvestmentCalendarEvent("public",LocalDate(2028,2,29),
            "Book closes","Public Treasury bond",CalendarCategory.ISSUANCE,JsonRecord()))
        assertTrue(draft.title.contains(localized("Book closes")))
        assertTrue(draft.title.contains("Public Treasury bond"))
    }
}

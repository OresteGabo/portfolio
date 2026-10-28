package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class CalendarPurchaseStatusTest {
    private fun issue() = JsonRecord().put("isin", "RWTEST000001/MN").put("settlementDate", "2026-10-23")
        .put("maturityDate", "2046-09-28").put("openBookDate", "2026-10-19").put("closingBookDate", "2026-10-21")
    private fun purchase(status: String = "active") = JsonRecord(issue().toString()).put("id", "purchase")
        .put("status", status).put("confirmationDocumentId", "contract")
    private val today = LocalDate(2026,10,20)
    private fun summary(purchases: List<JsonRecord>, complete: Boolean = true, documents: List<JsonRecord> = emptyList()) =
        calendarIssuePurchases(issue(), purchases, documents, complete)

    @Test fun confirmedPurchaseWithItsPdfNeverSuggestsAnotherBid() {
        val state = summary(listOf(purchase()), documents = listOf(JsonRecord().put("id", "contract")))
        assertEquals(CalendarPurchaseStatus.CONFIRMED_WITH_PDF, state.status)
        assertEquals("Your purchase is confirmed and its confirmation PDF is saved. No new bid is needed for this purchase.", calendarIssueGuidance(issue(), state, today))
        assertEquals(calendarIssueGuidance(issue(), state, today), calendarIssueGuidance(issue(), state, LocalDate(2026,10,28)))
    }
    @Test fun confirmedWithoutAnAvailablePdfDoesNotAskToBidOrClaimTheFileExists() {
        val state = summary(listOf(purchase()))
        assertEquals(CalendarPurchaseStatus.CONFIRMED, state.status)
        assertEquals("Your purchase is already confirmed. No new bid is needed for this purchase.", calendarIssueGuidance(issue(), state, today))
    }
    @Test fun pendingAndMixedApplicationsRemainDistinctFromConfirmedPurchases() {
        val pending = summary(listOf(purchase("submitted")))
        assertEquals(CalendarPurchaseStatus.PENDING, pending.status)
        assertTrue(calendarIssueGuidance(issue(), pending, today).contains("Do not submit the same application again"))
        val mixed = summary(listOf(purchase(), purchase("submitted")))
        assertEquals(CalendarPurchaseStatus.MIXED, mixed.status)
        assertTrue(calendarIssueGuidance(issue(), mixed, today).contains("another application awaiting confirmation"))
    }
    @Test fun aSharedIssuanceDocumentAloneDoesNotCountAsAPersonalPurchase() {
        val state = summary(emptyList(), documents = listOf(JsonRecord().put("id", "contract").put("category", "Bond issuance")))
        assertEquals(CalendarPurchaseStatus.NONE, state.status)
        assertTrue(calendarIssueGuidance(issue(), state, today).startsWith("If you want to apply"))
    }
    @Test fun conflictingSecuritiesAndDifferentReopeningDatesDoNotMatch() {
        assertEquals(CalendarPurchaseStatus.NONE, summary(listOf(purchase().put("isin", "DIFFERENT"))).status)
        assertEquals(CalendarPurchaseStatus.NONE, summary(listOf(purchase().put("settlementDate", "2026-12-18"))).status)
        assertEquals(CalendarPurchaseStatus.CONFIRMED, summary(listOf(purchase().put("isin", " rwtest000001 "))).status)
    }
    @Test fun publishedRowsWithoutSecurityCodesUseRealSettlementAndMaturityDates() {
        val published = JsonRecord(issue().toString()).put("isin", "")
        assertEquals(CalendarPurchaseStatus.CONFIRMED, calendarIssuePurchases(published, listOf(purchase()), emptyList(), true).status)
        assertEquals(CalendarPurchaseStatus.NONE, calendarIssuePurchases(published, listOf(purchase().put("maturityDate", "2040-01-01")), emptyList(), true).status)
        assertEquals(CalendarPurchaseStatus.NONE, calendarIssuePurchases(JsonRecord(), listOf(JsonRecord().put("status", "active")), emptyList(), true).status)
    }
    @Test fun incompleteHistoryDoesNotProduceAnUninformedBidPrompt() {
        val unknown = summary(emptyList(), complete = false)
        assertEquals(CalendarPurchaseStatus.UNAVAILABLE, unknown.status)
        assertTrue(calendarIssueGuidance(issue(), unknown, today).contains("has not been checked"))
        assertEquals(CalendarPurchaseStatus.CONFIRMED, summary(listOf(purchase()), complete = false).status)
    }
    @Test fun oldCalendarMilestonesUseTodaysActualSubscriptionWindow() {
        val state = summary(emptyList())
        assertTrue(calendarIssueGuidance(issue(), state, LocalDate(2026,10,18)).contains("before the book opens"))
        assertTrue(calendarIssueGuidance(issue(), state, LocalDate(2026,10,21)).contains("today’s closing deadline"))
        assertTrue(calendarIssueGuidance(issue(), state, LocalDate(2026,10,28)).startsWith("The subscription period has ended"))
    }
    @Test fun anotherCurrencyOrACorporateBondCannotMatchTheTreasuryCalendar() {
        assertEquals(CalendarPurchaseStatus.NONE, summary(listOf(purchase().put("currency", "USD"))).status)
        assertEquals(CalendarPurchaseStatus.NONE, summary(listOf(purchase().put("instrumentType", "corporate"))).status)
        assertEquals(CalendarPurchaseStatus.CONFIRMED, summary(listOf(purchase().put("currency", "RWF").put("instrumentType", "treasury"))).status)
    }
    @Test fun cancelledApplicationsDoNotPretendToBeCompletedPurchases() {
        assertEquals(CalendarPurchaseStatus.NONE, summary(listOf(purchase("cancelled"))).status)
        assertEquals(CalendarPurchaseStatus.RECORDED, summary(listOf(purchase("sold"))).status)
    }
}

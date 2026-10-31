package dev.orestegabo.portfolio.investments

import kotlin.test.*

class NotificationRoutingTest {
    private fun event(kind: String) = JsonRecord().put("id", "notification-1").put("kind", kind).put("action", "settings")
    @Test fun legacySettingsActionsResolveByTheirActualCategory() {
        assertEquals(NotificationDestination.SECURITY, notificationDestination(event("security")))
        assertEquals(NotificationDestination.TARGET, notificationDestination(event("targets")))
        assertEquals(NotificationDestination.REPORT, notificationDestination(event("reports")))
        assertEquals("Review sign-in activity", notificationDestination(event("security"))!!.label)
    }
    @Test fun financialLinksKeepTheirExactDateAndRecordContext() {
        assertEquals(NotificationDestination.CALENDAR, notificationDestination(event("coupons").put("dueDate", "2027-01-15")))
        assertEquals(NotificationDestination.CALENDAR, notificationDestination(event("maturities").put("dueDate", "2033-07-08")))
        assertEquals(NotificationDestination.PURCHASE, notificationDestination(event("pending").put("purchaseId", "purchase-1")))
        assertEquals(NotificationDestination.DOCUMENT, notificationDestination(event("documents").put("statementId", "statement-1")))
    }
    @Test fun unsupportedOrInvalidTargetsDoNotFallBackToUnrelatedSettings() {
        assertNull(notificationDestination(event("unknown")))
        assertNull(notificationDestination(event("prices")))
        assertNull(notificationDestination(event("pending")))
        assertNull(notificationDestination(event("documents").put("statementId", "../auth/session")))
        assertNull(notificationDestination(event("coupons").put("dueDate", "2027-02-30")))
        assertNull(notificationDestination(event("security").put("id", "")))
    }
}

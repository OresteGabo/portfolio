package dev.orestegabo.portfolio.investments

internal enum class NotificationDestination(val label: String) {
    CALENDAR("View calendar"), PURCHASE("Upload confirmation"), DOCUMENT("Review missing document"),
    SECURITY("Review sign-in activity"), TARGET("Edit monthly target"), REPORT("View report options")
}

/** Older stored events use action=settings; kind still identifies their specific destination. */
internal fun notificationDestination(item: JsonRecord): NotificationDestination? {
    fun validId(key: String) = item.text(key).matches(Regex("[A-Za-z0-9_-]{1,120}"))
    return when (item.text("kind")) {
        "security" -> if (validId("id")) NotificationDestination.SECURITY else null
        "targets" -> NotificationDestination.TARGET
        "reports" -> NotificationDestination.REPORT
        "pending" -> if (validId("purchaseId")) NotificationDestination.PURCHASE else null
        "documents" -> if (validId("statementId")) NotificationDestination.DOCUMENT else null
        "coupons", "maturities", "subscriptions" -> if (calendarDate(item.text("dueDate")) != null) NotificationDestination.CALENDAR else null
        else -> null
    }
}

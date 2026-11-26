package dev.orestegabo.portfolio.investments

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

internal enum class NotificationDateGroup(val title: String) {
    TODAY("Today"), YESTERDAY("Yesterday"), OLDER("Older"), OTHER("Other dates")
}

/** Uses the inbox's Rwanda market-day reference, never a coupon due date or the phone's local timezone. */
internal fun notificationDateGroups(notifications: List<JsonRecord>, today: LocalDate): Map<NotificationDateGroup, List<JsonRecord>> {
    val yesterday=today-DatePeriod(days=1)
    val dated=notifications.map { it to runCatching { LocalDate.parse(it.text("date")) }.getOrNull() }
        .sortedWith(compareByDescending<Pair<JsonRecord, LocalDate?>> { it.second })
    val grouped=dated.groupBy({ (_,date) -> when {
        date==today -> NotificationDateGroup.TODAY
        date==yesterday -> NotificationDateGroup.YESTERDAY
        date!=null && date<yesterday -> NotificationDateGroup.OLDER
        else -> NotificationDateGroup.OTHER
    } }, { it.first })
    return NotificationDateGroup.entries.mapNotNull { group -> grouped[group]?.let { group to it } }.toMap()
}

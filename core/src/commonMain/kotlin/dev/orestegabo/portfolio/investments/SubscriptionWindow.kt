package dev.orestegabo.portfolio.investments

import kotlinx.datetime.*

/** Date-based phases only: published calendar dates provide no intraday closing cutoff. */
enum class SubscriptionStatus { UPCOMING, OPEN, CLOSING_TODAY, CLOSED }

/** A published window. Parsing/accepting source records belongs to the caller. */
data class SubscriptionWindow(val start: LocalDate, val end: LocalDate) {
    fun statusOn(today: LocalDate): SubscriptionStatus = when {
        today < start -> SubscriptionStatus.UPCOMING
        today > end -> SubscriptionStatus.CLOSED
        today == end -> SubscriptionStatus.CLOSING_TODAY
        else -> SubscriptionStatus.OPEN
    }

    /** Preserve the existing bounded expansion; endpoints are separate published milestones. */
    fun intermediateOpenDays(): List<LocalDate> {
        val days = start.daysUntil(end)
        if (days !in 2..31) return emptyList()
        return (1 until days).map { start.plus(DatePeriod(days = it)) }
    }

    fun overlapsMonth(month: LocalDate): Boolean {
        val first = LocalDate(month.year, month.month, 1)
        val next = first.plus(DatePeriod(months = 1))
        return start < next && end >= first
    }
}

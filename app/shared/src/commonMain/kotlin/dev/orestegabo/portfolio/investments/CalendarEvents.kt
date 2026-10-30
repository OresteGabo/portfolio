package dev.orestegabo.portfolio.investments

import kotlinx.datetime.*

enum class CalendarCategory(val label: String) { ISSUANCE("Issuances"), COUPON("Coupons"), MATURITY("Maturities") }
enum class CalendarFilter(val label: String) { ALL("All events"), ISSUANCES("Issuances"), PAYMENTS("My payments") }

data class InvestmentCalendarEvent(
    val id: String,
    val date: LocalDate,
    val label: String,
    val title: String,
    val category: CalendarCategory,
    val record: JsonRecord,
    val amount: Double? = null,
) {
    fun matches(filter: CalendarFilter) = when (filter) {
        CalendarFilter.ALL -> true
        CalendarFilter.ISSUANCES -> category == CalendarCategory.ISSUANCE
        CalendarFilter.PAYMENTS -> category != CalendarCategory.ISSUANCE
    }
}

/** Monday-first, fixed six-week grid, including adjacent-month dates. */
fun calendarMonthDays(month: LocalDate): List<LocalDate> {
    val first = LocalDate(month.year, month.month, 1)
    val start = first.minus(DatePeriod(days = first.dayOfWeek.ordinal))
    return (0 until 42).map { start.plus(DatePeriod(days = it)) }
}


fun investmentCalendarEvents(issuances: List<JsonRecord>, portfolio: PortfolioSnapshot): List<InvestmentCalendarEvent> {
    val events = mutableListOf<InvestmentCalendarEvent>()
    issuances.forEach { record ->
        val id = record.text("id")
        fun milestone(field: String, label: String) {
            calendarDate(record.text(field))?.let { date -> events += InvestmentCalendarEvent(
                "$id:$field", date, label, record.text("title"), CalendarCategory.ISSUANCE, record) }
        }
        milestone("announcementDate", "Announcement")
        milestone("openBookDate", "Book opens")
        milestone("closingBookDate", "Book closes")
        milestone("settlementDate", "Settlement")
        val open = calendarDate(record.text("openBookDate"))
        val close = calendarDate(record.text("closingBookDate"))
        if (open != null && close != null && open.daysUntil(close) in 2..31) {
            (1 until open.daysUntil(close)).forEach { offset ->
                val date = open.plus(DatePeriod(days = offset))
                events += InvestmentCalendarEvent("$id:open:$date", date, "Book open", record.text("title"), CalendarCategory.ISSUANCE, record)
            }
        }
    }
    portfolio.purchases.filter { it.text("status") == "active" }.forEach { purchase ->
        purchase.terms()?.let { terms ->
            val tracking = trackBond(terms, terms.purchaseDate)
            tracking.schedule.forEach { date -> events += InvestmentCalendarEvent(
                "${purchase.text("id")}:coupon:$date", date, "Expected coupon", purchase.text("bondName"),
                CalendarCategory.COUPON, purchase, tracking.netPerPayment) }
            events += InvestmentCalendarEvent("${purchase.text("id")}:maturity", terms.maturityDate, "Principal due",
                purchase.text("bondName"), CalendarCategory.MATURITY, purchase, terms.faceValue)
        }
    }
    return events.sortedWith(compareBy({ it.date }, { it.category.ordinal }, { it.title }, { it.id }))
}

internal fun LocalDate.monthLabel() = localized(month.name.lowercase().replaceFirstChar { it.uppercase() })
internal fun LocalDate.dayLabel() = localized(dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() })
internal fun LocalDate.calendarLabel() = "${dayLabel()}, $day ${monthLabel()} $year"

/** A real subscription window, never an inferred interval between unrelated payments. */
data class CalendarSubscriptionRange(val record: JsonRecord, val start: LocalDate, val end: LocalDate) {
    val id get() = record.text("id")
    operator fun contains(date: LocalDate) = date >= start && date <= end
}

fun calendarSubscriptionRanges(issuances: List<JsonRecord>): List<CalendarSubscriptionRange> =
    issuances.mapNotNull { record ->
        val start = calendarDate(record.text("openBookDate"))
        val end = calendarDate(record.text("closingBookDate"))
        if (start != null && end != null && end >= start) CalendarSubscriptionRange(record, start, end) else null
    }.sortedWith(compareBy({ it.start }, { it.end }, { it.id }))

fun subscriptionRangesInMonth(ranges: List<CalendarSubscriptionRange>, month: LocalDate): List<CalendarSubscriptionRange> {
    val first = LocalDate(month.year, month.month, 1)
    val next = first.plus(DatePeriod(months = 1))
    return ranges.filter { it.start < next && it.end >= first }
}

fun preferredSubscriptionRange(ranges: List<CalendarSubscriptionRange>, anchor: LocalDate): CalendarSubscriptionRange? =
    ranges.firstOrNull { anchor in it } ?: ranges.firstOrNull { it.start >= anchor } ?: ranges.lastOrNull()

/** Agenda keeps the displayed month's meaningful dates, without repeating every open-book day. */
fun calendarMonthEvents(events: List<InvestmentCalendarEvent>, month: LocalDate) = events.filter {
    it.date.year == month.year && it.date.month == month.month && it.label != "Book open"
}.sortedWith(compareBy({ it.date }, { it.category.ordinal }, { it.title }, { it.id }))

/** Date-based status from the published window; closing-day cutoff times are not provided. */
enum class SubscriptionStatus(val label: String) {
    UPCOMING("Upcoming"), OPEN("Subscriptions open"), CLOSING_TODAY("Closing today"), CLOSED("Subscriptions closed")
}

fun CalendarSubscriptionRange.statusOn(today: LocalDate): SubscriptionStatus = when {
    today < start -> SubscriptionStatus.UPCOMING
    today > end -> SubscriptionStatus.CLOSED
    today == end -> SubscriptionStatus.CLOSING_TODAY
    else -> SubscriptionStatus.OPEN
}

/** BNR subscription dates follow the market's date, regardless of the phone's timezone. */
internal fun bondSubscriptionDate(now: kotlin.time.Instant): LocalDate =
    now.toLocalDateTime(TimeZone.of("Africa/Kigali")).date

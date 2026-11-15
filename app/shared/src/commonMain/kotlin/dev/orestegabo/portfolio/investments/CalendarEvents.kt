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
        if (open != null && close != null) {
            SubscriptionWindow(open, close).intermediateOpenDays().forEach { date ->
                events += InvestmentCalendarEvent("$id:open:$date", date, "Book open", record.text("title"), CalendarCategory.ISSUANCE, record)
            }
        }
    }
    portfolio.purchases.filter { it.text("status") in setOf("active", "submitted") }.forEach { purchase ->
        purchase.terms()?.let { terms ->
            scheduledBondPayments(terms).forEach { flow ->
                val coupon = flow.kind == BondCashFlowKind.COUPON
                events += InvestmentCalendarEvent(
                    if (coupon) "${purchase.text("id")}:coupon:${flow.date}" else "${purchase.text("id")}:maturity",
                    flow.date, if (coupon) "Expected coupon" else "Principal due", purchase.text("bondName"),
                    if (coupon) CalendarCategory.COUPON else CalendarCategory.MATURITY, purchase, flow.amount)
            }
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

fun subscriptionRangesInMonth(ranges: List<CalendarSubscriptionRange>, month: LocalDate): List<CalendarSubscriptionRange> =
    ranges.filter { SubscriptionWindow(it.start, it.end).overlapsMonth(month) }

fun preferredSubscriptionRange(ranges: List<CalendarSubscriptionRange>, anchor: LocalDate): CalendarSubscriptionRange? =
    ranges.firstOrNull { anchor in it } ?: ranges.firstOrNull { it.start >= anchor } ?: ranges.lastOrNull()

/** Agenda keeps the displayed month's meaningful dates, without repeating every open-book day. */
fun calendarMonthEvents(events: List<InvestmentCalendarEvent>, month: LocalDate) = events.filter {
    it.date.year == month.year && it.date.month == month.month && it.label != "Book open"
}.sortedWith(compareBy({ it.date }, { it.category.ordinal }, { it.title }, { it.id }))

/** Presentation copy is separate from the domain's date-based subscription phases. */
val SubscriptionStatus.label: String get() = when (this) {
    SubscriptionStatus.UPCOMING -> "Upcoming"
    SubscriptionStatus.OPEN -> "Subscriptions open"
    SubscriptionStatus.CLOSING_TODAY -> "Closing today"
    SubscriptionStatus.CLOSED -> "Subscriptions closed"
}

fun CalendarSubscriptionRange.statusOn(today: LocalDate): SubscriptionStatus =
    SubscriptionWindow(start, end).statusOn(today)

/** BNR subscription dates follow the market's date, regardless of the phone's timezone. */
internal fun bondSubscriptionDate(now: kotlin.time.Instant): LocalDate =
    now.toLocalDateTime(TimeZone.of("Africa/Kigali")).date

/** Reopenings reuse a security's verified coupon, never a guessed tenor/title match or its yield. */
internal fun calendarCouponRate(issue: JsonRecord, documents: List<JsonRecord>, couponTerms: List<JsonRecord> = emptyList()): Double? {
    val direct = issue.optJSONObject("issuance")
    fun rate(record: JsonRecord) = record.optDouble("couponRate", Double.NaN).takeIf { it.isFinite() && it in 0.0..1.0 }
    direct?.let { rate(it)?.let { value -> return value } }
    val isin = issue.text("isin").trim().uppercase().removeSuffix("/MN")
    val maturity = calendarDate(issue.text("maturityDate")) ?: return null
    if (isin.isBlank()) return null
    val published=documents.filter { it.text("category") == "Bond issuance" }.mapNotNull { it.optJSONObject("issuance") }
    val candidates=published.filter { it.text("isin").trim().uppercase().removeSuffix("/MN")==isin && calendarDate(it.text("maturityDate"))==maturity }
    return (if(candidates.isNotEmpty()) candidates else couponTerms)
        .filter { it.text("isin").trim().uppercase().removeSuffix("/MN") == isin && calendarDate(it.text("maturityDate")) == maturity }
        .mapNotNull(::rate).distinct().singleOrNull()
}

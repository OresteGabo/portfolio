package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

/** A due-date view over existing schedules and receipts, not another payment ledger. */
internal data class YearCoupon(
    val purchase: JsonRecord, val date: LocalDate, val expectedNet: Double?, val recordedAmount: Double?,
    val state: CouponReceiptState, val estimatedDate: Boolean,
) {
    val currency get() = purchase.text("currency")
    val key get() = "${purchase.text("id")}:$date"
}
internal data class YearCoupons(val rows: List<YearCoupon>, val incomplete: List<JsonRecord>)
internal data class YearCouponTotals(val scheduledNet: Double?, val confirmed: Double?, val automatic: Double?)

/** Null year is used only for year discovery; non-null selection remains a strict calendar-year view. */
internal fun yearlyCoupons(purchases: List<JsonRecord>, receipts: List<JsonRecord>, year: Int?,
    today: LocalDate, receiptsAvailable: Boolean): YearCoupons {
    val rows = mutableListOf<YearCoupon>()
    val incomplete = mutableListOf<JsonRecord>()
    for (purchase in purchases.filter { it.text("status") in setOf("active", "matured", "sold") && it.text("id").isNotBlank() }.distinctBy { it.text("id") }) {
        val bought = calendarDate(purchase.text("purchaseDate"))
        val maturity = calendarDate(purchase.text("maturityDate"))
        if (bought != null && bought > today) continue
        if (!purchase.text("currency").matches(Regex("[A-Z]{3}"))) { incomplete += purchase; continue }
        val evidence = if (!receiptsAvailable) emptyMap<LocalDate, JsonRecord>() else receipts.filter {
            it.text("purchaseId") == purchase.text("id") && it.text("currency").ifBlank { purchase.text("currency") } == purchase.text("currency") &&
                calendarDate(it.text("dueDate"))?.let { due -> (year == null || due.year == year) && due <= today } == true
        }.distinctBy { it.text("dueDate") }.associateBy { calendarDate(it.text("dueDate"))!! }
        val expected = linkedMapOf<LocalDate, Double>()
        if (purchase.text("status") != "sold" && (year == null || bought == null || bought.year <= year) && (year == null || maturity == null || maturity.year >= year)) {
            val terms = bondDetailTerms(purchase)?.takeIf { purchase.has("couponFrequency") && it.faceValue > 0 }
            if (terms == null) incomplete += purchase
            else if (terms.couponRate > 0) {
                val dates = couponSchedule(terms)
                if (dates.isEmpty()) incomplete += purchase
                for (due in dates.filter { (year == null || it.year == year) && (purchase.text("status") != "matured" || it <= today) })
                    expected[due] = terms.netCouponPayment()
            }
        }
        for (due in (expected.keys + evidence.keys).distinct().sorted()) {
            val receipt = evidence[due]
            rows += YearCoupon(purchase, due, expected[due], recordedCouponAmount(receipt, purchase.text("currency")),
                couponReceiptState(due, today, receiptsAvailable, receipt),
                expected.containsKey(due) && (purchase.text("scheduleConfidence") != "confirmed" ||
                    purchase.rowsOfDates().none { it == due }))
        }
    }
    return YearCoupons(rows.sortedWith(compareBy({ it.date }, { it.currency }, { it.purchase.text("bondName") }, { it.key })), incomplete)
}
private fun JsonRecord.rowsOfDates(): List<LocalDate> = optJSONArray("couponDates")?.let { dates ->
    (0 until dates.length()).mapNotNull { calendarDate(dates.getString(it)) }
}.orEmpty()

/** Currency is selected before aggregation; recorded amounts never fall back to expected amounts. */
internal fun yearlyCouponTotals(rows: List<YearCoupon>, currency: String, receiptsAvailable: Boolean): YearCouponTotals {
    val selected = rows.filter { it.currency == currency }
    fun recorded(state: CouponReceiptState): Double? {
        val entries = selected.filter { it.state == state }
        return if (!receiptsAvailable || entries.any { it.recordedAmount == null }) null else sumMoney(entries.mapNotNull { it.recordedAmount })
    }
    return YearCouponTotals(if (selected.isEmpty() || selected.any { it.expectedNet == null }) null else sumMoney(selected.mapNotNull { it.expectedNet }),
        recorded(CouponReceiptState.CONFIRMED), recorded(CouponReceiptState.AUTOMATIC))
}

internal data class NextCouponYear(val year: Int, val coupons: List<YearCoupon>)

/** Next populated schedule year, not a guessed date or a new forecast engine. */
internal fun nextCouponYear(purchases: List<JsonRecord>, receipts: List<JsonRecord>, afterYear: Int,
    today: LocalDate, receiptsAvailable: Boolean): NextCouponYear? {
    val upcoming = yearlyCoupons(purchases, receipts, null, today, receiptsAvailable).rows
        .filter { it.date.year > afterYear && it.expectedNet != null }
    val next = upcoming.firstOrNull()?.date?.year ?: return null
    return NextCouponYear(next, upcoming.filter { it.date.year == next })
}

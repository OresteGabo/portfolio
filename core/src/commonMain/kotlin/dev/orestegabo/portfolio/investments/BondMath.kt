package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.daysUntil
import kotlin.math.roundToInt

/** Mirrors lib/bonds/tracking.ts. Rates are fractions, dates are ISO calendar dates. */
data class BondTerms(
    val purchaseDate: LocalDate,
    val maturityDate: LocalDate,
    val faceValue: Double,
    val couponRate: Double,
    val taxRate: Double,
    val frequency: Int,
    val firstCoupon: LocalDate? = null,
    val confirmedDates: List<LocalDate> = emptyList(),
)
data class BondTracking(val schedule: List<LocalDate>, val netPerPayment: Double, val couponsPaid: Double,
    val accruedCoupon: Double, val remainingCoupons: Double, val nextCoupon: LocalDate?, val progress: Float)

fun couponSchedule(terms: BondTerms): List<LocalDate> {
    if (terms.confirmedDates.isNotEmpty()) return terms.confirmedDates.distinct().filter { it > terms.purchaseDate && it <= terms.maturityDate }.sorted()
    val interval = (12.0 / terms.frequency.coerceAtLeast(1)).roundToInt()
    val dates = mutableListOf<LocalDate>()
    if (terms.firstCoupon != null) {
        var date = terms.firstCoupon
        while (date != null && date <= terms.maturityDate) {
            if (date > terms.purchaseDate) dates += date
            date = date.plus(DatePeriod(months = interval))
        }
        if (dates.isNotEmpty() && dates.last() != terms.maturityDate) dates += terms.maturityDate
    } else {
        var date = terms.maturityDate
        while (date > terms.purchaseDate) {
            dates += date
            date = date.minus(DatePeriod(months = interval))
        }
        dates.reverse()
    }
    return dates
}

fun trackBond(terms: BondTerms, today: LocalDate): BondTracking {
    val schedule = couponSchedule(terms)
    val value = minOf(today, terms.maturityDate)
    val net = terms.faceValue * terms.couponRate * (1 - terms.taxRate) / terms.frequency.coerceAtLeast(1)
    val completed = if (today < terms.purchaseDate) emptyList() else schedule.filter { it <= value }
    val future = if (today < terms.purchaseDate) schedule else schedule.filter { it > value }
    val previous = completed.lastOrNull() ?: terms.purchaseDate
    val next = future.firstOrNull()
    val accrued = if (today >= terms.purchaseDate && next != null && value > previous) {
        net * (previous.daysUntil(value).toDouble() / previous.daysUntil(next).coerceAtLeast(1)).coerceAtMost(1.0)
    } else 0.0
    val progress = (terms.purchaseDate.daysUntil(value).coerceAtLeast(0).toDouble() /
        terms.purchaseDate.daysUntil(terms.maturityDate).coerceAtLeast(1)).coerceIn(0.0, 1.0).toFloat()
    return BondTracking(schedule, net, completed.size * net, accrued, future.size * net, next, progress)
}

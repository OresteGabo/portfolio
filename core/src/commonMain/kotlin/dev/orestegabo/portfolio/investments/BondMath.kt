package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.daysUntil

/** Client-side estimates, not persisted receipt evidence. Rates are fractions; dates are ISO dates.
 * Explicit published dates take precedence. Inferred dates use the original anchor to avoid month-end drift.
 */
data class BondTerms(
    val purchaseDate: LocalDate,
    val maturityDate: LocalDate,
    val faceValue: Double,
    val couponRate: Double,
    val taxRate: Double,
    val frequency: Int,
    val firstCoupon: LocalDate? = null,
    val confirmedDates: List<LocalDate> = emptyList(),
) {
    init {
        require(frequency in 1..12) { "Coupon frequency must be between 1 and 12." }
        require(maturityDate > purchaseDate && maturityDate <= purchaseDate.plus(DatePeriod(years = 100))) {
            "Bond dates must describe a positive term of at most 100 years."
        }
        require(firstCoupon == null || firstCoupon <= maturityDate && firstCoupon >= purchaseDate.minus(DatePeriod(years = 100))) {
            "The first coupon date is outside the supported schedule range."
        }
        require(faceValue.isFinite() && faceValue >= 0 && couponRate.isFinite() && couponRate in 0.0..1.0 &&
            taxRate.isFinite() && taxRate in 0.0..1.0) { "Bond amounts and rates must be finite and valid." }
    }
}
data class BondTracking(val schedule: List<LocalDate>, val netPerPayment: Double, val couponsPaid: Double,
    val accruedCoupon: Double, val remainingCoupons: Double, val nextCoupon: LocalDate?, val progress: Float) {
    /** Contractual amounts scheduled through the valuation date; never evidence of cash received. */
    val scheduledCouponsToDate get() = couponsPaid
}

fun couponSchedule(terms: BondTerms): List<LocalDate> {
    if (terms.confirmedDates.isNotEmpty()) return terms.confirmedDates.distinct().filter { it > terms.purchaseDate && it <= terms.maturityDate }.sorted()
    // A calendar-month schedule cannot represent e.g. seven equally spaced payments/year.
    // Published dates still work for those instruments; do not manufacture a calendar for them.
    if (12 % terms.frequency != 0) return emptyList()
    val interval = 12 / terms.frequency
    val dates = mutableListOf<LocalDate>()
    if (terms.firstCoupon != null) {
        var step = 0
        var date = terms.firstCoupon
        while (date != null && date <= terms.maturityDate) {
            if (date > terms.purchaseDate) dates += date
            date = terms.firstCoupon.plus(DatePeriod(months = interval * ++step))
        }
        if (dates.isNotEmpty() && dates.last() != terms.maturityDate) dates += terms.maturityDate
    } else {
        var step = 0
        var date = terms.maturityDate
        while (date > terms.purchaseDate) {
            dates += date
            date = terms.maturityDate.minus(DatePeriod(months = interval * ++step))
        }
        dates.reverse()
    }
    return dates
}

fun trackBond(terms: BondTerms, today: LocalDate): BondTracking {
    val schedule = couponSchedule(terms)
    val value = minOf(today, terms.maturityDate)
    val net = terms.netCouponPayment()
    val completed = if (today < terms.purchaseDate) emptyList() else schedule.filter { it <= value }
    val future = if (today < terms.purchaseDate) schedule else schedule.filter { it > value }
    val previous = completed.lastOrNull() ?: terms.purchaseDate
    val next = future.firstOrNull()
    val accrued = if (today >= terms.purchaseDate && next != null && value > previous) {
        decimalAmount(listOf(net, previous.daysUntil(value).toDouble()), previous.daysUntil(next).coerceAtLeast(1))
    } else 0.0
    val progress = (terms.purchaseDate.daysUntil(value).coerceAtLeast(0).toDouble() /
        terms.purchaseDate.daysUntil(terms.maturityDate).coerceAtLeast(1)).coerceIn(0.0, 1.0).toFloat()
    return BondTracking(schedule, net, decimalAmount(listOf(completed.size.toDouble(), net)), accrued, decimalAmount(listOf(future.size.toDouble(), net)), next, progress)
}

fun BondTerms.grossCouponPayment(): Double = decimalAmount(listOf(faceValue, couponRate), frequency)
fun BondTerms.netCouponPayment(): Double = decimalAmount(listOf(faceValue, couponRate), frequency, taxRate)

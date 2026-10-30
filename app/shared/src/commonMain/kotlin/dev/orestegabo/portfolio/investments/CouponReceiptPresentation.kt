package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

internal enum class CouponReceiptState(val label: String) {
    UPCOMING("Upcoming"), CONFIRMED("Confirmed by you"), AUTOMATIC("Marked automatically"),
    NOT_RECEIVED("Marked not received"), UNCONFIRMED("Not confirmed received"),
    UNAVAILABLE("Receipt status unavailable"), UNKNOWN_METHOD("Receipt method unavailable")
}

/** A scheduled date or automatic setting is not evidence of a manually verified receipt. */
internal fun couponReceiptState(due: LocalDate, asOf: LocalDate, available: Boolean, receipt: JsonRecord?): CouponReceiptState = when {
    due > asOf -> CouponReceiptState.UPCOMING
    !available -> CouponReceiptState.UNAVAILABLE
    receipt == null -> CouponReceiptState.UNCONFIRMED
    !receipt.optBoolean("received") -> CouponReceiptState.NOT_RECEIVED
    receipt.text("method") == "manual" -> CouponReceiptState.CONFIRMED
    receipt.text("method") == "automatic" -> CouponReceiptState.AUTOMATIC
    else -> CouponReceiptState.UNKNOWN_METHOD
}

internal fun recordedCouponAmount(receipt: JsonRecord?, currency: String): Double? = receipt?.takeIf {
    it.optBoolean("received") && it.text("currency").ifBlank { currency } == currency
}?.optDouble("amount", Double.NaN)?.takeIf { it.isFinite() && it >= 0 }

internal data class CouponReceiptTotals(val confirmedByUser: Double, val markedAutomatically: Double)

/** Display totals use recorded amounts, never inferred coupons, and remain scoped to this bond/currency. */
internal fun couponReceiptTotals(purchase: JsonRecord, receipts: List<JsonRecord>, asOf: LocalDate): CouponReceiptTotals {
    val eligible = receipts.filter {
        it.text("purchaseId") == purchase.text("id") &&
            it.text("currency").ifBlank { purchase.text("currency") } == purchase.text("currency") &&
            calendarDate(it.text("dueDate"))?.let { date -> date <= asOf } == true &&
            recordedCouponAmount(it, purchase.text("currency")) != null
    }.distinctBy { it.text("dueDate") }
    fun total(method: String) = roundedMoney(eligible.filter { it.text("method") == method }.sumOf { it.optDouble("amount") })
    return CouponReceiptTotals(total("manual"), total("automatic"))
}

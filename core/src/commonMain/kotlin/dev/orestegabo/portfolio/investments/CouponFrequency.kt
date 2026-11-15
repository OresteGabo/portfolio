package dev.orestegabo.portfolio.investments

import kotlin.jvm.JvmInline

/**
 * Number of coupon payments per year, independent of tenor, rate and receipt status.
 * Counts without an integral month interval remain valid when published payment dates exist.
 * The Int at public/record boundaries is retained for compatibility; domain scheduling uses this type.
 */
@JvmInline
internal value class CouponFrequency(val paymentsPerYear: Int) {
    init {
        require(accepts(paymentsPerYear)) { "Coupon frequency must be between 1 and 12." }
    }

    /** Null means a calendar-month schedule cannot be inferred; it does not invalidate published dates. */
    val calendarIntervalMonths: Int?
        get() = if (12 % paymentsPerYear == 0) 12 / paymentsPerYear else null

    companion object {
        private fun accepts(value: Int) = value in 1..12

        /** Accept the existing numeric record representation without rounding/truncating fractional counts. */
        fun fromNumber(value: Double): CouponFrequency? =
            if (value.isFinite() && value % 1.0 == 0.0 && accepts(value.toInt())) CouponFrequency(value.toInt()) else null
    }
}

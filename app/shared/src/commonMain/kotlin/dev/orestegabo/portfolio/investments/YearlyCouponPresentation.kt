package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

internal data class CouponCurrencyPage(val rows: List<YearCoupon>, val totals: YearCouponTotals)
internal data class CouponYearPage(
    val year: Int, val data: YearCoupons, val next: NextCouponYear?,
    val currencies: List<String>, val byCurrency: Map<String, CouponCurrencyPage>,
)

/** Build inside the screen's background calculation; no locale-formatted strings or Compose state. */
internal fun couponYearPage(
    purchases: List<JsonRecord>, receipts: List<JsonRecord>, year: Int,
    today: LocalDate, receiptsAvailable: Boolean,
): CouponYearPage {
    val data = yearlyCoupons(purchases, receipts, year, today, receiptsAvailable)
    val byCurrency = data.rows.groupBy { it.currency }.mapValues { (currency, rows) ->
        CouponCurrencyPage(rows, yearlyCouponTotals(rows, currency, receiptsAvailable))
    }
    return CouponYearPage(year, data,
        if (data.rows.isEmpty()) nextCouponYear(purchases, receipts, year, today, receiptsAvailable) else null,
        byCurrency.keys.sorted(), byCurrency)
}

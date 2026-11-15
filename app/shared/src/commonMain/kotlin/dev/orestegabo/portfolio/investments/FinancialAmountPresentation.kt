package dev.orestegabo.portfolio.investments

/** Missing/non-finite results are unknown, not a zero balance. Valid amounts keep existing rounding. */
internal fun displayAmountOrUnavailable(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let(::formatNumber) ?: localized("Unavailable")

/** Simulator output is optional at the presentation boundary; never manufacture a financial result. */
internal fun recordAmountText(record: JsonRecord, key: String, currency: String = "RWF"): String =
    record.optDouble(key).takeIf { it.isFinite() }?.let { money(it, currency) } ?: localized("Unavailable")

/** A missing month must not become zero or silently disappear from a continuous projection chart. */
internal fun projectionChartValues(rows: List<JsonRecord>): List<Double>? {
    val values = rows.map { it.optDouble("totalAccountValue") }
    return values.takeIf { it.all(Double::isFinite) }
}

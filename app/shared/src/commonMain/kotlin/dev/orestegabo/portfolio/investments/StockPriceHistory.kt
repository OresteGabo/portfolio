package dev.orestegabo.portfolio.investments

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal data class StockPricePoint(val date: LocalDate, val price: Double, val note: String, val contract: Boolean = false)

internal fun yearlyPriceChartRange(today: LocalDate) = (today - DatePeriod(years = 1))..today

internal fun chartMonthTicks(range: ClosedRange<LocalDate>): List<Pair<LocalDate, String>> {
    val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    var month = LocalDate(range.start.year, range.start.monthNumber, 1)
    if (month < range.start) month += DatePeriod(months = 1)
    return buildList {
        while (month <= range.endInclusive) {
            add(month to names[month.monthNumber - 1])
            month += DatePeriod(months = 1)
        }
    }
}

/** Only actual dated observations. Contracts never replace an official close on the same day. */
internal fun stockPriceHistory(history: JsonRecord?, quotes: JsonRecord?, ticker: String, period: String, today: LocalDate): List<StockPricePoint> {
    val start = when (period) {
        "month" -> today - DatePeriod(months = 1)
        "year" -> yearlyPriceChartRange(today).start
        else -> LocalDate(1900, 1, 1)
    }
    val points = mutableMapOf<LocalDate, StockPricePoint>()
    fun add(row: JsonRecord, key: String, contract: Boolean) {
        if (row.text("ticker") != ticker) return
        val date = runCatching { LocalDate.parse(row.text("date")) }.getOrNull() ?: return
        val price = row.optDouble(key, Double.NaN)
        if (date < start || date > today || !price.isFinite() || price <= 0) return
        val low = row.optDouble("low", price)
        val high = row.optDouble("high", price)
        val note = if (!contract) "Exchange close" else if (low.isFinite() && high.isFinite() && low > 0 && high > low)
            "Contract prices ${money(low)}–${money(high)} · median" else "Contract trade"
        points[date] = StockPricePoint(date, price, note, contract)
    }
    history?.rows("tradeObservations").orEmpty().forEach { add(it, "price", true) }
    history?.rows("observations").orEmpty().forEach { add(it, "closing", false) }
    // The latest published close may not have reached the background collector yet.
    val source = quotes?.optJSONObject("source")
    if (source != null && !source.optBoolean("stale")) {
        val published = source.text("publishedAt")
        val date = runCatching { Instant.parse(published).toLocalDateTime(TimeZone.of("Africa/Kigali")).date }
            .getOrNull() ?: runCatching { LocalDate.parse(published) }.getOrNull()
        val quote = quotes.rows("quotes").find { it.text("ticker") == ticker }
        if (date != null && quote != null && quote.optDouble("closing").isFinite()) add(JsonRecord().put("ticker", ticker).put("date", date.toString())
            .put("closing", quote.optDouble("closing", Double.NaN)), "closing", false)
    }
    return points.values.sortedBy { it.date }
}

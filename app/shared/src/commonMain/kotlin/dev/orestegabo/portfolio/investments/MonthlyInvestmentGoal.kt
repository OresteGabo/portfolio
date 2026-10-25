package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

const val DEFAULT_MONTHLY_INVESTMENT_TARGET = 100_000L
fun validInvestmentTarget(amount: Long) = amount in 1..1_000_000_000_000L

data class MonthlyInvestmentProgress(val month: LocalDate, val target: Long, val confirmed: Double, val pending: Double) {
    val remaining get() = (target - confirmed).coerceAtLeast(0.0)
    val fraction get() = (confirmed / target).coerceIn(0.0, 1.0).toFloat()
    val achieved get() = confirmed >= target
}

/** Uses complete server aggregates or full history, never the truncated preview or market gains. */
fun monthlyInvestmentProgress(snapshot: PortfolioSnapshot, month: LocalDate, target: Long, today: LocalDate): MonthlyInvestmentProgress? {
    require(validInvestmentTarget(target))
    val first = LocalDate(month.year, month.month, 1)
    val key = first.toString().take(7)
    snapshot.monthlyInvestmentTotals?.let { totals ->
        val row = totals.firstOrNull { it.text("month") == key }
        return MonthlyInvestmentProgress(first, target, row?.optDouble("confirmed", 0.0) ?: 0.0, row?.optDouble("pending", 0.0) ?: 0.0)
    }
    if (snapshot.recentPurchaseIds != null || snapshot.recentHoldingIds != null) return null
    fun relevant(row: JsonRecord, field: String) = row.text("currency") == "RWF" &&
        calendarDate(row.text(field))?.let { it <= today && it.year == month.year && it.month == month.month } == true
    fun Double.amount() = takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val bonds = snapshot.purchases.filter { relevant(it, "purchaseDate") }
    val shares = snapshot.holdings.filter { relevant(it, "tradeDate") }
    val confirmed = bonds.filter { it.text("status") in listOf("active", "sold", "matured") }.sumOf { it.bondCashCost().amount() } +
        shares.filter { it.text("status") in listOf("active", "sold") }.sumOf { it.optDouble("netAmountPayable", 0.0).amount() }
    val pending = bonds.filter { it.text("status") == "submitted" }.sumOf { it.bondCashCost().amount() }
    return MonthlyInvestmentProgress(first, target, confirmed, pending)
}

/** Compatibility for older compact APIs: retain monthly sums, not a second full-history store. */
internal fun monthlyGoalTotalsFromHistory(purchases: List<JsonRecord>, holdings: List<JsonRecord>, today: LocalDate): List<JsonRecord> {
    val full = PortfolioSnapshot(purchases, holdings)
    val first = LocalDate(today.year, today.month, 1)
    return (0..11).map { offset ->
        val month = shiftCalendarMonth(first, -offset)
        val progress = requireNotNull(monthlyInvestmentProgress(full, month, DEFAULT_MONTHLY_INVESTMENT_TARGET, today))
        JsonRecord().put("month", month.toString().take(7)).put("confirmed", progress.confirmed).put("pending", progress.pending)
    }
}

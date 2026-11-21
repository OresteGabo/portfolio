package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

enum class InvestmentGoalPeriod(val key: String) { MONTHLY("monthly"), YEARLY("yearly") }

/** Both amounts are retained; only the selected calendar period is an active goal. */
data class InvestmentGoal(val monthlyAmount: Long = DEFAULT_MONTHLY_INVESTMENT_TARGET,
    val yearlyAmount: Long = (monthlyAmount * 12).coerceAtMost(1_000_000_000_000L),
    val period: InvestmentGoalPeriod = InvestmentGoalPeriod.MONTHLY) {
    init { require(validInvestmentTarget(monthlyAmount) && validInvestmentTarget(yearlyAmount)) }
    val amount get() = if (period == InvestmentGoalPeriod.YEARLY) yearlyAmount else monthlyAmount
    val annualReference get() = if (period == InvestmentGoalPeriod.YEARLY) yearlyAmount.toDouble() else monthlyAmount * 12.0
    fun toRecord() = JsonRecord().put("monthlyTarget", monthlyAmount).put("yearlyTarget", yearlyAmount).put("goalPeriod", period.key)
}

fun investmentGoalFromRecord(record: JsonRecord?, fallback: InvestmentGoal = InvestmentGoal()): InvestmentGoal {
    fun amount(key: String, otherwise: Long) = record?.text(key)?.toLongOrNull()?.takeIf(::validInvestmentTarget) ?: otherwise
    return InvestmentGoal(amount("monthlyTarget", fallback.monthlyAmount), amount("yearlyTarget", fallback.yearlyAmount),
        InvestmentGoalPeriod.entries.firstOrNull { it.key == record?.text("goalPeriod") } ?: fallback.period)
}

/** Calendar-year contributions, never market returns or an incomplete compact preview. */
fun yearlyInvestmentProgress(snapshot: PortfolioSnapshot, year: Int, target: Long, today: LocalDate): MonthlyInvestmentProgress? {
    require(validInvestmentTarget(target))
    if (year > today.year) return MonthlyInvestmentProgress(LocalDate(year, 1, 1), target, 0.0, 0.0)
    // Compact totals cover the latest twelve months, enough for this calendar year only.
    if (snapshot.monthlyInvestmentTotals != null && year != today.year) return null
    val months = (1..if (year == today.year) today.month.ordinal + 1 else 12).map { month ->
        monthlyInvestmentProgress(snapshot, LocalDate(year, month, 1), target, today) ?: return null
    }
    return MonthlyInvestmentProgress(LocalDate(year, 1, 1), target,
        decimalAmount(emptyList(), addends = months.map { it.confirmed }),
        decimalAmount(emptyList(), addends = months.map { it.pending }))
}

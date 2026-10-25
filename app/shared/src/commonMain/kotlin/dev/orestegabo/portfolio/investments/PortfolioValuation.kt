package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

/** RSE equity quotes are denominated in RWF; other currencies must not be priced with them. */
fun PortfolioSnapshot.valuedShares(today: LocalDate, quotes: JsonRecord?): List<ValuedSharePosition> {
    val date = calendarDate(quotes?.optJSONObject("source")?.text("publishedAt").orEmpty().take(10))
    val rows = if (date != null && date > today) emptyList() else quotes?.rows("quotes").orEmpty()
    return sharePositions(today).map { position ->
        val quote = if (position.currency == "RWF") rows.firstOrNull {
            it.text("ticker").trim().uppercase() == position.ticker.trim().uppercase() &&
                it.optDouble("closing", 0.0).let { price -> price.isFinite() && price > 0 }
        } else null
        ValuedSharePosition(position, quote?.optDouble("closing"))
    }
}

data class ValuedSharePosition(val position: SharePosition, val closingPrice: Double?) {
    val estimatedValue get() = closingPrice?.let { position.shares * it } ?: position.cashCost
    val gainLoss get() = closingPrice?.let { estimatedValue - position.cashCost }
    /** Return on the combined cost of all lots, including purchase fees. */
    val gainLossRate get() = gainLoss?.takeIf { it.isFinite() && position.cashCost.isFinite() && position.cashCost > 0 }
        ?.let { it / position.cashCost }
}

data class CurrencyValuation(val cost: CurrencyInvestment, val shares: List<ValuedSharePosition>) {
    val shareValue get() = shares.sumOf { it.estimatedValue }
    val estimatedNetWorth get() = cost.bondFaceValue + shareValue
    val unpricedShares get() = shares.count { it.closingPrice == null }
    val pricedShares get() = shares.count { it.closingPrice != null }
}

fun PortfolioSnapshot.valuation(today: LocalDate, quotes: JsonRecord?): List<CurrencyValuation> {
    val shares = valuedShares(today, quotes).groupBy { it.position.currency }
    return summary(today).map { cost -> CurrencyValuation(cost, shares[cost.currency].orEmpty()) }
}

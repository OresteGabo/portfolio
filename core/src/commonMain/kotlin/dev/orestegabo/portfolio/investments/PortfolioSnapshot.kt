package dev.orestegabo.portfolio.investments

import kotlinx.datetime.*

/** The server records behind every private list and summary. No separate cached totals. */
data class PortfolioSnapshot(
    val purchases: List<JsonRecord> = emptyList(),
    val holdings: List<JsonRecord> = emptyList(),
    val documents: List<JsonRecord> = emptyList(),
    val recentPurchaseIds: Set<String>? = null,
    val recentHoldingIds: Set<String>? = null,
    val monthlyInvestmentTotals: List<JsonRecord>? = null,
) {
    fun recentBonds(today: LocalDate) = recentRows(purchases, "purchaseDate", today, recentPurchaseIds)
    fun recentShares(today: LocalDate) = recentRows(holdings, "tradeDate", today, recentHoldingIds)

    fun activeShares(today: LocalDate) = holdings.filter {
        it.text("status") == "active" && onOrBefore(it.text("tradeDate"), today)
    }

    fun sharePositions(today: LocalDate): List<SharePosition> = activeShares(today)
        .groupBy { it.text("ticker") to it.text("currency") }
        .map { (key, lots) -> SharePosition(key.first, key.second,
            lots.sumOf { it.optDouble("shares", 0.0) }, decimalAmount(emptyList(), addends = lots.map { it.optDouble("netAmountPayable", 0.0) })) }

    fun summary(today: LocalDate): List<CurrencyInvestment> {
        val bonds = purchases.filter { it.text("status") == "active" && onOrBefore(it.text("purchaseDate"), today) }
        val shares = activeShares(today)
        return (purchases + holdings).map { it.text("currency") }.distinct().sorted().map { currency ->
            val currencyBonds = bonds.filter { it.text("currency") == currency }
            val currencyShares = shares.filter { it.text("currency") == currency }
            CurrencyInvestment(currency,
                decimalAmount(emptyList(), addends = currencyBonds.map { it.optDouble("faceValue", 0.0) }),
                decimalAmount(emptyList(), addends = currencyBonds.map { it.bondCashCost() }),
                decimalAmount(emptyList(), addends = currencyShares.map { it.optDouble("netAmountPayable", 0.0) }), currencyBonds.size, currencyShares.sumOf { it.optInt("lotCount", 1) },
                currencyBonds.filter { calendarDate(it.text("maturityDate"))?.let { maturity -> maturity > today } == true }
                    .map { it.netAnnualBondCoupon() }.let { decimalAmount(emptyList(), addends = it) })
        }
    }
}

data class SharePosition(val ticker: String, val currency: String, val shares: Double, val cashCost: Double)
data class CurrencyInvestment(val currency: String, val bondFaceValue: Double, val bondCashCost: Double,
    val shareCashCost: Double, val bondCount: Int, val shareCount: Int, val netAnnualCoupon: Double = 0.0) {
    val investedCash get() = decimalAmount(emptyList(), addends = listOf(bondCashCost, shareCashCost))
}

private fun onOrBefore(value: String, today: LocalDate) =
    runCatching { LocalDate.parse(value) <= today }.getOrDefault(false)

fun JsonRecord.bondCashCost() = decimalAmount(listOf(optDouble("faceValue", 0.0), optDouble("pricePercent", 100.0)), 100,
    addends = listOf(optDouble("accruedInterestPaid", 0.0), optDouble("feesPaid", 0.0)))

fun JsonRecord.netAnnualBondCoupon() = decimalAmount(listOf(optDouble("faceValue", 0.0), optDouble("couponRate", 0.0)),
    taxRate = optDouble("withholdingTaxRate", 0.0))

private fun recentRows(rows: List<JsonRecord>, dateKey: String, today: LocalDate, ids: Set<String>?): List<JsonRecord> {
    val start = today.minus(DatePeriod(years = 1))
    return rows.filter { row ->
        val date = calendarDate(row.text(dateKey))
        date != null && date >= start && date <= today && (ids == null || row.text("id") in ids)
    }.sortedWith(compareByDescending<JsonRecord> { it.text(dateKey) }.thenByDescending { it.text("createdAt") }.thenBy { it.text("id") }).take(12)
}

package dev.orestegabo.portfolio.investments

/**
 * Builds company display rows by grouping active purchases with a non-blank ticker by ticker/currency.
 * Amounts use remaining shares/cost after sales when supplied, otherwise original shares/cost.
 * Name and date come from the latest trade, not from a market quote or a valuation timestamp.
 *
 * Original ledger purchases and evidence remain separate. Grouped rows are new records with synthetic
 * company IDs; ungrouped rows retain their original record references and must be treated as read-only.
 * Results are sorted by descending ISO trade-date text. No persistence records are rewritten.
 */
fun shareCompanyRows(holdings: List<JsonRecord>): List<JsonRecord> = holdings
    .groupBy { row ->
        if (row.text("status") == "active" && row.text("ticker").isNotBlank())
            listOf("company", row.text("ticker"), row.text("currency"))
        else listOf("purchase", row.text("id"))
    }.values.map { lots ->
        if (lots.first().text("status") != "active" || lots.first().text("ticker").isBlank()) lots.first()
        else {
            val latestPurchase = lots.maxBy { it.text("tradeDate") }
            JsonRecord().put("id", "company:${latestPurchase.text("ticker")}:${latestPurchase.text("currency")}")
                .put("ticker", latestPurchase.text("ticker")).put("currency", latestPurchase.text("currency"))
                .put("securityName", latestPurchase.text("securityName")).put("status", "active")
                .put("tradeDate", latestPurchase.text("tradeDate"))
                .put("shares", lots.sumOf { it.optDouble("remainingShares", it.optDouble("shares", 0.0)) })
                .put("netAmountPayable", decimalAmount(emptyList(), addends = lots.map {
                    it.optDouble("remainingCost", it.optDouble("netAmountPayable", 0.0))
                }))
        }
    }.sortedByDescending { it.text("tradeDate") }

package dev.orestegabo.portfolio.investments

/** Display rows only. Original purchases and their evidence remain separate in the ledger. */
fun shareCompanyRows(holdings: List<JsonRecord>): List<JsonRecord> = holdings
    .groupBy { row ->
        if (row.text("status") == "active" && row.text("ticker").isNotBlank())
            listOf("company", row.text("ticker"), row.text("currency"))
        else listOf("purchase", row.text("id"))
    }.values.map { lots ->
        if (lots.first().text("status") != "active" || lots.first().text("ticker").isBlank()) lots.first()
        else {
            val latest = lots.maxBy { it.text("tradeDate") }
            JsonRecord().put("id", "company:${latest.text("ticker")}:${latest.text("currency")}")
                .put("ticker", latest.text("ticker")).put("currency", latest.text("currency"))
                .put("securityName", latest.text("securityName")).put("status", "active")
                .put("tradeDate", latest.text("tradeDate"))
                .put("shares", lots.sumOf { it.optDouble("remainingShares", it.optDouble("shares", 0.0)) })
                .put("netAmountPayable", decimalAmount(emptyList(), addends = lots.map {
                    it.optDouble("remainingCost", it.optDouble("netAmountPayable", 0.0))
                }))
        }
    }.sortedByDescending { it.text("tradeDate") }

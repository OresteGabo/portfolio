package dev.orestegabo.portfolio.investments

internal data class ShareBalanceFixture(val holdings: List<JsonRecord>, val entries: List<JsonRecord>)

/** Synthetic only; largest benchmark uses the existing 5,000-entry local ledger limit. */
internal fun shareBalanceFixture(holdingCount: Int, orphanSales: Int = 0): ShareBalanceFixture {
    val holdings = (0 until holdingCount).map { index ->
        JsonRecord().put("id", "lot-$index").put("ticker", "TEST").put("currency", "RWF")
            .put("status", "active").put("tradeDate", "2026-01-01").put("shares", 1000)
            .put("netAmountPayable", 123456.78).put("unknownMetadata", "synthetic-$index")
    }
    val entries = buildList {
        repeat(4) { round ->
            holdings.forEachIndexed { index, lot ->
                add(JsonRecord().put("id", "sale-$round-$index").put("kind", "sale").put("holdingId", lot.text("id"))
                    .put("currency", "RWF").put("date", "2026-02-01").put("shares", 10).put("allocatedCost", 1234.57))
            }
        }
        holdings.forEachIndexed { index, _ ->
            add(JsonRecord().put("id", "deposit-$index").put("kind", "deposit").put("amount", 1000))
        }
        repeat(orphanSales) { index ->
            add(JsonRecord().put("id", "orphan-sale-$index").put("kind", "sale").put("holdingId", "absent-$index")
                .put("shares", 10).put("allocatedCost", 1))
        }
    }
    return ShareBalanceFixture(holdings, entries)
}

/** Frozen pre-optimization traversal; do not use this reference implementation in production. */
internal fun referenceShareBalances(holdings: List<JsonRecord>, entries: List<JsonRecord>): List<JsonRecord> = holdings.map { lot ->
    val sales = entries.filter { it.text("kind") == "sale" && it.text("holdingId") == lot.text("id") }
    val sold = sales.sumOf { it.optDouble("shares", 0.0) }
    val original = lot.optDouble("shares", 0.0)
    JsonRecord(lot.toString()).put("remainingShares", (original - sold).coerceAtLeast(0.0)).put("soldShares", sold)
        .put("remainingCost", if (original > 0) decimalAmount(emptyList(), addends = listOf(lot.optDouble("netAmountPayable", 0.0)) +
            sales.map { -it.optDouble("allocatedCost", 0.0) }) else 0.0)
}

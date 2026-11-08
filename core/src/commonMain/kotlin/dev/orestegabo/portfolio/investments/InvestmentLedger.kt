package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

/** Original purchases stay immutable; balances are derived from a small ledger of actual events. */
fun shareBalances(holdings: List<JsonRecord>, entries: List<JsonRecord>): List<JsonRecord> = holdings.map { lot ->
    val sold = entries.filter { it.text("kind") == "sale" && it.text("holdingId") == lot.text("id") }.sumOf { it.optDouble("shares", 0.0) }
    val original = lot.optDouble("shares", 0.0)
    val remaining = (original - sold).coerceAtLeast(0.0)
    JsonRecord(lot.toString()).put("remainingShares", remaining).put("soldShares", sold)
        .put("remainingCost", if (original > 0) decimalAmount(emptyList(), addends = listOf(lot.optDouble("netAmountPayable", 0.0)) + entries.filter { it.text("kind") == "sale" && it.text("holdingId") == lot.text("id") }.map { -it.optDouble("allocatedCost", 0.0) }) else 0.0)
}

fun validateLedgerEntry(raw: JsonRecord, holdings: List<JsonRecord>, prior: List<JsonRecord>, today: LocalDate): JsonRecord {
    val kind = raw.text("kind")
    require(kind in listOf("deposit", "withdrawal", "dividend", "sale")) { "Choose a transaction type." }
    val date = requireNotNull(calendarDate(raw.text("date"))) { "Enter a valid transaction date." }
    require(date <= today) { "Transactions cannot be dated in the future." }
    val currency = raw.text("currency")
    require(currency.matches(Regex("[A-Z]{3}"))) { "Choose a three-letter currency." }
    val result = JsonRecord().put("kind", kind).put("date", date.toString()).put("currency", currency)
    val note = raw.text("note").trim()
    require(note.length <= 500) { "Keep the note under 500 characters." }
    result.put("note", note)
    if (kind in listOf("sale", "dividend")) {
        val lot = holdings.singleOrNull { it.text("id") == raw.text("holdingId") } ?: error("Choose a saved share purchase.")
        require(lot.text("currency") == currency && date.toString() >= lot.text("tradeDate")) { "Use the purchase currency and a date on or after purchase." }
        result.put("holdingId", lot.text("id")).put("ticker", lot.text("ticker"))
        if (kind == "sale") {
            require(lot.text("status") == "active") { "Only an active share purchase can be sold." }
            val available = lot.optDouble("shares") - prior.filter { it.text("kind") == "sale" && it.text("holdingId") == lot.text("id") }.sumOf { it.optDouble("shares", 0.0) }
            val quantity = raw.getDouble("shares"); val price = raw.getDouble("price")
            val fees = raw.optDouble("fees", 0.0); val tax = raw.optDouble("tax", 0.0)
            require(quantity > 0 && quantity % 1.0 == 0.0 && quantity <= available && price > 0 && price <= 1e12 && fees.isFinite() && fees >= 0 && tax.isFinite() && tax >= 0) { "Check the quantity, price and charges. You cannot sell more shares than remain." }
            val gross = decimalAmount(listOf(quantity, price))
            require(gross <= 1e15 && fees + tax <= gross) { "Sale charges cannot exceed proceeds." }
            val remainingCost = decimalAmount(emptyList(), addends = listOf(lot.optDouble("netAmountPayable")) + prior.filter { it.text("kind") == "sale" && it.text("holdingId") == lot.text("id") }.map { -it.optDouble("allocatedCost", 0.0) })
            val allocated = if (quantity == available) remainingCost
                else proportionalCost(lot.optDouble("netAmountPayable"), quantity, lot.optDouble("shares")).coerceAtMost(remainingCost)

            val net = decimalAmount(emptyList(), addends = listOf(gross, -fees, -tax))
            result.put("shares", quantity).put("price", price).put("fees", fees).put("tax", tax).put("amount", net)
                .put("allocatedCost", allocated).put("realizedGain", decimalAmount(emptyList(), addends = listOf(net, -allocated)))
            return result
        }
    }
    val amount = raw.getDouble("amount")
    require(amount > 0 && amount <= 1e15) { "Enter a positive amount." }
    return result.put("amount", amount)
}

data class RecordedCash(val currency: String, val balance: Double, val dividends: Double, val realizedGain: Double)
fun recordedCash(portfolio: PortfolioSnapshot, receipts: List<JsonRecord>, entries: List<JsonRecord>, today: LocalDate): List<RecordedCash> {
    val through = today.toString()
    val events = entries.filter { it.text("date") <= through }
    val bonds = portfolio.purchases.filter { it.text("status") != "submitted" && it.text("purchaseDate") <= through }
    val shares = portfolio.holdings.filter { it.text("tradeDate") <= through }
    val confirmed = receipts.filter { it.optBoolean("received") && it.text("method") == "manual" && it.text("dueDate") <= through }
    return (events.map { it.text("currency") } + (bonds + shares + confirmed).map { it.text("currency") }).filter { it.isNotBlank() }.distinct().sorted().map { currency ->
        val e = events.filter { it.text("currency") == currency }
        val incoming = e.map { it.optDouble("amount", 0.0) * if (it.text("kind") == "withdrawal") -1 else 1 }
        val spent = bonds.filter { it.text("currency") == currency }.map { -it.bondCashCost() } + shares.filter { it.text("currency") == currency }.map { -it.optDouble("netAmountPayable", 0.0) }
        val coupons = confirmed.filter { it.text("currency") == currency }.map { it.optDouble("amount", 0.0) }
        RecordedCash(currency, decimalAmount(emptyList(), addends = incoming + spent + coupons),
            decimalAmount(emptyList(), addends = e.filter { it.text("kind") == "dividend" }.map { it.optDouble("amount", 0.0) }),
            decimalAmount(emptyList(), addends = e.filter { it.text("kind") == "sale" }.map { it.optDouble("realizedGain", 0.0) }))
    }
}

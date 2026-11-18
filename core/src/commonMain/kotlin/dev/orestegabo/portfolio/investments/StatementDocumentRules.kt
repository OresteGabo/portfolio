package dev.orestegabo.portfolio.investments

private fun statementDate(text: String): String {
    val m = pattern("""^(\d{1,2})-(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)-(\d{4})$""").find(text) ?: return ""
    return isoDocumentDate(m.groupValues[3].toInt(), documentMonths.indexOf(m.groupValues[2].lowercase()) + 1, m.groupValues[1].toInt(), false).orEmpty()
}
internal fun parseStatementDocument(raw: String, usedOcr: Boolean): JsonRecord? {
    familyCheck(raw)
    if (!raw.matchesPattern("""\bBK\s+CAPITAL\b""") || !raw.matchesPattern("""\bCLIENT\s+STATEMENT\b""")) return null
    fun fail(): Nothing = throw DocumentParseException()
    if (!raw.matchesPattern("""Balance\s*\(RWF\)""") || Regex("""\b(?:USD|EUR|GBP)\b""").containsMatchIn(raw)) fail()
    fun period(label: String) = statementDate(capture(raw, "$label\\s+Date\\s*:[ \\t]*(\\S+)"))
    val fromDate = period("From"); val toDate = period("To")
    if (fromDate.isEmpty() || toDate.isEmpty() || toDate < fromDate) fail()
    val opening = pattern("""Opening\s+Balance\s+As\s+On""").findAll(raw).toList()
    val closing = pattern("""Closing\s+Balance\s+As\s+On""").findAll(raw).toList()
    if (opening.size != 1 || closing.size != 1 || closing[0].range.first < opening[0].range.first) fail()
    val ledger = raw.substring(opening[0].range.last + 1, closing[0].range.first)
    if (ledger.isEmpty()) fail()
    val starts = pattern("""^\s*(\S+-\S+)\s+([A-Za-z][A-Za-z-]*)\b""", true).findAll(ledger).toList()
    val rows = mutableListOf<JsonRecord>(); val movements = mutableListOf<JsonRecord>()
    for ((i, match) in starts.withIndex()) {
        val block = ledger.substring(match.range.first, starts.getOrNull(i + 1)?.range?.first ?: ledger.length)
        val text = block.replace(Regex("\\s+"), " ").trim()
        val day = statementDate(match.groupValues[1])
        if (day.isEmpty() || day < fromDate || day > toDate) fail()
        val columns = block.split(Regex("\\r?\\n")).map { it.trim().split(Regex("\\s+")) }.filter { tokens -> tokens.size >= 2 && tokens.takeLast(2).all { Regex("""[+\-\d.,()]+""").matches(it) } }
        if (columns.size != 1) fail()
        val tokens = columns[0]
        if (Regex("[+-]").matches(tokens.getOrNull(tokens.size - 3).orEmpty())) fail()
        val amount = documentNumber(tokens[tokens.size - 2]) ?: fail()
        val credit = documentNumber(tokens.last()) ?: fail()
        val type = match.groupValues[2].lowercase()
        val purchase = type == "purchase" || type == "journal" && text.matchesPattern("""\bBond\s+Cost\b""")
        if (!purchase) {
            if (amount > 0 && credit > 0) fail()
            if (amount == 0.0 && credit == 0.0) continue
            val kind = when {
                text.matchesPattern("""\bdividend\b""") -> "dividend"
                text.matchesPattern("""\bcoupon\b""") -> "coupon"
                text.matchesPattern("""\b(?:fee|fees|charge|charges|custody|commission|levy)\b""") -> "fee"
                type == "receipt" -> "cash-receipt"
                else -> "other"
            }
            movements.add(JsonRecord().put("kind", kind).put("date", day).put("amount", if (amount != 0.0) amount else credit).put("direction", if (amount != 0.0) "debit" else "credit").put("rowNumber", i + 1).put("title", mapOf("cash-receipt" to "Cash receipt", "fee" to "Statement fee", "coupon" to "Coupon movement", "dividend" to "Dividend movement")[kind] ?: "Other statement movement"))
            continue
        }
        if (amount <= 0 || amount > 1e13 || credit != 0.0) fail()
        if (type == "purchase") {
            val trade = pattern("""Purchase\s+of\s+([A-Z][A-Z0-9]{1,9})\s+(\S+)\s*@\s*(\S+)\s+on\s+(\d{2})-(\d{2})-(\d{4})""").find(text) ?: fail()
            val contract = pattern("""\bPurchase[ \t]+(\d+#)(?:([0-9]+)\b|[ \t]+(\d+)(?=[ \t]+Purchase\b|[ \t]*(?:\r?\n|$))|[^\n]*\n[ \t]*(\d+)(?=[ \t]+(?:by\b|[A-Za-z][A-Za-z0-9]*[ \t]*-)|[ \t]*(?:\r?\n|$)))""").find(block) ?: fail()
            val shares = documentNumber(trade.groupValues[2]) ?: fail()
            val price = documentNumber(trade.groupValues[3]) ?: fail()
            if (shares % 1 != 0.0 || shares <= 0 || price <= 0 || "${trade.groupValues[6]}-${trade.groupValues[5]}-${trade.groupValues[4]}" != day || amount < shares * price) fail()
            rows.add(JsonRecord().put("kind", "equity").put("rowNumber", i + 1).put("date", day).put("amount", amount).put("title", trade.groupValues[1].uppercase()).put("ticker", trade.groupValues[1].uppercase()).put("shares", shares).put("pricePerShare", price).put("contractNote", contract.groupValues[1] + (2..4).firstNotNullOf { index -> contract.groups[index]?.value }))
        } else {
            val title = pattern("""\b([A-Za-z]+\s+Bond\s+Cost)\b""").find(text)?.groupValues?.get(1) ?: "Bond purchase"
            rows.add(JsonRecord().put("kind", "bond").put("rowNumber", i + 1).put("date", day).put("amount", amount).put("title", title))
        }
    }
    if (rows.isEmpty() && movements.isEmpty() || rows.size + movements.size > 500) fail()
    return JsonRecord().put("kind", "statement").put("parser", bkStatement()).put("broker", "BK Capital").put("currency", "RWF").put("fromDate", fromDate).put("toDate", toDate).put("rows", rows).put("movements", movements).put("usedOcr", usedOcr)
}

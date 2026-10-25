package dev.orestegabo.portfolio.investments

internal data class DocumentInvestmentLogo(val ticker: String = "", val bond: Boolean = false, val name: String)

/** Resolve the investment, never mistake a contract's broker for its share issuer. */
internal fun documentInvestmentLogo(document: JsonRecord): DocumentInvestmentLogo? {
    val category = document.text("category").lowercase()
    if (category.contains("statement") || category.contains("relevé")) return null
    val security = listOf("ticker", "symbol", "securityName", "instrumentName", "companyName").map { document.text(it) }
    val details = document.optJSONObject("details")
    val facts = (details?.rows("facts").orEmpty() + details?.rows("sections").orEmpty().flatMap { it.rows("items") })
        .filter { it.text("label").lowercase().replace(Regex("[^a-z]"), "") in setOf("ticker", "symbol", "security", "securityname", "company", "companyname", "instrument", "instrumentname") }
        .map { it.text("value") }
    if (category.contains("bond") || (security + facts).any { Regex("\\b(bond|treasury)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) })
        return DocumentInvestmentLogo(bond = true, name = "BNR")

    val names = listOf(
        Triple("BOK", "Bank of Kigali", "\\b(BOK|BK\\s+GROUP|BANK\\s+OF\\s+KIGALI)\\b"),
        Triple("IMR", "I&M Bank", "\\b(IMR|IMBANK)\\b|\\bI\\s*(&|AND)\\s*M\\b"),
        Triple("BLR", "Bralirwa", "\\b(BLR|BRALIRWA)\\b"),
        Triple("CMR", "Cimerwa", "\\b(CMR|CIMERWA)\\b"),
        Triple("KCB", "KCB", "\\b(KCB|KENYA\\s+COMMERCIAL\\s+BANK)\\b"),
        Triple("MTNR", "MTN", "\\b(MTNR|MTN)\\b"),
        Triple("EQTY", "Equity Bank", "\\bEQTY\\b|\\bEQUITY\\s+(BANK|GROUP|HOLDINGS)\\b")
    )
    fun matches(values: List<String>): List<DocumentInvestmentLogo> = names.filter { (symbol, _, pattern) ->
        values.any { value ->
            val name = value.trim()
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(name) ||
                (symbol == "BOK" && name.equals("BK", true)) ||
                (symbol == "EQTY" && name.equals("Equity", true))
        }
    }.map { DocumentInvestmentLogo(ticker = it.first, name = it.second) }
    val primary = matches(security + facts)
    if (primary.isNotEmpty()) return primary.singleOrNull()
    // Labels can identify an issuer, but generic "Equity" and "BK Capital" do not.
    return matches(listOf(document.text("label"))).singleOrNull()
}

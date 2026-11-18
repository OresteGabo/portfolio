package dev.orestegabo.portfolio.investments

import kotlinx.serialization.json.*
import kotlin.math.*

fun validateEquityDocument(value: JsonRecord): JsonRecord? {
    fun text(key: String, max: Int, required: Boolean = false): String? {
        val raw = value.element()[key]
        if (raw != null && raw != JsonNull && (raw !is JsonPrimitive || !raw.isString)) return null
        val text = value.text(key).trim()
        return text.takeIf { it.length <= max && (!required || it.isNotEmpty()) }
    }
    val securityName = text("securityName", 160, true) ?: return null
    val isin = text("isin", 32, true) ?: return null
    val ticker = text("ticker", 10, true) ?: return null
    val strings = listOf("broker", "contractNote", "cdsAccountNo", "cdsReference", "tradeReportId").associateWith { text(it, 120) }
    if (strings.values.any { it == null } || !Regex("[A-Z][A-Z0-9]{1,9}").matches(ticker) || !Regex("[A-Z0-9]{8,32}").matches(isin) || value.text("exchange") != "RSE" || value.text("currency") != "RWF" || value.text("instrumentType") != "equity" || !validDocumentDate(value.text("tradeDate")) || !validDocumentDate(value.text("settlementDate")) || value.text("settlementDate") < value.text("tradeDate")) return null
    val keys = listOf("shares", "pricePerShare", "grossConsideration", "brokerageCommission", "rseTransactionFee", "csdTransactionLevy", "cmaTransactionFee", "totalCharges", "netAmountPayable")
    for (key in keys) {
        val raw = value.element()[key] as? JsonPrimitive ?: return null
        if (raw.isString || raw.doubleOrNull?.let { it.isFinite() && it in 0.0..1e13 } != true) return null
    }
    fun n(key: String) = value.optDouble(key)
    if (n("shares") % 1 != 0.0 || n("shares") <= 0 || n("pricePerShare") <= 0 || abs(cents(n("shares") * n("pricePerShare")) - n("grossConsideration")) > .02 || abs(cents(n("brokerageCommission") + n("rseTransactionFee") + n("csdTransactionLevy") + n("cmaTransactionFee")) - n("totalCharges")) > .02 || abs(cents(n("grossConsideration") + n("totalCharges")) - n("netAmountPayable")) > .02) return null
    return JsonRecord().put("instrumentType", "equity").put("securityName", securityName).put("isin", isin).put("ticker", ticker).put("exchange", "RSE").put("currency", "RWF").put("tradeDate", value.text("tradeDate")).put("settlementDate", value.text("settlementDate")).also { result -> keys.forEach { result.put(it, n(it)) }; strings.forEach { (k, v) -> result.put(k, v) } }.put("status", "active").put("notes", "")
}
private fun equityDate(text: String): String {
    val m = pattern("""^(\d{1,2})[-/\s](Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)[-/\s](\d{4})(?=\s|$)""").find(text) ?: return ""
    return isoDocumentDate(m.groupValues[3].toInt(), documentMonths.indexOf(m.groupValues[2].lowercase().take(3)) + 1, m.groupValues[1].toInt(), false).orEmpty()
}
internal fun parseEquityDocument(raw: String, usedOcr: Boolean): JsonRecord? {
    familyCheck(raw)
    val text = raw.replace("\r", "")
    if (!text.matchesPattern("""\bBK\s+CAPITAL\b""") || pattern("""EQUITY\s+PURCHASE\s+CONTRACT\s+NOTE\s*:""").findAll(text).count() != 1 || text.matchesPattern("""EQUITY\s+SALE\s+CONTRACT""")) return null
    val fields = JsonRecord().put("instrumentType", "equity").put("exchange", "RSE").put("currency", "RWF").put("status", "active").put("notes", "")
    fun c(regex: String) = capture(text, regex)
    fields.put("contractNote", c("""EQUITY\s+PURCHASE\s+CONTRACT\s+NOTE\s*:[ \t]*(\S+)"""))
        .put("securityName", c("""Security\s+Name\s*:\s*([^\n]+?)(?=\s+(?:Trade|Settlement|ISIN)\s|\n|$)"""))
        .put("isin", c("""ISIN\s+Code\s*:[ \t]*(\S+)""").uppercase())
        .put("tradeDate", equityDate(c("""Trade\s+Date\s*:\s*([^\n]+)""")))
        .put("settlementDate", equityDate(c("""Settlement\s+Date\s*:\s*([^\n]+)""")))
        .put("shares", documentNumber(c("""No\.?(?:\s+of)\s+Shares\s*:[ \t]*(\S+)""")) ?: 0)
        .put("pricePerShare", documentNumber(c("""\bPrice\s*:[ \t]*(\S+)""")) ?: 0)
        .put("broker", "BK Capital")
        .put("cdsAccountNo", c("""CDS\s+Account\s+No\.\s*:\s*([A-Z0-9]+)"""))
        .put("cdsReference", c("""CDS\s+Ref\.\s+No\.\s*:\s*([A-Z0-9]+)"""))
        .put("tradeReportId", c("""Trade\s+Report\s+ID\s*:\s*(\d+)"""))
        .put("ticker", mapOf("RW000A1JCYA" to "BOK", "RW000A2DN989" to "IMR")[fields.text("isin")].orEmpty())
    val labels = linkedMapOf("grossConsideration" to "Gross\\s+Consideration", "brokerageCommission" to "Brokerage\\s+Commission", "rseTransactionFee" to "RSE\\s+Transaction\\s+Fee", "csdTransactionLevy" to "CSD\\s+Transaction\\s+Levy", "cmaTransactionFee" to "CMA\\s+Transaction\\s+Fee", "totalCharges" to "Total\\s+Charges", "netAmountPayable" to "NET\\s+AMOUNT\\s+PAYABLE")
    val missing = mutableListOf<String>()
    for ((key, label) in labels) {
        val row = c("$label[ \\t]+([^\\n]+)")
        val amount = if (row.matchesPattern("""[+-]\s+\d""")) null else documentNumber(row.trim().split(Regex("\\s+")).lastOrNull())
        fields.put(key, amount ?: 0)
        if (amount == null) missing.add(key.replace(Regex("[A-Z]")) { " " + it.value.lowercase() })
    }
    for ((label, key) in listOf("security name" to "securityName", "ISIN" to "isin", "ticker" to "ticker", "trade date" to "tradeDate", "settlement date" to "settlementDate", "number of shares" to "shares", "share price" to "pricePerShare")) if (fields.text(key).isEmpty() || fields.optDouble(key) == 0.0) missing.add(label)
    if (!text.matchesPattern("""All\s+figures\s+are\s+in\s+RWF""") || !text.matchesPattern("""Exchange\s*:\s*RSE\b""")) return null
    val warnings = mutableListOf<String>()
    if (usedOcr) warnings.add("Text recognition was used. Check every number against your PDF.")
    if (missing.isEmpty() && validateEquityDocument(fields) == null) warnings.add("The extracted totals or dates do not agree. Correct them before saving.")
    return JsonRecord().put("kind", "equity").put("parser", bkPurchase()).put("fields", fields).put("missingFields", missing).put("warnings", warnings).put("usedOcr", usedOcr)
}

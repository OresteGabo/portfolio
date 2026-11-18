package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*
import kotlin.math.*

/** Recognizes supported layouts, not a PDF's authenticity or its signer's identity. */
class DocumentParseException(val code: String = "not_bond_result") : IllegalArgumentException("Could not recognize this investment document.")
internal fun pattern(source: String, multiline: Boolean = false) = Regex(source, if (multiline) setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE) else setOf(RegexOption.IGNORE_CASE))
internal fun String.matchesPattern(source: String) = pattern(source).containsMatchIn(this)
internal fun capture(text: String, expression: String): String {
    val values = pattern(expression).findAll(text).map { it.groupValues[1].trim() }.toList()
    if (values.map { it.uppercase() }.distinct().size > 1) throw DocumentParseException()
    return values.firstOrNull().orEmpty()
}
internal fun familyCheck(text: String) {
    val families = listOf(text.matchesPattern("""\bCLIENT\s+STATEMENT\b"""), text.matchesPattern("""\bEQUITY\s+(?:PURCHASE|SALE)\s+CONTRACT\s+NOTE\b"""), text.matchesPattern("""\b(?:INVESTOR\s+RESULTS|SUMMARY\s+MARKET\s+RESULTS)\b"""))
    if (families.count { it } > 1 || pattern("""INVESTOR\s+RESULTS""").findAll(text).count() > 1 || pattern("""SUMMARY\s+MARKET\s+RESULTS""").findAll(text).count() > 1 || text.matchesPattern("""INVESTOR\s+RESULTS""") && text.matchesPattern("""SUMMARY\s+MARKET\s+RESULTS""")) throw DocumentParseException()
}
internal fun parserIdentity(institution: String, type: String, format: String) = JsonRecord().put("institution", institution).put("documentType", type).put("format", format).put("version", 1)
internal fun bnrInvestor() = parserIdentity("BNR", "bond-confirmation", "investor-results")
internal fun bnrIssuance() = parserIdentity("BNR", "bond-issuance", "summary-market-results")
internal fun bkPurchase() = parserIdentity("BK Capital", "equity-purchase", "rse-purchase-contract-note")
internal fun bkStatement() = parserIdentity("BK Capital", "statement", "rwf-client-statement")
internal fun jsRound(number: Double) = floor(number + .5)
internal fun cents(number: Double) = jsRound(number * 100) / 100
internal fun validDocumentDate(value: String): Boolean = Regex("""\d{4}-\d{2}-\d{2}""").matches(value) && runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)
internal val documentMonths = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
internal fun isoDocumentDate(year: Int, month: Int, day: Int, bounded: Boolean = true): String? {
    if (bounded && year !in 2000..2199) return null
    return runCatching { LocalDate(year, month, day).toString() }.getOrNull()
}
/** Admit whole tokens only, preserving the existing safe scaled-unit and precision limits. */
fun documentNumber(token: String?, decimals: Int = 2): Double? {
    if (decimals !in 1..6 || token == null || !Regex("^(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d{1,$decimals})?$").matches(token)) return null
    val plain = token.replace(",", "")
    val parts = plain.split('.')
    val units = (parts[0] + parts.getOrElse(1) { "" }.padEnd(decimals, '0')).toLongOrNull() ?: return null
    val value = plain.toDoubleOrNull() ?: return null
    return value.takeIf { units <= 9_007_199_254_740_991L && it.isFinite() && it <= 1e13 }
}

object InvestmentDocumentRules {
    fun parse(text: String, usedOcr: Boolean = false): JsonRecord {
        familyCheck(text)
        parseStatement(text, usedOcr)?.let { return it }
        parseIssuance(text, usedOcr)?.let { return it }
        val equity = parseEquity(text, usedOcr)
        val bond = parseBond(text, usedOcr)
        val isBond = recognizedBond(bond, text)
        if (equity != null && !isBond) return equity
        if (isBond && equity == null) return JsonRecord("{\"kind\":\"bond\"}").also { target -> bond.element().forEach { (k, v) -> target.put(k, v) } }
        throw DocumentParseException()
    }
    fun parseBond(text: String, usedOcr: Boolean = false) = parseBondDocument(text, usedOcr)
    fun parseIssuance(text: String, usedOcr: Boolean = false) = parseIssuanceDocument(text, usedOcr)
    fun parseEquity(text: String, usedOcr: Boolean = false) = parseEquityDocument(text, usedOcr)
    fun parseStatement(text: String, usedOcr: Boolean = false) = parseStatementDocument(text, usedOcr)
}

package dev.orestegabo.portfolio.investments

import kotlin.math.*

private val documentDatePattern = pattern("""([\dIlioO]{1,2})\s*[-/.]?\s*(Jan|Feb|Mar|Apr|May|Jun|Ju[lI]|Aug|Sep|Oct|Nov|Dec)[a-z]*\s*[-/.]?\s*([\dIlioO]{2,4})(?!\d)""")
private fun digits(value: String) = value.replace(Regex("[Ili]"), "1").replace(Regex("[oO]"), "0")
private fun bondDate(value: String): String? {
    val text = value.trim()
    Regex("""^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})(?=\s|$)""").find(text)?.let { return isoDocumentDate(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
    val m = documentDatePattern.find(text)?.takeIf { it.range.first == 0 } ?: return null
    val year = digits(m.groupValues[3]).toInt()
    return isoDocumentDate(if (year < 100) 2000 + year else year, documentMonths.indexOf(m.groupValues[2].lowercase().take(3).replace(Regex("^jui$"), "jul")) + 1, digits(m.groupValues[1]).toInt())
}
private fun bondNumber(value: String): Double? {
    if (value.isEmpty()) return null
    val token = value.replace(Regex("\\s"), "")
    return documentNumber(token, max(2, token.substringAfter('.', "").length))
}
internal fun recognizedBond(result: JsonRecord, text: String): Boolean {
    val f = result.getJSONObject("fields")
    val terms = listOf("tenorYears", "couponRate", "settlementDate", "maturityDate").count { f.has(it) && f.text(it).isNotEmpty() }
    return (f.text("bondName").isNotEmpty() || f.text("isin").isNotEmpty()) && terms >= 2 && (f.text("issuer") == "Government of Rwanda" || text.matchesPattern("""INVESTOR\s+RESULTS|RESULTS\s+OF[\s\S]{0,80}TREASURY\s+BOND"""))
}
internal fun parseBondDocument(raw: String, usedOcr: Boolean): JsonRecord {
    familyCheck(raw)
    val text = raw.replace("\r", "").replace(Regex("[|_]"), " ")
    val fields = JsonRecord()
    val warnings = mutableListOf<String>()
    fun c(regex: String) = capture(text, regex)
    c("""(?:ISSUANCE\s+NUMBER|ISSUE\s+NUMBER)\s*[:\s]*([A-Z]+\d+\s*/\s*\d{4}\s*/\s*\d+\s*(?:YR|Y))""").takeIf { it.isNotEmpty() }?.let { fields.put("bondName", it.replace(Regex("\\s"), "").uppercase()) }
    c("""(?:BOND\s+CODE|ISIN(?:\s*/\s*SECURITY\s+CODE)?)\s*[:\s]*([A-Z]{2}[A-Z0-9/.-]{8,30})""").takeIf { it.isNotEmpty() }?.let { fields.put("isin", it.uppercase()) }
    if (text.matchesPattern("NATIONAL BANK OF RWANDA|GOVERNMENT OF RWANDA|BANKI NKURU")) fields.put("issuer", "Government of Rwanda").put("instrumentType", "treasury").put("currency", "RWF").put("market", "primary")
    val tenor = bondNumber(c("""TENOR\s*[:\s]*(\S+?)\s*Y(?:ears)?(?=\s|$)""").ifEmpty { c("""RESULTS\s+OF\s+(\d+)\s+YEAR""") })
    if (tenor != null && tenor > 0 && tenor % 1 == 0.0 && tenor <= 100) fields.put("tenorYears", tenor)
    val price = bondNumber(c("""(?:^|\n)\s*(?:EXECUTED\s+)?PRICE\s*[:\s]*(\S+)"""))
    if (price != null && price > 0 && price <= 1000) fields.put("pricePercent", price)
    bondNumber(c("""C[ao]upon\s+(?:Interest\s+)?rate\s*[:\s]*([^\s%]+)\s*%(?=\s|$)"""))?.takeIf { it <= 100 }?.let { fields.put("couponRate", it / 100) }
    val settlement = c("""SETTLEMENT\s+DATE[ \t:]*(?:\n[ \t]*)*([\dIlioO][^\n]*)""")
    val maturity = c("""REDEMP?E?TION\s+DATE[ \t:]*(?:\n[ \t]*)*([\dIlioO][^\n]*)""").ifEmpty { c("""MATURITY\s+DATE[ \t:]*(?:\n[ \t]*)*([\dIlioO][^\n]*)""") }
    bondDate(settlement)?.let { fields.put("settlementDate", it).put("purchaseDate", it); warnings.add("Trade date defaults to settlement date. Change it if your transaction used a different date.") }
    bondDate(maturity)?.let { fields.put("maturityDate", it) }
    val investorSection = pattern("""INVESTOR\s+RESULTS""").split(text).getOrNull(1)?.let { pattern("""Digitally\s+signed""").split(it)[0] }
    if (investorSection != null && investorSection.matchesPattern("Allocation") && investorSection.matchesPattern("""Coupon\s+Am[o0]unt""")) {
        val amounts = investorSection.split(Regex("\\s+")).filter { Regex("""^[+-]?\d""").containsMatchIn(it) }.map { documentNumber(it) }
        if (!investorSection.matchesPattern("""[+-]\s+\d""") && amounts.size == 4 && amounts.all { it != null } && amounts[1]!! > 0) {
            fields.put("faceValue", amounts[1])
            if (fields.has("pricePercent") && abs(jsRound(amounts[1]!! * fields.optDouble("pricePercent")) / 100 - amounts[2]!!) > 1) warnings.add("Settlement amount differs from principal × price. Enter any accrued interest and fees before saving.")
        } else warnings.add("Could not identify one investor allocation unambiguously. Enter your own face value; the total issuance amount was ignored.")
    }
    if (investorSection?.matchesPattern("""BK\s+CAPITAL""") == true) fields.put("broker", "BK Capital")
    val schedule = pattern("""COUPON\s+PAYMENT(?:\s+DATES)?([\s\S]*?)(?=SETTLEMENT\s+DATE|REDEMP?TION\s+DATE|MATURITY\s+DATE|INVESTOR\s+RESULTS|$)""").find(text)?.groupValues?.get(1)
    var invalidDates = 0
    if (schedule != null) {
        val dates = documentDatePattern.findAll(schedule).mapNotNull { bondDate(it.value).also { date -> if (date == null) invalidDates++ } }.toList()
        val unique = dates.distinct().sorted()
        if (unique.size != dates.size) invalidDates++
        if (unique.isNotEmpty()) {
            val filtered = unique.filter { (!fields.has("settlementDate") || it > fields.text("settlementDate")) && (!fields.has("maturityDate") || it <= fields.text("maturityDate")) }
            invalidDates += unique.size - filtered.size
            fields.put("couponDates", filtered).put("firstCouponDate", filtered.firstOrNull().orEmpty()).put("couponFrequency", 2)
                .put("scheduleConfidence", if (!usedOcr && invalidDates == 0 && fields.has("tenorYears") && filtered.size == fields.optInt("tenorYears") * 2) "confirmed" else "estimated")
            if (fields.has("tenorYears") && filtered.size != fields.optInt("tenorYears") * 2) warnings.add("Found ${filtered.size} coupon dates; a full ${fields.optInt("tenorYears")}-year semiannual schedule usually has ${fields.optInt("tenorYears") * 2}. Check for missing dates or a reopened bond.")
        }
    }
    if (invalidDates != 0) warnings.add("Some coupon dates were invalid, duplicated, or outside the bond term. Check the schedule against your PDF.")
    if (usedOcr) warnings.add("This PDF needed text recognition. Check every value, especially the security code, principal, and coupon dates, against the original.")
    val required = linkedMapOf("bondName" to "Bond name", "isin" to "Security code", "tenorYears" to "Tenor", "faceValue" to "Your allocated principal", "pricePercent" to "Executed price", "couponRate" to "Coupon rate", "settlementDate" to "Settlement date", "maturityDate" to "Maturity date", "firstCouponDate" to "Coupon schedule")
    return JsonRecord().put("parser", bnrInvestor()).put("fields", fields).put("warnings", warnings).put("missingFields", required.filterKeys { !fields.has(it) || fields.text(it).isEmpty() }.values.toList()).put("usedOcr", usedOcr)
}
internal fun parseIssuanceDocument(raw: String, usedOcr: Boolean): JsonRecord? {
    familyCheck(raw)
    if (!raw.matchesPattern("""SUMMARY\s+MARKET\s+RESULTS""") || raw.matchesPattern("""INVESTOR\s+RESULTS""")) return null
    val text = pattern("""((?:ISSUANCE|ISSUE)\s+NUMBER|BOND\s+CODE|SETTLEMENT\s+DATE|REDEMP?E?TION\s+DATE|MATURITY\s+DATE|Coupon\s+(?:Interest\s+)?rate|Y\s*ield\s+rate)([^\n]+)""").replace(raw) { it.groupValues[1] + " " + it.groupValues[2].replace(Regex("\\s"), "") }.let { repaired -> pattern("""((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s*-\s*)(\d)\s+(\d)\b""").replace(repaired) { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] } }
    val base = parseBondDocument(text, usedOcr)
    if (!recognizedBond(base, text)) return null
    val fields = JsonRecord().also { result -> base.getJSONObject("fields").element().filterKeys { it !in setOf("purchaseDate", "faceValue", "broker") }.forEach { (k, v) -> result.put(k, v) } }
    val summary = pattern("""SUMMARY\s+MARKET\s+RESULTS""").split(text).getOrElse(1) { "" }
    pattern("""\b(\d(?:\s*\d)?)\s*[-/]\s*(\d(?:\s*\d)?)\s*[-/]\s*(\d(?:\s*\d){3})\b""").find(summary)?.let { m -> isoDocumentDate(bondNumber(m.groupValues[3])!!.toInt(), bondNumber(m.groupValues[2])!!.toInt(), bondNumber(m.groupValues[1])!!.toInt())?.let { fields.put("issuanceDate", it) } }
    val amountMatches = Regex("""(?<![\d,.\-])\b\d(?:\s*\d){0,2}(?:\s*,\s*\d\s*\d\s*\d){2,}(?:\s*\.\s*\d+)?\b(?![\d,.])""").findAll(summary).toList()
    val totals = amountMatches.map { documentNumber(it.value.replace(Regex("\\s"), "")) }
    if (!summary.matchesPattern("""[+-]\s+\d[\d\s]*,""") && totals.size == 3 && totals.all { it != null }) listOf("amountOffered", "appliedAmount", "amountSold").forEachIndexed { i, k -> fields.put(k, totals[i]) }
    amountMatches.lastOrNull()?.let { last -> Regex("""^\s+(\d+)\s+[\d\s.]+%""").find(summary.substring(last.range.last + 1))?.let { fields.put("bidCount", it.groupValues[1].toDouble()) } }
    bondNumber(capture(text, """Y\s*ield\s+rate\s*[:\s]*([^\s%]+)\s*%(?=\s|$)"""))?.takeIf { it <= 100 }?.let { fields.put("yieldRate", it / 100) }
    val required = listOf("bondName", "isin", "tenorYears", "pricePercent", "couponRate", "settlementDate", "maturityDate", "firstCouponDate", "issuanceDate", "amountOffered", "appliedAmount", "amountSold", "bidCount", "yieldRate")
    return JsonRecord().put("kind", "bond-issuance").put("parser", bnrIssuance()).put("fields", fields).put("usedOcr", usedOcr).put("warnings", base.getJSONArray("warnings").let { list -> (0 until list.length()).map { list.getString(it) }.filterNot { it.startsWith("Trade date defaults") } }).put("missingFields", required.filter { !fields.has(it) || fields.text(it).isEmpty() })
}

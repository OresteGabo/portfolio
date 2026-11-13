package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

/** Annual projection only. Never writes an assumed rate into a contract or invents payment dates. */
data class PendingCouponEstimate(val netAnnual: Double?, val rate: Double?, val assumed: Boolean)

private data class CouponReference(val tenor: Double, val rate: Double, val date: LocalDate)

// Observed TREASURY coupons (not yields). Checked 2026-11-13, Rwanda Stock Exchange:
// https://rse.rw/outstanding-bonds and ?page_bonds-trades=5. Dates are listing issue dates.
// New verified public terms supersede these fallback points for their tenor.
private val rwandaCouponReferences = listOf(
    CouponReference(3.0, .105, LocalDate(2025, 8, 13)),
    CouponReference(5.0, .11, LocalDate(2025, 6, 18)),
    CouponReference(7.0, .115, LocalDate(2026, 7, 21)),
    CouponReference(10.0, .12, LocalDate(2026, 8, 18)),
    CouponReference(15.0, .129, LocalDate(2026, 9, 22)),
    CouponReference(20.0, .1315, LocalDate(2026, 10, 23)),
)

fun pendingCouponEstimate(purchase: JsonRecord, publicTerms: List<JsonRecord>, today: LocalDate): PendingCouponEstimate {
    fun unavailable() = PendingCouponEstimate(null, null, false)
    val face = purchase.optDouble("faceValue", Double.NaN)
    val tax = purchase.optDouble("withholdingTaxRate", Double.NaN)
    if (!face.isFinite() || face < 0 || !tax.isFinite() || tax !in 0.0..1.0) return unavailable()
    fun result(rate: Double, assumed: Boolean) = PendingCouponEstimate(decimalAmount(listOf(face, rate), taxRate = tax), rate, assumed)
    val saved = purchase.optDouble("couponRate", Double.NaN)
    if (saved.isFinite() && saved in 0.0..1.0 && (saved > 0 || purchase.optBoolean("publishedTermsAvailable") || purchase.text("issuanceDocumentId").isNotBlank()))
        return result(saved, false)
    if (purchase.text("status") != "submitted" || purchase.text("issuer") != "Government of Rwanda" || purchase.text("currency") != "RWF") return unavailable()
    val tenor = purchase.optDouble("tenorYears", Double.NaN)
    if (!tenor.isFinite() || tenor <= 0 || tenor > 100) return unavailable()
    fun isin(row: JsonRecord) = row.text("isin").uppercase().filterNot(Char::isWhitespace).removeSuffix("/MN")
    val terms = publicTerms.filter {
        it.text("issuer") == "Government of Rwanda" && it.text("currency") == "RWF" &&
            it.optDouble("couponRate", Double.NaN).let { rate -> rate.isFinite() && rate > 0 && rate <= 1 } &&
            it.optDouble("tenorYears", Double.NaN).let { years -> years.isFinite() && years > 0 && years <= 100 }
    }.distinctBy { Triple(isin(it), it.optDouble("tenorYears"), it.optDouble("couponRate")) }
    val exact = terms.filter { isin(purchase).isNotBlank() && isin(it) == isin(purchase) &&
        it.text("maturityDate") == purchase.text("maturityDate") }.map { it.optDouble("couponRate") }.distinct().singleOrNull()
    if (exact != null) return result(exact, false)
    val curve = rwandaCouponReferences.filter { it.date <= today }.associate { it.tenor to it.rate }.toMutableMap()
    // Median of unique verified security coupons prevents duplicated lots from weighting the assumption.
    terms.groupBy { it.optDouble("tenorYears") }.forEach { (years, rows) ->
        val rates = rows.map { it.optDouble("couponRate") }.sorted()
        curve[years] = if (rates.size % 2 == 1) rates[rates.size / 2] else (rates[rates.size / 2 - 1] + rates[rates.size / 2]) / 2
    }
    if (curve.isEmpty()) return unavailable()
    curve[tenor]?.let { return result(it, true) }
    val lower = curve.keys.filter { it < tenor }.maxOrNull()
    val upper = curve.keys.filter { it > tenor }.minOrNull()
    // Interpolate within observed tenors; use the nearest endpoint outside, never extrapolate higher rates.
    val rate = when {
        lower == null -> curve.getValue(requireNotNull(upper))
        upper == null -> curve.getValue(lower)
        else -> curve.getValue(lower) + (curve.getValue(upper) - curve.getValue(lower)) * (tenor - lower) / (upper - lower)
    }
    return result(rate, true)
}

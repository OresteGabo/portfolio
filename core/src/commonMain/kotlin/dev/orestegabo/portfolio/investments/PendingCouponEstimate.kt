package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

/**
 * Annual coupon projection, separate from contractual payment dates and investment gains.
 *
 * @property netAnnual Annual coupon after withholding tax, or null when no supported estimate is available.
 * @property rate Fractional annual coupon rate (0.12 means 12%), not market yield.
 * @property assumed True only when a tenor-based rate was used. False also covers an unavailable estimate;
 * callers must check [netAnnual] before treating a result as known.
 */
data class PendingCouponEstimate(val netAnnual: Double?, val rate: Double?, val assumed: Boolean)

private data class TreasuryCouponReference(val tenorYears: Double, val couponRate: Double, val listingIssueDate: LocalDate)

// Observed TREASURY coupons (not yields). Checked 2026-11-13, Rwanda Stock Exchange:
// https://rse.rw/outstanding-bonds and ?page_bonds-trades=5. Dates are listing issue dates.
// New verified public terms supersede these fallback points for their tenor.
private val rwandaTreasuryCouponReferences = listOf(
    TreasuryCouponReference(3.0, .105, LocalDate(2025, 8, 13)),
    TreasuryCouponReference(5.0, .11, LocalDate(2025, 6, 18)),
    TreasuryCouponReference(7.0, .115, LocalDate(2026, 7, 21)),
    TreasuryCouponReference(10.0, .12, LocalDate(2026, 8, 18)),
    TreasuryCouponReference(15.0, .129, LocalDate(2026, 9, 22)),
    TreasuryCouponReference(20.0, .1315, LocalDate(2026, 10, 23)),
)

/**
 * Uses saved coupon terms first. Without them, only submitted RWF Government of Rwanda purchases
 * with a valid tenor receive a matching public rate or a tenor-curve estimate. ISIN (International
 * Securities Identification Number) and maturity identify exact matches. Explicitly published zero
 * coupons are known terms; an unsubstantiated saved zero permits estimation.
 *
 * The curve combines built-in reference points dated no later than [today] with caller-supplied
 * [publicTerms]. Public terms are not date-filtered here: callers own their selection. Conflicting
 * exact-match rates fall back to the curve. Duplicate (ISIN, tenor, rate) tuples count once toward
 * each tenor's median; intermediate tenors interpolate and outlying tenors use the nearest endpoint.
 *
 * Neither input records nor contractual rates/payment dates are changed. Rates and tax use fractions,
 * and the annual monetary result uses the shared decimal rounding boundary.
 */
fun pendingCouponEstimate(purchase: JsonRecord, publicTerms: List<JsonRecord>, today: LocalDate): PendingCouponEstimate {
    fun unavailable() = PendingCouponEstimate(null, null, false)
    val principal = purchase.optDouble("faceValue", Double.NaN)
    val withholdingTaxRate = purchase.optDouble("withholdingTaxRate", Double.NaN)
    if (!principal.isFinite() || principal < 0 || !withholdingTaxRate.isFinite() || withholdingTaxRate !in 0.0..1.0) return unavailable()
    fun annualProjection(couponRate: Double, isRateAssumed: Boolean) = PendingCouponEstimate(
        decimalAmount(listOf(principal, couponRate), taxRate = withholdingTaxRate), couponRate, isRateAssumed,
    )
    val savedCouponRate = purchase.optDouble("couponRate", Double.NaN)
    if (savedCouponRate.isFinite() && savedCouponRate in 0.0..1.0 && (savedCouponRate > 0 || purchase.optBoolean("publishedTermsAvailable") || purchase.text("issuanceDocumentId").isNotBlank()))
        return annualProjection(savedCouponRate, false)
    if (purchase.text("status") != "submitted" || purchase.text("issuer") != "Government of Rwanda" || purchase.text("currency") != "RWF") return unavailable()
    val tenorYears = purchase.optDouble("tenorYears", Double.NaN)
    if (!tenorYears.isFinite() || tenorYears <= 0 || tenorYears > 100) return unavailable()
    fun normalizedIsin(row: JsonRecord) = row.text("isin").uppercase().filterNot(Char::isWhitespace).removeSuffix("/MN")
    val eligiblePublicTerms = publicTerms.filter {
        it.text("issuer") == "Government of Rwanda" && it.text("currency") == "RWF" &&
            it.optDouble("couponRate", Double.NaN).let { rate -> rate.isFinite() && rate > 0 && rate <= 1 } &&
            it.optDouble("tenorYears", Double.NaN).let { years -> years.isFinite() && years > 0 && years <= 100 }
    }.distinctBy { Triple(normalizedIsin(it), it.optDouble("tenorYears"), it.optDouble("couponRate")) }
    val matchingCouponRate = eligiblePublicTerms.filter { normalizedIsin(purchase).isNotBlank() && normalizedIsin(it) == normalizedIsin(purchase) &&
        it.text("maturityDate") == purchase.text("maturityDate") }.map { it.optDouble("couponRate") }.distinct().singleOrNull()
    if (matchingCouponRate != null) return annualProjection(matchingCouponRate, false)
    val couponRateByTenor = rwandaTreasuryCouponReferences.filter { it.listingIssueDate <= today }.associate { it.tenorYears to it.couponRate }.toMutableMap()
    // Deduplicate identical security/tenor/rate tuples above so repeated lots do not weight the median.
    eligiblePublicTerms.groupBy { it.optDouble("tenorYears") }.forEach { (years, rows) ->
        val rates = rows.map { it.optDouble("couponRate") }.sorted()
        couponRateByTenor[years] = if (rates.size % 2 == 1) rates[rates.size / 2] else (rates[rates.size / 2 - 1] + rates[rates.size / 2]) / 2
    }
    if (couponRateByTenor.isEmpty()) return unavailable()
    couponRateByTenor[tenorYears]?.let { return annualProjection(it, true) }
    val shorterTenorYears = couponRateByTenor.keys.filter { it < tenorYears }.maxOrNull()
    val longerTenorYears = couponRateByTenor.keys.filter { it > tenorYears }.minOrNull()
    // Interpolate within observed tenors; use the nearest endpoint outside, never extrapolate higher rates.
    val tenorBasedCouponRate = when {
        shorterTenorYears == null -> couponRateByTenor.getValue(requireNotNull(longerTenorYears))
        longerTenorYears == null -> couponRateByTenor.getValue(shorterTenorYears)
        else -> couponRateByTenor.getValue(shorterTenorYears) +
            (couponRateByTenor.getValue(longerTenorYears) - couponRateByTenor.getValue(shorterTenorYears)) *
            (tenorYears - shorterTenorYears) / (longerTenorYears - shorterTenorYears)
    }
    return annualProjection(tenorBasedCouponRate, true)
}

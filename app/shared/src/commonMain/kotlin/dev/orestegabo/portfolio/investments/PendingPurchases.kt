package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate

const val PendingBondCommission = 11_000.0

/** Submitted records carry no confirmed price/coupon; only active records enter totals. */
fun pendingBondInput(issue: JsonRecord, amount: Double, recorded: LocalDate): JsonRecord {
    require(amount.isFinite() && amount >= 100_000 && amount % 100_000 == 0.0) { "Use an amount in RWF 100,000 increments." }
    val settlement = calendarDate(issue.text("settlementDate")) ?: error("This bond has no settlement date yet.")
    val maturity = calendarDate(issue.text("maturityDate")) ?: error("This bond has no maturity date yet.")
    require(maturity > recorded && maturity > settlement) { "Choose a bond that has not matured." }
    require(issue.text("title").isNotBlank() && issue.optDouble("tenorYears", 0.0) > 0) { "Choose a published bond." }
    return JsonRecord().put("instrumentType", "treasury").put("issuer", "Government of Rwanda").put("currency", "RWF")
        .put("market", "primary").put("bondName", issue.text("title")).put("isin", issue.text("isin"))
        .put("purchaseDate", recorded.toString()).put("settlementDate", settlement.toString()).put("maturityDate", maturity.toString())
        .put("tenorYears", issue.optDouble("tenorYears")).put("faceValue", amount)
        // Required legacy storage defaults, never presented as executed terms or counted in active totals.
        .put("pricePercent", 100).put("couponRate", 0).put("couponFrequency", 2).put("withholdingTaxRate", .05)
        .put("firstCouponDate", "").put("couponDates", JsonList()).put("scheduleConfidence", "estimated")
        .put("accruedInterestPaid", 0).put("feesPaid", PendingBondCommission).put("broker", "").put("accountReference", "").put("sourceUrl", "")
        .put("status", "submitted").put("notes", "Pending application. Allocation, executed price and coupon are unconfirmed; stored price is provisional.")
}

fun needsPersonalConfirmation(purchase: JsonRecord) = purchase.text("status")=="submitted" ||
    purchase.text("confirmationMethod")=="manual" && purchase.text("confirmationDocumentId").isBlank()

fun pendingMatchesConfirmation(pending: JsonRecord, confirmed: JsonRecord): Boolean =
    needsPersonalConfirmation(pending) && pending.text("currency") == confirmed.text("currency") &&
        pending.text("settlementDate") == confirmed.text("settlementDate") &&
        pending.text("maturityDate") == confirmed.text("maturityDate") &&
        (pending.text("isin").isBlank() || pending.text("isin").uppercase().removeSuffix("/MN") == confirmed.text("isin").uppercase().removeSuffix("/MN")) &&
        confirmed.optDouble("faceValue", 0.0) > 0 && confirmed.optDouble("faceValue", 0.0) <= pending.optDouble("faceValue", 0.0)

fun importedPurchaseInput(review: JsonRecord): JsonRecord {
    require(review.optJSONArray("missingFields")?.length() == 0) { "Some details could not be read. Try a clearer PDF." }
    val warnings = review.optJSONArray("warnings")
    require(warnings == null || (0 until warnings.length()).none { index ->
        val text = warnings.getString(index)
        text.contains("differs", true) || text.contains("do not agree", true) || text.contains("unambiguously", true) ||
            text.contains("coupon dates were invalid", true)
    }) { "The PDF figures could not be verified. Try another confirmation PDF." }
    val body = JsonRecord(requireNotNull(review.optJSONObject("fields")) { "No purchase details were found." }.toString())
    if (review.text("kind") == "bond") {
        require(body.text("issuer") == "Government of Rwanda" && body.text("bondName").isNotBlank() && body.text("isin").isNotBlank()) { "Use a BNR bond confirmation PDF." }
        val dates = listOf("purchaseDate", "settlementDate", "maturityDate", "firstCouponDate").associateWith { LocalDate.parse(body.text(it)) }
        require(dates.getValue("settlementDate") >= dates.getValue("purchaseDate") &&
            dates.getValue("maturityDate") > dates.getValue("settlementDate")) { "The PDF dates could not be verified." }
        require(body.optDouble("faceValue", 0.0).let { it.isFinite() && it >= 100_000 && it % 100_000 == 0.0 } &&
            body.optDouble("pricePercent", 0.0).let { it.isFinite() && it > 0 && it <= 1000 } &&
            body.has("couponRate") && body.optDouble("couponRate").let { it.isFinite() && it in 0.0..1.0 } &&
            body.optDouble("tenorYears", 0.0).let { it.isFinite() && it > 0 && it <= 100 }) { "Some figures could not be read. Try a clearer PDF." }
        body.put("instrumentType", "treasury").put("currency", "RWF").put("market", "primary").put("withholdingTaxRate", .05)
        listOf("accruedInterestPaid", "feesPaid").forEach { if (!body.has(it)) body.put(it, 0) }
        body.put("notes", "").put("sourceUrl", "").put("accountReference", "")
    } else {
        require(review.text("kind") == "equity") { "Unsupported purchase document." }
    }
    return body.put("status", "active")
}

/** Keep the recorded commission once; the PDF supplies the final allocation and terms. */
fun completePendingPurchase(review: JsonRecord, pending: JsonRecord?): JsonRecord {
    val confirmed = importedPurchaseInput(review)
    if (pending != null) {
        require(pendingMatchesConfirmation(pending, confirmed)) { "This PDF does not match the pending purchase." }
        confirmed.put("feesPaid", pending.optDouble("feesPaid", 0.0))
    }
    return confirmed
}

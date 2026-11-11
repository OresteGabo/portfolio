package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun importedIssuanceInput(review: JsonRecord): JsonRecord {
    require(review.text("kind") == "bond-issuance" && review.optJSONArray("missingFields")?.length() == 0) {
        "Some issuance details could not be read. Choose a clearer BNR market results PDF."
    }
    val p = requireNotNull(review.optJSONObject("fields"))
    require(p.text("isin").matches(Regex("RW[A-Z0-9]{8,30}/MN")) && p.text("bondName").isNotBlank() &&
        p.text("issuer") == "Government of Rwanda" && !p.has("faceValue")) { "Use a BNR market results PDF." }
    listOf("issuanceDate", "settlementDate", "maturityDate", "firstCouponDate").forEach {
        require(calendarDate(p.text(it)) != null) { "Check the issuance dates." }
    }
    require(p.text("issuanceDate") <= p.text("settlementDate") && p.text("settlementDate") < p.text("maturityDate")) { "Check the issuance dates." }
    listOf("tenorYears", "pricePercent", "couponRate", "yieldRate", "amountOffered", "appliedAmount", "amountSold", "bidCount").forEach {
        require(p.optDouble(it).let { n -> n.isFinite() && n >= 0 }) { "Check the issuance figures." }
    }
    require(p.optDouble("tenorYears") in 1.0..100.0 && p.optDouble("tenorYears") % 1 == 0.0 &&
        p.optDouble("pricePercent") > 0 && p.optDouble("pricePercent") <= 1000 &&
        p.optDouble("couponRate") <= 1 && p.optDouble("yieldRate") <= 1 && p.optDouble("amountOffered") > 0 &&
        p.optDouble("amountSold") <= p.optDouble("appliedAmount") && p.optDouble("bidCount") % 1 == 0.0) { "Check the issuance figures." }
    val dates = p.getJSONArray("couponDates").let { a -> (0 until a.length()).map(a::getString) }
    require(dates.isNotEmpty() && dates == dates.distinct().sorted() && dates.all {
        calendarDate(it) != null && it > p.text("settlementDate") && it <= p.text("maturityDate")
    } && dates.first() == p.text("firstCouponDate") && dates.last() == p.text("maturityDate")) { "Check the coupon schedule." }
    return p
}

/** A targeted review explicitly confirms the recorded full allocation; auction totals never supply it. */
fun pendingIssuanceConfirmation(review: JsonRecord, pending: JsonRecord): JsonRecord {
    val issue = importedIssuanceInput(review)
    require(pending.text("status") == "submitted" && pending.text("currency") == "RWF" &&
        pending.text("issuer") == "Government of Rwanda" && pending.text("market") == "primary" &&
        pending.text("settlementDate") == issue.text("settlementDate") &&
        pending.text("maturityDate") == issue.text("maturityDate") &&
        pending.optDouble("tenorYears") == issue.optDouble("tenorYears") &&
        (pending.text("isin").isBlank() || pending.text("isin").uppercase().removeSuffix("/MN") == issue.text("isin").uppercase().removeSuffix("/MN"))) {
        "This PDF does not match the pending bond or applied amount."
    }
    val principal = pending.optDouble("faceValue")
    val fees = pending.optDouble("feesPaid", 0.0)
    val accrued = pending.optDouble("accruedInterestPaid", 0.0)
    val recordedCash = pending.optDouble("amountInvested", principal + fees + accrued)
    val cost = principal * issue.optDouble("pricePercent") / 100 + fees + accrued
    require(principal.isFinite() && principal >= 100_000 && principal % 100_000 == 0.0 &&
        fees.isFinite() && fees >= 0 && accrued.isFinite() && accrued >= 0 &&
        recordedCash.isFinite() && kotlin.math.abs(recordedCash - cost) < .01) {
        "Use your personal PDF to confirm the final allocation and price."
    }
    return JsonRecord(issue.toString()).put("faceValue", principal).put("feesPaid", fees)
        .put("accruedInterestPaid", accrued).put("amountInvested", cost).put("currency", "RWF")
}

/** Fill only unread public terms after an exact security/settlement match; never invent an allocation or price. */
fun purchaseReviewWithPublishedTerms(review: JsonRecord, documents: List<JsonRecord>): JsonRecord {
    if (review.text("kind") != "bond") return review
    val original = review.optJSONObject("fields") ?: return review
    if (original.text("isin").isBlank() || calendarDate(original.text("settlementDate")) == null ||
        original.text("issuer") != "Government of Rwanda" || original.text("currency") != "RWF") return review
    val document = documents.filter { doc ->
        val issue = doc.optJSONObject("issuance")
        doc.text("category") == "Bond issuance" && issue != null &&
            issue.text("isin").uppercase().removeSuffix("/MN") == original.text("isin").uppercase().removeSuffix("/MN") &&
            issue.text("settlementDate") == original.text("settlementDate") &&
            (original.text("maturityDate").isBlank() || original.text("maturityDate") == issue.text("maturityDate"))
    }.singleOrNull() ?: return review
    val issue = document.getJSONObject("issuance")
    if (runCatching { importedIssuanceInput(JsonRecord().put("kind", "bond-issuance").put("fields", issue).put("missingFields", JsonList())) }.isFailure) return review
    val fields = JsonRecord(original.toString())
    val supplied = mutableSetOf<String>()
    for ((key, label) in listOf("bondName" to "Bond name", "maturityDate" to "Maturity date", "firstCouponDate" to "Coupon schedule")) {
        if (fields.text(key).isBlank()) { fields.put(key, issue.text(key)); supplied += label }
    }
    for ((key, label) in listOf("tenorYears" to "Tenor", "couponRate" to "Coupon rate")) {
        if (fields.isNull(key)) { fields.put(key, issue.getDouble(key)); supplied += label }
    }
    if ((fields.optJSONArray("couponDates")?.length() ?: 0) == 0) {
        fields.put("couponDates", issue.getJSONArray("couponDates")).put("couponFrequency", issue.optInt("couponFrequency", 2))
            .put("scheduleConfidence", issue.text("scheduleConfidence").ifBlank { "estimated" })
        supplied += "Coupon schedule"
    }
    if (supplied.isEmpty()) return review
    val missing = review.optJSONArray("missingFields") ?: return review
    return JsonRecord(review.toString()).put("fields", fields).put("termsSourceDocumentId", document.text("id"))
        .put("missingFields", JsonList((0 until missing.length()).map(missing::getString).filterNot { it in supplied }))
}

/** Fill missing security facts only; private acquisition prices, fees and allocations are never inputs. */
internal fun purchaseReviewWithSecurityTerms(review: JsonRecord, terms: List<JsonRecord>): JsonRecord {
    if(review.text("kind")!="bond") return review
    val original=review.optJSONObject("fields") ?: return review
    if(original.text("issuer")!="Government of Rwanda" || original.text("currency")!="RWF" || original.text("isin").isBlank())return review
    fun security(p: JsonRecord)=p.text("isin").uppercase().filterNot(Char::isWhitespace).removeSuffix("/MN")
    val candidate=terms.singleOrNull { security(it)==security(original) && it.text("maturityDate")==original.text("maturityDate") } ?: return review
    val readRate=original.optDouble("couponRate",Double.NaN)
    if(readRate.isFinite() && readRate!=candidate.optDouble("couponRate",Double.NaN))return review
    val fields=JsonRecord(original.toString());val supplied=mutableSetOf<String>()
    for((key,label) in listOf("couponRate" to "Coupon rate","tenorYears" to "Tenor")) {
        if(fields.isNull(key)) candidate.optDouble(key,Double.NaN).takeIf { it.isFinite() && it>0 }?.let { fields.put(key,it);supplied+=label }
    }
    val purchase=calendarDate(original.text("purchaseDate")) ?: return review
    val dates=candidate.optJSONArray("couponDates")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty().filter { calendarDate(it)?.let { day -> day>purchase && it<=original.text("maturityDate") }==true }.distinct().sorted()
    if((fields.optJSONArray("couponDates")?.length() ?: 0)==0 && dates.isNotEmpty() &&
        (fields.text("firstCouponDate").isBlank() || fields.text("firstCouponDate") in dates)) {
        fields.put("couponDates",JsonList(dates)).put("couponFrequency",candidate.optInt("couponFrequency",2)).put("scheduleConfidence","confirmed")
        if(fields.text("firstCouponDate").isBlank())fields.put("firstCouponDate",dates.first())
        supplied+="Coupon schedule"
    }
    if(supplied.isEmpty())return review
    val missing=review.optJSONArray("missingFields") ?: return review
    return JsonRecord(review.toString()).put("fields",fields).put("termsSource","shared-coupon-terms")
        .put("missingFields",JsonList((0 until missing.length()).map(missing::getString).filterNot { it in supplied }))
}

/** Overlay the auction's actual terms on its published calendar row; retain book-open dates. */
fun mergeBondIssuances(published: List<JsonRecord>, documents: List<JsonRecord>, couponTerms: List<JsonRecord> = emptyList()): List<JsonRecord> {
    val result = published.toMutableList()
    documents.filter { it.text("category") == "Bond issuance" }.forEach { document ->
        val issue = document.optJSONObject("issuance") ?: return@forEach
        val index = result.indexOfFirst { it.text("settlementDate") == issue.text("settlementDate") &&
            it.text("maturityDate") == issue.text("maturityDate") }
        val row = if (index >= 0) JsonRecord(result[index].toString()) else JsonRecord().put("id", "issuance:${document.text("id")}")
        row.put("title", issue.text("bondName")).put("bondName", issue.text("bondName")).put("isin", issue.text("isin"))
            .put("tenorYears", issue.optDouble("tenorYears")).put("settlementDate", issue.text("settlementDate"))
            .put("maturityDate", issue.text("maturityDate")).put("issuance", issue)
        if (index >= 0) result[index] = row else result += row
    }
    // Calendar identity hints only: unique public security terms, never investor records or auction prices.
    for (index in result.indices) {
        var row=result[index]
        val formTerms = bondApplicationFor(row, documents)?.optJSONObject("bidTerms")
        if (formTerms != null && formTerms.text("issuanceDate") == row.text("closingBookDate") &&
            formTerms.text("settlementDate") == row.text("settlementDate") && formTerms.text("maturityDate") == row.text("maturityDate") &&
            formTerms.optInt("tenorYears") == row.optInt("tenorYears") && formTerms.text("bondName").isNotBlank()) {
            row = JsonRecord(row.toString()).put("bondName", formTerms.text("bondName"))
            result[index] = row
        }
        if (row.text("isin").isBlank() || row.text("bondName").isBlank()) {
            val candidates=couponTerms.filter { it.text("maturityDate")==row.text("maturityDate") &&
                it.optDouble("tenorYears",Double.NaN)==row.optDouble("tenorYears",Double.NaN) &&
                it.text("issuer")=="Government of Rwanda" && it.text("currency")=="RWF" &&
                (row.text("isin").isBlank() || row.text("isin").uppercase().removeSuffix("/MN") == it.text("isin").uppercase().removeSuffix("/MN")) }
            candidates.singleOrNull()?.let {
                val enriched = JsonRecord(row.toString())
                if (row.text("isin").isBlank()) enriched.put("isin", it.text("isin"))
                if (row.text("bondName").isBlank() && it.text("bondName").isNotBlank()) enriched.put("bondName", it.text("bondName"))
                result[index] = enriched
            }
        }
    }
    // The published calendar explicitly names the original auction. Inherit only its security
    // identity, never its auction price/yield or settlement/coupon dates.
    return result.map { row ->
        val original = result.singleOrNull { it.text("id") == row.text("reopensIssueId") && row.text("reopensIssueId").isNotBlank() }
        val verified = original?.optJSONObject("issuance")
        if (row.text("kind") == "reopen" && original != null && row.text("maturityDate") == original.text("maturityDate")) {
            val enriched = JsonRecord(row.toString())
            if (row.text("bondName").isBlank() && original.text("bondName").isNotBlank()) enriched.put("bondName", original.text("bondName"))
            if (row.text("isin").isBlank() && verified != null) enriched.put("isin", verified.text("isin"))
            enriched
        } else row
    }
}

@Composable
internal fun PublicIssuanceConsent(checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { change(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = change)
        AppText("This is a public BNR issuance report, not a personal contract or statement. I agree to share it with all users.", style = MaterialTheme.typography.bodySmall)
    }
}

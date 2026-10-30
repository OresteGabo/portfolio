package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

/** Overlay the auction's actual terms on its published calendar row; retain book-open dates. */
fun mergeBondIssuances(published: List<JsonRecord>, documents: List<JsonRecord>): List<JsonRecord> {
    val result = published.toMutableList()
    documents.filter { it.text("category") == "Bond issuance" }.forEach { document ->
        val issue = document.optJSONObject("issuance") ?: return@forEach
        val index = result.indexOfFirst { it.text("settlementDate") == issue.text("settlementDate") &&
            it.text("maturityDate") == issue.text("maturityDate") }
        val row = if (index >= 0) JsonRecord(result[index].toString()) else JsonRecord().put("id", "issuance:${document.text("id")}")
        row.put("title", issue.text("bondName")).put("isin", issue.text("isin"))
            .put("tenorYears", issue.optDouble("tenorYears")).put("settlementDate", issue.text("settlementDate"))
            .put("maturityDate", issue.text("maturityDate")).put("issuance", issue)
        if (index >= 0) result[index] = row else result += row
    }
    return result
}

@Composable
fun BondIssuanceReview(model: PortfolioModel, review: JsonRecord, target: JsonRecord? = null, done: () -> Unit) {
    DisposableEffect(review) { onDispose { model.discardImportedPdf(review) } }
    val parsed = remember(review) { runCatching { importedIssuanceInput(review) } }
    val p = review.optJSONObject("fields") ?: JsonRecord()
    val confirmation = remember(review, target) { target?.let { runCatching { pendingIssuanceConfirmation(review, it) } } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppText(if (target == null) "Review bond issuance" else "Review purchase", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { AppText(if (model.user?.optBoolean("isOwner") == true) "This public report and its bond details will be available to all invited users." else
            "Only the owner can publish shared issuance results. Ask for this public report to be reviewed, or upload your personal allocation confirmation.", style = MaterialTheme.typography.bodySmall) }
        if (target == null) item { AppText("Matching pending purchases receive these terms. Your allocation stays unconfirmed.") }
        else item { Panel {
            AppText("Confirm purchase", fontWeight = FontWeight.Bold)
            AppText("Confirm that your full applied amount was allocated and these figures are correct.")
            Fact("Amount applied", money(target.optDouble("faceValue"), target.text("currency")))
            Fact("Commission", money(target.optDouble("feesPaid", 0.0), target.text("currency")))
            confirmation?.getOrNull()?.let { Fact("Total cash cost", money(it.optDouble("amountInvested"), "RWF")) }
            AppText("The public report supplies the bond terms. Your recorded amount is used for this purchase.", style = MaterialTheme.typography.bodySmall)
        } }
        item { Panel {
            AppText(p.text("bondName"), fontWeight = FontWeight.Bold)
            Fact("Tenor (years)", p.text("tenorYears")); Fact("Security code", p.text("isin")); Fact("Issuance date", p.text("issuanceDate"))
            Fact("Settlement", p.text("settlementDate")); Fact("Maturity", p.text("maturityDate"))
            Fact("Price", "${p.text("pricePercent")}%"); Fact("Coupon", percent(p.optDouble("couponRate")))
            Fact("Yield", percent(p.optDouble("yieldRate")))
            listOf("amountOffered" to "Amount offered", "appliedAmount" to "Applied amount", "amountSold" to "Amount sold").forEach { (key, label) ->
                Fact(label, money(p.optDouble(key), "RWF"))
            }
            Fact("Bids", p.text("bidCount"))
        } }
        item { Panel {
            AppText("Coupon payment dates", fontWeight = FontWeight.SemiBold)
            val dates = p.optJSONArray("couponDates") ?: JsonList()
            for (i in 0 until dates.length()) Fact("${i + 1}", dates.getString(i))
        } }
        val warnings = review.optJSONArray("warnings") ?: JsonList()
        for (i in 0 until warnings.length()) item { AppText(warnings.getString(i), style = MaterialTheme.typography.bodySmall) }
        if (parsed.isFailure) item { AppText(parsed.exceptionOrNull()?.message.orEmpty(), color = MaterialTheme.colorScheme.error) }
        item { AppText("Repeated uploads reuse the saved issuance. Different figures require review.", style = MaterialTheme.typography.bodySmall) }
        if (confirmation?.isFailure == true) item { AppText(confirmation.exceptionOrNull()?.message.orEmpty(), color = MaterialTheme.colorScheme.error) }
        item { Button(enabled = model.user?.optBoolean("isOwner") == true && parsed.isSuccess && (target == null || confirmation?.isSuccess == true) && !model.busy,
            onClick = { model.saveImportedIssuance(review, target, done) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            AppText(if (target == null) "Save bond issuance" else "Save PDF and confirm purchase")
        } }
    }
}

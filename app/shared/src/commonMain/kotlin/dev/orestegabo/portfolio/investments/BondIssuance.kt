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
fun BondIssuanceReview(model: PortfolioModel, review: JsonRecord, done: () -> Unit) {
    DisposableEffect(review) { onDispose { model.discardImportedPdf(review) } }
    val parsed = remember(review) { runCatching { importedIssuanceInput(review) } }
    val p = review.optJSONObject("fields") ?: JsonRecord()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppText("Review bond issuance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { AppText("This public report and its bond details will be available to all invited users.", style = MaterialTheme.typography.bodySmall) }
        item { AppText("Matching pending purchases receive these terms. Your allocation stays unconfirmed.") }
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
        item { Button(enabled = parsed.isSuccess && !model.busy, onClick = { model.saveImportedIssuance(review, done) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { AppText("Save bond issuance") } }
    }
}

package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ConversationReview(model: PortfolioModel, review: JsonRecord, target: JsonRecord?, entry: ImportReceipt, done: () -> Unit) {
    val related = entry.targetId.takeIf(String::isNotBlank)?.let { id -> model.purchases.firstOrNull { it.text("id") == id }
        ?: target?.takeIf { it.text("id") == id } }
    when (review.text("kind")) {
        "bond", "equity" -> PurchaseConversationReview(model, review, related, entry, done)
        "bond-issuance" -> IssuanceConversationReview(model, review, related, entry, done)
        "statement" -> {
            AppText("BK Capital statement", fontWeight = FontWeight.SemiBold)
            AppText("${review.text("fromDate")} – ${review.text("toDate")}", style = MaterialTheme.typography.bodyMedium)
            Fact("Purchases", review.rows("rows").size.toString())
            Fact("Other movements", review.rows("movements").size.toString())
            if (review.optBoolean("usedOcr")) AppText("Text recognition used. Check every amount against the PDF.", style = MaterialTheme.typography.bodySmall)
            AppText("Statement entries are checked against your investments. Missing contracts become reminders; they do not add unconfirmed amounts to your portfolio.", style = MaterialTheme.typography.bodySmall)
            Button(enabled = !model.busy && (review.rows("rows").isNotEmpty() || review.rows("movements").isNotEmpty()), modifier = Modifier.fillMaxWidth(),
                onClick = { model.saveImportedStatement(review, done) }) {
                AppText(if (entry.documentId.isNotBlank()) "Check records using saved PDF" else if (entry.state == ImportState.FAILED) "Retry save" else "Save PDF and check records")
            }
        }
        else -> AppText("Unsupported purchase document.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PurchaseConversationReview(model: PortfolioModel, review: JsonRecord, target: JsonRecord?, entry: ImportReceipt, done: () -> Unit) {
    var selection by rememberSaveable(entry.id) { mutableStateOf(target?.text("id")) }
    val state = PurchaseReviewState(review, model.documents, model.purchases, model.historyComplete, target, selection)
    LaunchedEffect(entry.id, model.busy, model.historyComplete) {
        if (state.bond && !model.historyComplete && !model.busy && model.error == null) model.loadInvestmentHistory()
    }
    val body = state.body
    if (body == null) {
        AppText(userErrorMessage(state.parsed.exceptionOrNull(), "Could not read this PDF."), color = MaterialTheme.colorScheme.error)
        return
    }
    if (state.wrongTarget) AppText("This PDF does not match the pending bond or applied amount.", color = MaterialTheme.colorScheme.error)
    if (state.bond && !model.historyComplete) TextButton(enabled = !model.busy, onClick = { model.error = null; model.loadInvestmentHistory() }) { AppText("Retry loading pending purchases") }
    if (target == null && state.matches.isNotEmpty()) {
        AppText("Match an existing purchase", fontWeight = FontWeight.SemiBold)
        state.matches.forEach { row ->
            Row(Modifier.fillMaxWidth().clickable(enabled = !model.busy) { selection = row.text("id") }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(state.selectedId == row.text("id"), null)
                Column(Modifier.weight(1f)) {
                    FinancialText(row.text("bondName"), style = MaterialTheme.typography.bodyMedium)
                    FinancialText(money(row.optDouble("faceValue"), row.text("currency")), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    if (target != null || state.selected != null) AppText("Matches your existing purchase.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    FinancialText(body.text(if (state.bond) "bondName" else "securityName"), fontWeight = FontWeight.Bold)
    if (state.bond) {
        Fact("Allocated", money(body.optDouble("faceValue"), body.text("currency")))
        Fact("Settlement", body.text("settlementDate")); Fact("Maturity", body.text("maturityDate"))
        Fact("Coupon", percent(body.optDouble("couponRate"))); Fact("Price", "${body.optDouble("pricePercent")}%")
        state.confirmed?.let { Fact("Commission", money(it.optDouble("feesPaid", 0.0), it.text("currency"))) }
    } else {
        Fact("Shares", body.text("shares")); Fact("Cost", money(body.optDouble("netAmountPayable"), body.text("currency")))
        Fact("Trade date", body.text("tradeDate"))
    }
    if (state.effectiveReview.has("termsSourceDocumentId")) AppText("Coupon details from the shared BNR issuance report", style = MaterialTheme.typography.labelSmall)
    if (review.optBoolean("usedOcr")) AppText("Text recognition used. Check the figures against your PDF.", style = MaterialTheme.typography.bodySmall)
    Button(enabled = state.ready && !model.busy, modifier = Modifier.fillMaxWidth(), onClick = {
        state.confirmed?.let { model.saveImportedPurchase(review, if (state.bond) it else body, state.selected?.text("id"), done) }
    }) {
        AppText(if (entry.documentId.isNotBlank()) "Confirm using saved PDF" else if (entry.state == ImportState.FAILED) "Retry save" else if (state.selected != null) "Save PDF and confirm purchase" else "Save purchase and PDF")
    }
}

@Composable
private fun IssuanceConversationReview(model: PortfolioModel, review: JsonRecord, target: JsonRecord?, entry: ImportReceipt, done: () -> Unit) {
    val parsed = remember(review) { runCatching { importedIssuanceInput(review) } }
    val fields = parsed.getOrNull()
    if (fields == null) { AppText(userErrorMessage(parsed.exceptionOrNull(), "Could not read this PDF."), color = MaterialTheme.colorScheme.error); return }
    val confirmation = target?.let { runCatching { pendingIssuanceConfirmation(review, it) } }
    FinancialText(fields.text("bondName"), fontWeight = FontWeight.Bold)
    AppText("This public report and its bond details will be available to all invited users.", style = MaterialTheme.typography.bodySmall)
    if (target != null) {
        AppText("Confirm that your full applied amount was allocated and these figures are correct.", style = MaterialTheme.typography.bodyMedium)
        Fact("Amount applied", money(target.optDouble("faceValue"), target.text("currency")))
        Fact("Commission", money(target.optDouble("feesPaid", 0.0), target.text("currency")))
        confirmation?.getOrNull()?.let { Fact("Total cash cost", money(it.optDouble("amountInvested"), "RWF")) }
    } else AppText("This saves the public terms. It does not confirm a personal allocation.", style = MaterialTheme.typography.bodyMedium)
    Fact("Security code", fields.text("isin")); Fact("Settlement", fields.text("settlementDate"))
    Fact("Maturity", fields.text("maturityDate")); Fact("Price", "${fields.optDouble("pricePercent")}%")
    Fact("Coupon", percent(fields.optDouble("couponRate"))); Fact("First coupon", fields.text("firstCouponDate"))
    var showSchedule by rememberSaveable(entry.id) { mutableStateOf(false) }
    TextButton(onClick = { showSchedule = !showSchedule }) { AppText("Coupon payment dates") }
    if (showSchedule) fields.optJSONArray("couponDates")?.let { dates -> for (i in 0 until dates.length()) Fact("${i + 1}", dates.getString(i)) }
    review.optJSONArray("warnings")?.let { warnings -> for (i in 0 until warnings.length()) AppText(warnings.getString(i), style = MaterialTheme.typography.bodySmall) }
    if (confirmation?.isFailure == true) AppText(userErrorMessage(confirmation.exceptionOrNull()), color = MaterialTheme.colorScheme.error)
    Button(enabled = !model.busy && (target == null || confirmation?.isSuccess == true), modifier = Modifier.fillMaxWidth(),
        onClick = { model.saveImportedIssuance(review, target, done) }) {
        AppText(if (entry.documentId.isNotBlank() && target != null) "Confirm using saved PDF" else if (entry.documentId.isNotBlank()) "Use saved issuance report" else if (entry.state == ImportState.FAILED) "Retry save" else if (target != null) "Save PDF and confirm purchase" else "Save bond issuance")
    }
}

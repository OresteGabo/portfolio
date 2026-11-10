package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingBondScreen(model: PortfolioModel, initialIssueId: String? = null, done: () -> Unit) {
    val today = rememberBondCalendarToday()
    val choices = model.calendar.filter { calendarTemporaryPurchaseAllowed(it, today) }
    var selectedId by rememberSaveable(initialIssueId) { mutableStateOf(initialIssueId ?: choices.lastOrNull { it.text("settlementDate") <= Today.toString() }?.text("id") ?: choices.firstOrNull()?.text("id")) }
    val issue = choices.find { it.text("id") == selectedId }
    var amount by rememberSaveable(initialIssueId) { mutableStateOf(ApplicationAmountStep.toString()) }
    var choosing by remember { mutableStateOf(false) }
    val draft = issue?.let { runCatching { pendingBondInput(it, amount.toInputNumberOrNull() ?: 0.0, today) }.getOrNull() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (choices.isEmpty()) item { EmptyState("No published bonds", EmptyArtwork.BONDS, compact = true) }
        else {
            item { PendingBondHeader(issue?.let(::calendarBondName) ?: "Choose a bond", issue?.text("settlementDate").orEmpty(),
                issue?.text("maturityDate").orEmpty(), choose = if (initialIssueId == null) ({ choosing = true }) else null) }
            item { PendingPurchaseNotice() }
            item { ApplicationAmountSelector("Amount applied (RWF)", amount, { amount = it }, enabled = issue != null && !model.busy) }
            item { PendingPurchaseSummary(amount.toInputNumberOrNull() ?: 0.0) }
            item { Button(enabled = draft != null && !model.busy, onClick = { draft?.let { purchase ->
                if (issue != null && calendarTemporaryPurchaseAllowed(issue, Today)) model.savePurchase(purchase, null, done)
            } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { AppText("Save pending purchase") } }
            item { PendingPurchaseDisclaimer() }
        }
    }
    if (choosing) ModalBottomSheet(onDismissRequest = { choosing = false }) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(choices, key = { it.text("id") }) { row ->
                ListItem(headlineContent = { AppText(row.text("title")) }, supportingContent = { AppText(row.text("settlementDate")) },
                    leadingContent = { RadioButton(selected = selectedId == row.text("id"), onClick = null) },
                    modifier = Modifier.clickable { selectedId = row.text("id"); choosing = false })
            }
        }
    }
}

@Composable
fun PurchaseImportReview(model: PortfolioModel, review: JsonRecord, target: JsonRecord?, done: () -> Unit) {
    DisposableEffect(review) { onDispose { model.discardImportedPdf(review) } }
    var selection by rememberSaveable(review, target?.text("id")) { mutableStateOf(target?.text("id")) }
    val state = PurchaseReviewState(review, model.documents, model.purchases, model.historyComplete, target, selection, model.sharedCouponTerms)
    val effectiveReview = state.effectiveReview
    val parsed = state.parsed
    val body = state.body
    val bond = state.bond
    val matches = state.matches
    val selectedId = state.selectedId
    val selected = state.selected
    val confirmedBody = state.confirmed
    val wrongTarget = state.wrongTarget
    val ready = state.ready
    LaunchedEffect(review, model.busy, model.historyComplete) {
        if (bond && !model.historyComplete && !model.busy && model.error == null) model.loadInvestmentHistory()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppText("Review purchase", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (effectiveReview.text("termsSource")=="shared-coupon-terms") item { AppText("Missing coupon details supplied from shared public security terms.", style = MaterialTheme.typography.labelSmall) }
        if (effectiveReview.has("termsSourceDocumentId")) item { AppText("Coupon details from the shared BNR issuance report", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (body == null) item { EmptyState(userErrorMessage(parsed.exceptionOrNull(), "Could not read this PDF."), EmptyArtwork.DOCUMENTS, compact = true) }
        else {
            if (wrongTarget) item { NoticeForImport("This PDF does not match the pending bond or applied amount.") }
            if (bond && !model.historyComplete) item {
                if (model.busy) LoadingState() else TextButton(onClick = { model.error = null; model.loadInvestmentHistory() }) { AppText("Retry loading pending purchases") }
            }
            if (target == null && matches.isNotEmpty()) {
                item { AppText("Match an existing purchase", fontWeight = FontWeight.SemiBold) }
                items(matches, key = { it.text("id") }) { row ->
                    ListItem(headlineContent = { FinancialText(row.text("bondName")) }, supportingContent = { FinancialText("Applied · ${money(row.optDouble("faceValue"), row.text("currency"))}") },
                        leadingContent = { RadioButton(selected = selectedId == row.text("id"), onClick = null) },
                        modifier = Modifier.clickable { selection = row.text("id") })
                }
            }
            if (target != null) item { AppText(if(target?.text("status")=="submitted") "Updates your pending purchase" else "Adds your confirmation PDF", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            item { Panel {
                if (bond) {
                    AppText(body.text("bondName"), fontWeight = FontWeight.Bold)
                    Fact("Allocated", money(body.optDouble("faceValue"), body.text("currency")))
                    Fact("Settlement", body.text("settlementDate"))
                    Fact("Maturity", body.text("maturityDate"))
                    Fact("Coupon", percent(body.optDouble("couponRate")))
                    Fact("Price", "${body.optDouble("pricePercent")}%")
                    confirmedBody?.let { Fact("Commission", money(it.optDouble("feesPaid", 0.0), it.text("currency"))) }
                } else {
                    AppText(body.text("securityName"), fontWeight = FontWeight.Bold)
                    Fact("Shares", body.text("shares")); Fact("Cost", money(body.optDouble("netAmountPayable"), body.text("currency")))
                    Fact("Trade date", body.text("tradeDate"))
                }
            } }
            if (review.optBoolean("usedOcr")) item { AppText("Text recognition used. Check the figures against your PDF.", style = MaterialTheme.typography.bodySmall) }
            item { Button(enabled = ready && !model.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {
                confirmedBody?.let { model.saveImportedPurchase(review, if (bond) it else body, selected?.text("id"), done) }
            }) { AppText(if (selected != null) "Confirm purchase" else "Save purchase") } }
        }
    }
}

@Composable private fun NoticeForImport(text: String) { AppText(text, color = MaterialTheme.colorScheme.error) }

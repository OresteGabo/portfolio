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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingBondScreen(model: PortfolioModel, initialIssueId: String? = null, done: () -> Unit) {
    val choices = model.calendar.filter { calendarDate(it.text("settlementDate")) != null && (calendarDate(it.text("maturityDate")) ?: Today) > Today }
    var selectedId by rememberSaveable(initialIssueId) { mutableStateOf(initialIssueId ?: choices.lastOrNull { it.text("settlementDate") <= Today.toString() }?.text("id") ?: choices.firstOrNull()?.text("id")) }
    val issue = choices.find { it.text("id") == selectedId }
    var amount by rememberSaveable { mutableStateOf("") }
    var choosing by remember { mutableStateOf(false) }
    val draft = issue?.let { runCatching { pendingBondInput(it, amount.toInputNumberOrNull() ?: 0.0, Today) }.getOrNull() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppText("Pending bond", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { AppText("No file needed. Confirm later with your PDF or published issuance terms.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (choices.isEmpty()) item { EmptyState("No published bonds", EmptyArtwork.BONDS, compact = true) }
        else {
            item { ListItem(headlineContent = { AppText(issue?.text("title") ?: "Choose a bond") },
                supportingContent = { AppText(issue?.text("settlementDate").orEmpty()) },
                trailingContent = { AppIcon(Icons.Outlined.ArrowDropDown, "Choose bond") }, modifier = Modifier.clickable { choosing = true }) }
            item { Field("Amount applied (RWF)", amount, { amount = it.filter(Char::isDigit).take(15) }, KeyboardType.Number, "In RWF 100,000 increments") }
            item { Panel {
                Fact("Commission", money(PendingBondCommission, "RWF"))
                Fact("Total paid", money((amount.toInputNumberOrNull() ?: 0.0) + PendingBondCommission, "RWF"))
            } }
            item { Button(enabled = draft != null && !model.busy, onClick = { draft?.let { model.savePurchase(it, null, done) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { AppText("Save pending purchase") } }
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
    val effectiveReview = remember(review, model.documents) { purchaseReviewWithPublishedTerms(review, model.documents) }
    val parsed = remember(effectiveReview) { runCatching { importedPurchaseInput(effectiveReview) } }
    val body = parsed.getOrNull()
    val bond = review.text("kind") == "bond"
    LaunchedEffect(review, model.busy, model.historyComplete) {
        if (bond && !model.historyComplete && !model.busy && model.error == null) model.loadInvestmentHistory()
    }
    val matches = if (body == null) emptyList() else model.purchases.filter { pendingMatchesConfirmation(it, body) }
    var selection by rememberSaveable(review, target?.text("id")) { mutableStateOf(target?.text("id")) }
    // Resolve the single match after full history arrives, without a transient "new" save.
    val selectedId = selection ?: matches.singleOrNull()?.text("id")
    val selected = model.purchases.find { it.text("id") == selectedId }
    val confirmedBody = if (body == null) null else runCatching { completePendingPurchase(effectiveReview, selected) }.getOrNull()
    val wrongTarget = target != null && (body == null || !bond || !pendingMatchesConfirmation(target, body))
    val ready = body != null && !wrongTarget && (!bond || model.historyComplete) &&
        (selected != null && pendingMatchesConfirmation(selected, body) || target == null && matches.isEmpty()) && confirmedBody != null
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppText("Review purchase", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
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

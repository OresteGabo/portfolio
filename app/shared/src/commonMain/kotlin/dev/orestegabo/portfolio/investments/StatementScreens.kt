package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A statement debit is not a bond principal or a complete share contract. */
fun statementConfirmationMatches(item: JsonRecord, review: JsonRecord): Boolean {
    val purchase = runCatching { importedPurchaseInput(review) }.getOrNull() ?: return false
    if (review.text("kind") != item.text("kind") || purchase.text("currency") != "RWF") return false
    fun near(a: Double, b: Double) = abs(a-b) <= .02
    if (item.text("kind") == "equity") return purchase.text("broker").uppercase().replace(Regex("[^A-Z0-9]"), "") == "BKCAPITAL" &&
        purchase.text("contractNote") == item.text("contractNote") && purchase.text("ticker") == item.text("ticker") &&
        purchase.text("tradeDate") == item.text("date") && purchase.optDouble("shares") == item.optDouble("shares") &&
        near(purchase.optDouble("pricePerShare"), item.optDouble("pricePerShare")) && near(purchase.optDouble("netAmountPayable"), item.optDouble("amount"))
    val posting = calendarDate(item.text("date")) ?: return false
    val settlement = calendarDate(purchase.text("settlementDate")) ?: return false
    return posting.toEpochDays() - settlement.toEpochDays() in 0..7 &&
        near(purchase.optDouble("faceValue") * purchase.optDouble("pricePercent") / 100 + purchase.optDouble("accruedInterestPaid", 0.0), item.optDouble("amount"))
}

@Composable
fun MissingStatementTile(item: JsonRecord, open: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
        ListItem(headlineContent = { AppText(item.text("title"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { FinancialText("${if (item.text("state") == "review-required") "Review required" else "Missing PDF"} · ${item.text("date")} · ${money(item.optDouble("amount"), "RWF")}") },
            leadingContent = { AppIcon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = { AppIcon(Icons.Outlined.UploadFile, "Upload missing confirmation") },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.clickable(onClick = open))
    }
}

@Composable
fun StatementReviewScreen(model: PortfolioModel, review: JsonRecord, open: (JsonRecord) -> Unit) {
    var rows by remember(review) { mutableStateOf<List<JsonRecord>?>(null) }
    var attempted by remember(review) { mutableStateOf(false) }
    LaunchedEffect(review, model.busy) {
        if (!attempted && !model.busy) {
            attempted = true
            model.checkStatement(review) { rows = it }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppText("BK Capital statement", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            AppText("${review.text("fromDate")} – ${review.text("toDate")}", style = MaterialTheme.typography.bodySmall) }
        if (rows == null) item {
            if (model.busy) LoadingState() else Button(onClick = { attempted = false }) { AppText("Check again") }
        } else {
            item { FinancialText("${rows!!.count { it.text("state") == "recorded" }} recorded · ${rows!!.count { it.text("state") in listOf("missing", "review-required") }} missing PDFs", style = MaterialTheme.typography.labelLarge) }
            items(rows!!.withIndex().toList(), key = { it.index }) { (_, row) ->
                if (row.text("state") == "missing") MissingStatementTile(row) { if (row.text("state") == "review-required") model.showStatementRecordReview(row) else open(row) }
                else ListItem(headlineContent = { AppText(row.text("title")) }, supportingContent = { AppText("Recorded · ${row.text("date")}") },
                    leadingContent = { AppIcon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) })
            }
            if (rows!!.any { it.text("state") == "missing" }) item {
                AppText("Missing PDFs stay in your portfolio as reminders. Amounts are added to totals after confirmation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MissingDocumentsScreen(model: PortfolioModel, open: (JsonRecord) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (model.statementItems.isEmpty()) item { EmptyState("No missing documents", EmptyArtwork.DOCUMENTS, compact = true) }
        items(model.statementItems, key = { it.text("id") }) { row -> MissingStatementTile(row) { if (row.text("state") == "review-required") model.showStatementRecordReview(row) else open(row) } }
    }
}



@Composable
internal fun StatementReconciliationDialog(model: PortfolioModel, upload: (JsonRecord) -> Unit) {
    val rows = model.statementReviewRows ?: return
    var deciding by remember { mutableStateOf<Pair<JsonRecord, String?>?>(null) }
    var reason by remember { mutableStateOf("") }
    var addingBond by remember { mutableStateOf<JsonRecord?>(null) }
    addingBond?.let { row ->
        StatementBondDialog(model, row, dismiss = { addingBond = null })
        return
    }
    deciding?.let { (row, purchaseId) ->
        AlertDialog(onDismissRequest = { if (!model.busy) deciding = null }, title = { AppText(if (purchaseId == null) "Clear manual decision?" else "Confirm this match?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText("Check the source PDF and investment. This links evidence only; it never adds a holding or changes purchase amounts.")
                OutlinedTextField(reason, { reason = it.take(500) }, label = { AppText("Reason for this decision") })
            } }, confirmButton = { TextButton(enabled = !model.busy && reason.trim().length >= 3, onClick = {
                model.resolveStatementRecord(row, purchaseId, reason) { deciding = null; reason = "" }
            }) { AppText("Confirm") } }, dismissButton = { TextButton(onClick = { deciding = null }) { AppText("Cancel") } })
        return
    }
    AlertDialog(onDismissRequest = { if (!model.busy) model.closeStatementReview() }, title = { AppText("Statement reconciliation") },
        text = { LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (rows.isEmpty()) item { AppText("No reconciliation records are linked to this PDF yet.") }
            items(rows, key = { it.text("id") }) { row ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    AppText(row.text("title"), fontWeight = FontWeight.Bold)
                    FinancialText("${row.text("date")} · ${money(row.optDouble("amount"))}")
                    AppText(when (statementReconciliationStatus(row)) {
                        "matched" -> if (row.text("matchMethod") == "manual") "Matched · confirmed by you" else "Matched · reference and figures agree"
                        "review-required" -> "Review required · verify the source before linking"
                        "unmatched" -> "Unmatched · confirmation needed"
                        else -> "Evidence only · no holding or received income created"
                    }, style = MaterialTheme.typography.bodySmall)
                    if (row.rows("sources").isEmpty()) AppText("No source PDF is linked to this record.", style = MaterialTheme.typography.bodySmall)
                    row.rows("sources").distinctBy { it.text("documentId") }.forEach { source ->
                        TextButton(enabled = !model.busy, onClick = {
                            val id = source.text("documentId")
                            model.closeStatementReview()
                            model.openDocumentPreview(JsonRecord().put("id", id).put("label", source.text("label")).put("downloadUrl", "/api/bonds/documents/$id"))
                        }) { AppText("View source PDF") }
                    }
                    row.rows("candidates").forEach { purchase ->
                        FinancialText("${purchase.text("isin")} · ${money(if (row.text("kind") == "equity") purchase.optDouble("netAmountPayable") else purchase.optDouble("faceValue"))}", style = MaterialTheme.typography.bodySmall)
                        val proof = purchase.text(if (row.text("kind") == "equity") "contractDocumentId" else "confirmationDocumentId")
                        if (proof.isNotBlank()) TextButton(enabled = !model.busy, onClick = {
                            model.closeStatementReview()
                            model.openDocumentPreview(JsonRecord().put("id", proof).put("label", "Purchase confirmation").put("downloadUrl", "/api/bonds/documents/$proof"))
                        }) { AppText("View purchase proof") }
                        TextButton(enabled = !model.busy && (!purchase.has("canLink") || purchase.optBoolean("canLink")), onClick = { reason = ""; deciding = row to purchase.text("id") }) {
                            FinancialText("Link ${purchase.text("bondName").ifBlank { purchase.text("securityName").ifBlank { purchase.text("ticker") } }} · ${purchase.text("settlementDate").ifBlank { purchase.text("tradeDate") }}")
                        }
                    }
                    if (statementReconciliationStatus(row) == "unmatched" && row.rows("bondOptions").isNotEmpty()) {
                        TextButton(enabled = !model.busy, onClick = { addingBond = row }) { AppText("Add bond from saved terms") }
                    }
                    if (row.text("matchMethod") == "manual" || row.rows("decisions").lastOrNull()?.text("action") == "link") TextButton(enabled = !model.busy, onClick = { reason = ""; deciding = row to null }) { AppText("Correct / clear this link") }
                    if (row.text("kind") in listOf("bond", "equity") && statementReconciliationStatus(row) != "matched") TextButton(enabled = !model.busy, onClick = {
                        model.closeStatementReview(); upload(row)
                    }) { AppText("Upload purchase confirmation") }
                    if (row.rows("decisions").isNotEmpty()) AppText("Decision history", style = MaterialTheme.typography.labelMedium)
                    row.rows("decisions").forEach { decision ->
                        if (decision.text("proofDocumentId").isNotBlank()) TextButton(enabled = !model.busy, onClick = {
                            val id = decision.text("proofDocumentId")
                            model.closeStatementReview()
                            model.openDocumentPreview(JsonRecord().put("id", id).put("label", "Purchase confirmation").put("downloadUrl", "/api/bonds/documents/$id"))
                        }) { AppText("View purchase proof") }
                        AppText("${decision.text("at").take(19)} · ${if (decision.text("action") == "link") "Linked" else "Cleared"} · ${decision.text("reason")}", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        } }, confirmButton = { TextButton(onClick = model::closeStatementReview) { AppText("Close") } })
}

@Composable
private fun StatementBondDialog(model: PortfolioModel, row: JsonRecord, dismiss: () -> Unit) {
    var selected by remember(row.text("id")) { mutableStateOf<JsonRecord?>(null) }
    var principal by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fees by remember { mutableStateOf("") }
    var accrued by remember { mutableStateOf("0") }
    var tax by remember { mutableStateOf("5") }
    var confirmed by remember { mutableStateOf(false) }
    val figures = statementBondFigures(principal, price, fees, accrued, tax)
    AlertDialog(onDismissRequest = { if (!model.busy) dismiss() }, title = { AppText("Add bond from saved terms") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppText("Choose the bond you purchased. Its coupon and payment dates come from saved public terms. Verify your own principal and execution figures against the statement.")
            Fact("Statement bond cost", money(row.optDouble("amount"), "RWF"))
            row.rows("bondOptions").forEach { instrument ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !model.busy) {
                    selected = instrument; price = instrument.text("pricePercent"); confirmed = false
                }) {
                    RadioButton(selected?.text("id") == instrument.text("id"), null)
                    Column {
                        FinancialText(instrument.text("bondName"), fontWeight = FontWeight.SemiBold)
                        FinancialText("${instrument.text("isin")} · ${percent(instrument.optDouble("couponRate"))}", style = MaterialTheme.typography.bodySmall)
                        FinancialText("${instrument.text("settlementDate")} – ${instrument.text("maturityDate")}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            selected?.let { instrument ->
                Fact("Settlement", instrument.text("settlementDate"))
                AppText("Purchase is recorded at settlement; the statement posting date remains attached as evidence.", style = MaterialTheme.typography.bodySmall)
                Fact("First coupon", instrument.text("firstCouponDate"))
                Fact("Coupon payment dates", (instrument.optJSONArray("couponDates")?.length() ?: 0).toString())
                @Composable fun number(label: String, value: String, update: (String) -> Unit) {
                    OutlinedTextField(value, { update(it); confirmed = false }, label = { AppText(label) }, enabled = !model.busy,
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                }
                number("Principal purchased (RWF)", principal) { principal = it }
                number("Executed price (%)", price) { price = it }
                number("Separately charged fees (RWF)", fees) { fees = it }
                number("Accrued interest paid (RWF)", accrued) { accrued = it }
                number("Withholding tax (%)", tax) { tax = it }
                AppText("Fees are additional to the statement bond cost. Enter 0 only when no fees were charged.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(confirmed, { confirmed = it }, enabled = !model.busy)
                    AppText("I verified the bond and my purchase figures.", style = MaterialTheme.typography.bodySmall)
                }
                model.error?.let { AppText(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        } }, confirmButton = { TextButton(enabled = !model.busy && selected != null && figures != null && confirmed, onClick = {
            model.createStatementBond(row, selected!!, figures!!, dismiss)
        }) { AppText("Save purchase") } }, dismissButton = { TextButton(enabled = !model.busy, onClick = dismiss) { AppText("Cancel") } })
}

/** Missing/invalid inputs never become zero or inferred principal. Server checks the actual statement cost. */
internal fun statementBondFigures(principal: String, price: String, fees: String, accrued: String, tax: String): JsonRecord? {
    val values = listOf(principal, price, fees, accrued, tax).mapIndexed { index, raw ->
        val decimals = if (index in listOf(0, 2, 3)) 2 else 6
        if (!raw.trim().matches(Regex("\\d+(?:\\.\\d{1,$decimals})?"))) return null
        raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1e13 } ?: return null
    }
    if (values[0] <= 0 || values[1] <= 0 || values[1] > 1000 || values[4] > 100) return null
    return JsonRecord().put("faceValue", values[0]).put("pricePercent", values[1]).put("feesPaid", values[2])
        .put("accruedInterestPaid", values[3]).put("withholdingTaxRate", values[4] / 100)
}

internal fun statementReconciliationStatus(row: JsonRecord): String = row.text("reconciliationStatus").ifBlank {
    when(row.text("state")) { "recorded" -> "matched"; "missing" -> "unmatched"; "evidence" -> "evidence-only"; else -> "review-required" }
}

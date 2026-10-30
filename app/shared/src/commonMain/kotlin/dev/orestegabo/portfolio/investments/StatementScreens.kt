package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
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

internal fun statementReconciliationStatus(row: JsonRecord): String = row.text("reconciliationStatus").ifBlank {
    when(row.text("state")) { "recorded" -> "matched"; "missing" -> "unmatched"; "evidence" -> "evidence-only"; else -> "review-required" }
}

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
            supportingContent = { FinancialText("Missing PDF · ${item.text("date")} · ${money(item.optDouble("amount"), "RWF")}") },
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
            item { FinancialText("${rows!!.count { it.text("state") == "recorded" }} recorded · ${rows!!.count { it.text("state") == "missing" }} missing PDFs", style = MaterialTheme.typography.labelLarge) }
            items(rows!!.withIndex().toList(), key = { it.index }) { (_, row) ->
                if (row.text("state") == "missing") MissingStatementTile(row) { open(row) }
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
        items(model.statementItems, key = { it.text("id") }) { row -> MissingStatementTile(row) { open(row) } }
    }
}


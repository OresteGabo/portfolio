package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class InvestmentActivity(val date: String, val title: String, val detail: String)
internal fun investmentActivities(rows: List<JsonRecord>, documents: List<JsonRecord>, receipts: List<JsonRecord>, ledger: List<JsonRecord>): List<InvestmentActivity> {
    val ids = rows.map { it.text("id") }.toSet()
    val linked = rows.flatMap { investmentEvidence(it, documents).map { evidence -> evidence.document } }.distinctBy { it.text("id") }
    return (rows.map { InvestmentActivity(it.text("purchaseDate").ifBlank { it.text("tradeDate") }, "Purchased", it.text("bondName").ifBlank { it.text("securityName").ifBlank { it.text("ticker") } }) } +
        linked.map { InvestmentActivity(it.text("createdAt").take(10).ifBlank { it.text("documentDate") }, "Document added", it.text("label").ifBlank { it.text("fileName") }) } +
        receipts.filter { it.text("purchaseId") in ids }.map { InvestmentActivity(it.text("dueDate"), when {
            !it.optBoolean("received") -> "Coupon marked not received"
            it.text("method") == "automatic" -> "Coupon marked automatically"
            else -> "Coupon confirmed received"
        }, money(it.optDouble("amount", 0.0), it.text("currency").ifBlank { rows.firstOrNull { row -> row.text("id") == it.text("purchaseId") }?.text("currency") ?: "RWF" })) } +
        ledger.filter { it.text("holdingId") in ids }.map { InvestmentActivity(it.text("date"), if (it.text("kind") == "sale") "Share sale" else "Dividend", money(it.optDouble("amount", 0.0), it.text("currency"))) }
    ).sortedWith(compareByDescending<InvestmentActivity> { it.date }.thenBy { it.title })
}

@Composable
internal fun InvestmentActivityTimeline(model: PortfolioModel, rows: List<JsonRecord>) {
    var expanded by remember(rows.map { it.text("id") }) { mutableStateOf(false) }
    val events = remember(rows, model.documents, model.couponReceipts, model.ledgerEntries) { investmentActivities(rows, model.documents, model.couponReceipts, model.ledgerEntries) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailDisclosure("Investment activity", expanded) { expanded = !expanded }
        if (expanded) events.forEach { event ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText("•", color = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText(event.title, style = MaterialTheme.typography.labelLarge)
                    AppText(displayDate(event.date), style = MaterialTheme.typography.labelSmall)
                    FinancialText(event.detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

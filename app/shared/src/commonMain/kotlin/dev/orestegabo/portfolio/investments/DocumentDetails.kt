package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Navigation follows owned persisted evidence, never a guessed company name or document title. */
internal fun documentBonds(document: JsonRecord, purchases: List<JsonRecord>) = purchases.filter { purchase ->
    document.text("id").isNotBlank() && listOf("confirmationDocumentId", "issuanceDocumentId", "statementDocumentId").any {
        purchase.text(it) == document.text("id")
    }
}
internal fun documentShares(document: JsonRecord, holdings: List<JsonRecord>) = holdings.filter { holding ->
    document.text("id").isNotBlank() && listOf("contractDocumentId", "statementDocumentId").any {
        holding.text(it) == document.text("id")
    }
}.distinctBy { it.text("ticker") to it.text("currency") }

@Composable
internal fun DocumentDetailsScreen(model: PortfolioModel, document: JsonRecord,
    openBond: (JsonRecord) -> Unit, openShare: (JsonRecord) -> Unit) {
    val actions = LocalNativeActions.current
    var annualAction by remember { mutableStateOf<String?>(null) }
    val year = document.optInt("annualReportYear")
    val bonds = documentBonds(document, model.purchases)
    val shares = documentShares(document, model.holdings)
    fun preview() { if (year > 0) annualAction = "preview" else model.openDocumentPreview(document) }
    fun share() { if (year > 0) annualAction = "share" else model.action { shareInvestmentDocument(model.api, actions, document) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailNote(document.text("category"))
                FinancialText(document.text("label"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                if (document.text("instrumentName").isNotBlank()) FinancialText(document.text("instrumentName"), style = MaterialTheme.typography.bodyLarge)
                DetailNote(if (document.text("category") == "Bond issuance" || document.text("visibility") == "shared") "Shared · Public issuance" else "Private · Only you")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(enabled = !model.busy, onClick = ::preview) { AppIcon(Icons.Outlined.Description, null); Spacer(Modifier.width(8.dp)); AppText("Open PDF") }
                    OutlinedButton(enabled = !model.busy, onClick = ::share) { AppIcon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); AppText("Share PDF") }
                }
            }
        }
        if (bonds.isNotEmpty() || shares.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailHeading("Related investments")
                bonds.forEach { bond ->
                    OutlinedButton(enabled = !model.busy, onClick = { openBond(bond) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FinancialText(bond.text("bondName"))
                            FinancialText(bond.optDouble("faceValue",Double.NaN).takeIf { it.isFinite() }?.let { money(it,bond.text("currency")) } ?: "—", style = MaterialTheme.typography.bodySmall)
                        }; AppIcon(Icons.Outlined.ChevronRight, null)
                    }
                }
                shares.forEach { holding ->
                    OutlinedButton(enabled = !model.busy, onClick = { openShare(holding) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FinancialText(holding.text("securityName").ifBlank { holding.text("ticker") })
                            FinancialText("${holding.text("ticker")} · ${holding.text("currency")}", style = MaterialTheme.typography.bodySmall)
                        }; AppIcon(Icons.Outlined.ChevronRight, null)
                    }
                }
            }
        }
        if (!model.historyComplete) item {
            TextButton(enabled = !model.busy, onClick = { model.loadInvestmentHistory(force = true) }) { AppText("Load related investments") }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DetailHeading("File information")
                if (document.text("issuer").isNotBlank()) DetailValueRow("Issuer", document.text("issuer"))
                calendarDate(document.text("documentDate"))?.let { DetailValueRow("Document date", detailDate(it)) }
                if (document.text("originalFileName").isNotBlank()) DetailValueRow("File name", document.text("originalFileName"))
                if (document.text("description").isNotBlank()) FinancialText(document.text("description"), style = MaterialTheme.typography.bodyMedium)
            }
        }
        document.optJSONObject("details")?.let { details ->
            if (details.text("summary").isNotBlank()) item { FinancialText(details.text("summary"), style = MaterialTheme.typography.bodyMedium) }
            listOf("facts", "charges", "flow").forEach { key ->
                if (details.rows(key).isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        HorizontalDivider()
                        details.rows(key).forEach { DetailValueRow(it.text("label"), it.text("value")) }
                    }
                }
            }
            details.rows("sections").forEachIndexed { index, section -> item(key = "section:$index") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HorizontalDivider(); DetailHeading(section.text("title"))
                    section.rows("items").forEach { DetailValueRow(it.text("label"), it.text("value")) }
                }
            } }
        }
    }
    annualAction?.let { action -> AlertDialog(onDismissRequest = { annualAction = null },
        title = { AppText("${localized("Annual report")} · $year") },
        text = { AppText("Generate a calendar-year PDF with your financial figures, even when Hide numbers is on. The current year is limited to today. Original documents are not included.") },
        confirmButton = { TextButton(enabled = !model.busy, onClick = {
            annualAction = null
            if (action == "preview") model.openDocumentPreview(document) else model.exportAnnualReport(year, actions)
        }) { AppText("Generate PDF") } }, dismissButton = { TextButton(onClick = { annualAction = null }) { AppText("Cancel") } }) }
    if (model.annualReportYear != null) model.reportProgress?.let { progress ->
        AlertDialog(onDismissRequest = {}, title = { AppText("Annual report") }, text = {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp)); AppText(progress, Modifier.weight(1f))
            }
        }, confirmButton = {})
    }
}

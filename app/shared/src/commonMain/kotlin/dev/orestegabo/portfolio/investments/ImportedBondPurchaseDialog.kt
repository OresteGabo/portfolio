package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ImportedBondPurchaseDialog(model: PortfolioModel, pending: JsonRecord, dismiss: () -> Unit, completed: () -> Unit) {
    var requested by remember { mutableStateOf(false) }
    var selectedId by rememberSaveable(pending.text("id")) { mutableStateOf<String?>(null) }
    LaunchedEffect(model.busy) {
        if (!requested && !model.busy) { requested = true; model.loadInvestmentHistory(force = true) }
    }
    val candidates = if (model.historyComplete) importedBondPurchasesForPending(pending, model.purchases) else emptyList()
    val selected = candidates.singleOrNull { it.text("id") == selectedId }
    AlertDialog(onDismissRequest = { if (!model.busy) dismiss() },
        title = { AppText("Link imported bond confirmation") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText("Choose the personal confirmation for this application. Its PDF, allocation, dates and costs stay unchanged; the pending estimate stops being counted.",
                    style = MaterialTheme.typography.bodyMedium)
                Fact("Amount applied", money(pending.optDouble("faceValue"), pending.text("currency")))
                if (!requested || model.busy) InziraLoadingMark(active = true, modifier = Modifier.size(28.dp))
                else if (candidates.isEmpty()) AppText("No imported confirmation matches this bond and applied amount.",
                    style = MaterialTheme.typography.bodyMedium)
                if (!model.busy) model.error?.let { AppText(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                candidates.forEach { purchase ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                        selected = selectedId == purchase.text("id"), enabled = !model.busy,
                        role = Role.RadioButton, onClick = { selectedId = purchase.text("id") }).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selectedId == purchase.text("id"), null, Modifier.clearAndSetSemantics {})
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FinancialText(purchase.text("bondName"), fontWeight = FontWeight.SemiBold)
                            FinancialText(displayDate(purchase.text("settlementDate")), style = MaterialTheme.typography.bodyMedium)
                            FinancialText("${localized("Allocated")} · ${money(purchase.optDouble("faceValue"), purchase.text("currency"))}", style = MaterialTheme.typography.bodyMedium)
                            FinancialText("${localized("Commission")} · ${money(purchase.optDouble("feesPaid"), purchase.text("currency"))}", style = MaterialTheme.typography.labelMedium)
                            AppText("Confirmation already in Library", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!model.busy && (model.error != null || !model.historyComplete)) TextButton(onClick = {
                    model.error = null; model.loadInvestmentHistory(force = true)
                }) { AppText("Retry") }
            }
        },
        confirmButton = { TextButton(enabled = selected != null && requested && !model.busy, onClick = {
            selected?.let { model.linkExistingBondPurchase(pending, it, completed) }
        }) { AppText(if (model.busy) "Linking…" else "Link purchase") } },
        dismissButton = { TextButton(enabled = !model.busy, onClick = dismiss) { AppText("Cancel") } })
}

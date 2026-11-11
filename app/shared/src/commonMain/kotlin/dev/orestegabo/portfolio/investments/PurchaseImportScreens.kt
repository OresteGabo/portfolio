package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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

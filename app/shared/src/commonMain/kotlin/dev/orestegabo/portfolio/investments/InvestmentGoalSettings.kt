package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

@Composable
fun InvestmentTargetDialog(model: PortfolioModel, dismiss: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf(model.monthlyInvestmentTarget.toString()) }
    val parsed = amount.toLongOrNull()?.takeIf(::validInvestmentTarget)
    val hidden = LocalNumbersHidden.current
    AlertDialog(onDismissRequest = dismiss, title = { Text("Monthly target") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (hidden) {
                Text("Your target is hidden.")
                TextButton(onClick = model::toggleNumberVisibility) { Text("Show numbers to edit") }
            } else {
            OutlinedTextField(amount, { value -> if (value.all { it.isDigit() || it == ',' || it == ' ' }) amount = value.filter(Char::isDigit).take(13) },
                visualTransformation = if (LocalNumbersHidden.current) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                label = { Text("Amount (RWF)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Confirmed purchases, including fees.") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100_000L, 300_000L, 500_000L).forEach { value ->
                    FilterChip(selected = parsed == value, onClick = { amount = value.toString() }, label = { Text("${value / 1000}k") })
                }
            }
            }
            Text("Saved on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(enabled = parsed != null && !model.busy && !hidden, onClick = { model.updateMonthlyInvestmentTarget(parsed!!); dismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun InvestmentGoalBreakdown(model: PortfolioModel) {
    val month = LocalDate(Today.year, Today.month, 1)
    val rows = (0..2).map { offset -> monthlyInvestmentProgress(model.portfolio, shiftCalendarMonth(month, -offset), model.monthlyInvestmentTarget, Today) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rows.first() == null) TextButton(enabled = !model.busy, onClick = { model.loadInvestmentHistory() }) { Text("Check progress") }
        else {
            Text("Recent months", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            rows.filterNotNull().forEach { progress ->
                Row {
                    Text(progress.month.monthLabel(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    FinancialText(money(progress.confirmed, "RWF"), style = MaterialTheme.typography.bodyMedium)
                }
                if (!LocalNumbersHidden.current) LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                else Spacer(Modifier.height(4.dp))
            }
            rows.first()?.let { current ->
                FinancialText(if (current.achieved) "This month’s target reached" else "${money(current.remaining, "RWF")} to your target",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

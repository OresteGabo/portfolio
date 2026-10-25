package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
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
    // Keep financial editing in the protected main scene: iOS platform dialogs use a sibling scene.
    PlatformBackHandler(enabled = true, back = dismiss)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .45f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss)
        .imePadding().padding(24.dp), contentAlignment = Alignment.Center) {
    Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth()
        .semantics { paneTitle = localized("Monthly target") }.pointerInput(Unit) { detectTapGestures {} },
        shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppText("Monthly target", style = MaterialTheme.typography.headlineSmall)
            if (hidden) {
                AppText("Your target is hidden.")
                TextButton(onClick = model::toggleNumberVisibility) { AppText("Show numbers to edit") }
            } else {
            Field("Amount (RWF)", amount, { value ->
                if (value.all { it.isDigit() || it == ',' || it == ' ' }) amount = value.filter(Char::isDigit).take(13)
            }, keyboard = KeyboardType.Number, helper = "Confirmed purchases, including fees.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100_000L, 300_000L, 500_000L).forEach { value ->
                    FilterChip(selected = parsed == value, onClick = { amount = value.toString() }, label = { FinancialText("${value / 1000}k") })
                }
            }
            }
            AppText("Saved on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = dismiss) { AppText("Cancel") }
                TextButton(enabled = parsed != null && !model.busy && !hidden,
                    onClick = { model.updateMonthlyInvestmentTarget(parsed!!); dismiss() }) { AppText("Save") }
            }
        }
    }
    }
}

@Composable
fun InvestmentGoalBreakdown(model: PortfolioModel) {
    val month = LocalDate(Today.year, Today.month, 1)
    val rows = (0..2).map { offset -> model.monthlyTargetProgress(shiftCalendarMonth(month, -offset)) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rows.first() == null) {
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            else AppText("Progress unavailable", style = MaterialTheme.typography.bodySmall)
        }
        else {
            AppText("Recent months", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            rows.filterNotNull().forEach { progress ->
                Row {
                    AppText(progress.month.monthLabel(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    FinancialText(money(progress.confirmed, "RWF"), style = MaterialTheme.typography.bodyMedium)
                }
                if (!LocalNumbersHidden.current) CaptureRedactionRegion(Modifier.fillMaxWidth(), masked = { Spacer(Modifier.fillMaxSize()) }) {
                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                }
                else Spacer(Modifier.height(4.dp))
            }
            rows.first()?.let { current ->
                FinancialText(if (current.achieved) "This month’s target reached" else "${money(current.remaining, "RWF")} to your target",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

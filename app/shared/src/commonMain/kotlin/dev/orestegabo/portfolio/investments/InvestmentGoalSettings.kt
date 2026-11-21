package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
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
    var monthly by rememberSaveable { mutableStateOf(model.investmentGoal.monthlyAmount.toString()) }
    var yearly by rememberSaveable { mutableStateOf(model.investmentGoal.yearlyAmount.toString()) }
    var periodKey by rememberSaveable { mutableStateOf(model.investmentGoal.period.key) }
    val period = InvestmentGoalPeriod.entries.first { it.key == periodKey }
    val amount = if (period == InvestmentGoalPeriod.YEARLY) yearly else monthly
    val parsed = amount.toLongOrNull()?.takeIf(::validInvestmentTarget)
    val hidden = LocalNumbersHidden.current
    // Keep financial editing in the protected main scene: iOS platform dialogs use a sibling scene.
    PlatformBackHandler(enabled = true, back = dismiss)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .45f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss)
        .imePadding().padding(24.dp), contentAlignment = Alignment.Center) {
    Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth()
        .semantics { paneTitle = localized("Investment goal") }.pointerInput(Unit) { detectTapGestures {} },
        shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppText("Investment goal", style = MaterialTheme.typography.headlineSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                InvestmentGoalPeriod.entries.forEachIndexed { index, choice ->
                    SegmentedButton(selected = choice == period, enabled = !hidden,
                        onClick = { periodKey = choice.key }, shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                        AppText(if (choice == InvestmentGoalPeriod.YEARLY) "Yearly" else "Monthly")
                    }
                }
            }
            AppText(if (period == InvestmentGoalPeriod.YEARLY) "January–December" else "Each calendar month",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hidden) {
                AppText("Your target is hidden.")
                TextButton(onClick = model::toggleNumberVisibility) { AppText("Show numbers to edit") }
            } else {
            Field("Amount (RWF)", amount, { value ->
                if (value.all { it.isDigit() || it == ',' || it == ' ' }) {
                    val digits = value.filter(Char::isDigit).take(13)
                    if (period == InvestmentGoalPeriod.YEARLY) yearly = digits else monthly = digits
                }
            }, keyboard = KeyboardType.Number, helper = "Confirmed and pending purchases, including fees.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = if (period == InvestmentGoalPeriod.YEARLY) listOf(1_200_000L, 3_600_000L, 6_000_000L) else listOf(100_000L, 300_000L, 500_000L)
                presets.forEach { value ->
                    FilterChip(selected = parsed == value, onClick = {
                        if (period == InvestmentGoalPeriod.YEARLY) yearly = value.toString() else monthly = value.toString()
                    }, label = { FinancialText(if (period == InvestmentGoalPeriod.YEARLY) "${value / 1000000.0}m" else "${value / 1000}k") })
                }
            }
            }
            AppText(if (model.isGuest) "Saved on this device." else "Saved for this account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = dismiss) { AppText("Cancel") }
                TextButton(enabled = parsed != null && !model.busy && !hidden,
                    onClick = { requireNotNull(parsed)
                        model.updateInvestmentGoal(model.investmentGoal.copy(
                            monthlyAmount = monthly.toLongOrNull()?.takeIf(::validInvestmentTarget) ?: model.investmentGoal.monthlyAmount,
                            yearlyAmount = yearly.toLongOrNull()?.takeIf(::validInvestmentTarget) ?: model.investmentGoal.yearlyAmount, period = period)); dismiss() }) { AppText("Save") }
            }
        }
    }
    }
}

@Composable
fun InvestmentGoalBreakdown(model: PortfolioModel) {
    val month = LocalDate(Today.year, Today.month, 1)
    val yearly = model.investmentGoal.period == InvestmentGoalPeriod.YEARLY
    val rows = if (yearly) (1..Today.month.ordinal + 1).map { m -> model.monthlyTargetProgress(LocalDate(Today.year, m, 1)) }
        else (0..2).map { offset -> model.monthlyTargetProgress(shiftCalendarMonth(month, -offset)) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rows.first() == null) {
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            else AppText("Progress unavailable", style = MaterialTheme.typography.bodySmall)
        }
        else {
            AppText(if (yearly) "Monthly contributions" else "Recent months", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            rows.filterNotNull().forEach { progress ->
                Row {
                    AppText(progress.month.monthLabel(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    FinancialText(money(progress.invested, "RWF"), style = MaterialTheme.typography.bodyMedium)
                }
                if (!yearly && !LocalNumbersHidden.current) CaptureRedactionRegion(Modifier.fillMaxWidth(), masked = { Spacer(Modifier.fillMaxSize()) }) {
                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                }
                else Spacer(Modifier.height(4.dp))
            }
            model.investmentGoalProgress()?.let { current ->
                FinancialText(if (current.achieved) if (yearly) "This year’s goal reached" else "This month’s target reached" else "${money(current.remaining, "RWF")} to your target",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Pending contributions are a lighter portion of the same goal, not confirmed purchases. */
@Composable
internal fun InvestmentGoalProgressBar(progress: MonthlyInvestmentProgress) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(8.dp)
        .clip(MaterialTheme.shapes.small).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.fraction, 0f..1f) }) {
        drawRect(track)
        drawRect(primary.copy(alpha = .35f), size = androidx.compose.ui.geometry.Size(size.width * progress.fraction, size.height))
        val confirmed = (progress.confirmed / progress.target).coerceIn(0.0, 1.0).toFloat()
        drawRect(primary, size = androidx.compose.ui.geometry.Size(size.width * confirmed, size.height))
    }
}

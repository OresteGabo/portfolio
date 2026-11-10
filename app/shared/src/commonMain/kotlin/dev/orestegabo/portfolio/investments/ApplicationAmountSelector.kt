package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

internal const val ApplicationAmountStep = 100_000L
internal const val MaximumApplicationAmount = 10_000_000_000_000L

internal fun steppedApplicationAmount(value: String, increase: Boolean): String {
    val current = value.toLongOrNull()?.coerceIn(0, MaximumApplicationAmount) ?: ApplicationAmountStep
    return (current + if (increase) ApplicationAmountStep else -ApplicationAmountStep)
        .coerceIn(ApplicationAmountStep, MaximumApplicationAmount).toString()
}

@Composable
internal fun ApplicationAmountSelector(label: String, value: String, change: (String) -> Unit, enabled: Boolean) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .15f))) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppText(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText("RWF", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                FinancialText(formatNumber(value.toDoubleOrNull() ?: ApplicationAmountStep.toDouble()),
                    Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 36.sp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = .12f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled = enabled && (value.toLongOrNull() ?: ApplicationAmountStep) > ApplicationAmountStep,
                    onClick = { change(steppedApplicationAmount(value, false)) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    AppIcon(Icons.Outlined.Remove, "Decrease amount", Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); AppText(formatNumber(ApplicationAmountStep.toDouble()), maxLines = 1)
                }
                FilledTonalButton(enabled = enabled && (value.toLongOrNull() ?: ApplicationAmountStep) < MaximumApplicationAmount,
                    onClick = { change(steppedApplicationAmount(value, true)) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    AppIcon(Icons.Outlined.Add, "Increase amount", Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); AppText(formatNumber(ApplicationAmountStep.toDouble()), maxLines = 1)
                }
            }
        }
    }
}

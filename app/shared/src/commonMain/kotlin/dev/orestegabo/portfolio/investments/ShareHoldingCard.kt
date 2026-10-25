package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ShareHoldingCard(valuation: ValuedSharePosition, name: String, onClick: () -> Unit) {
    val position = valuation.position
    val gain = valuation.gainLoss
    val rate = valuation.gainLossRate
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val returnColor = when {
        gain == null || gain == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
        gain > 0 -> if (dark) Color(0xFF81D8A7) else Color(0xFF147D46)
        else -> MaterialTheme.colorScheme.error
    }
    val trend = when {
        gain == null || gain == 0.0 -> Icons.Outlined.Remove
        gain > 0 -> Icons.AutoMirrored.Outlined.TrendingUp
        else -> Icons.AutoMirrored.Outlined.TrendingDown
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InvestmentLogo(ticker = position.ticker)
                Column(Modifier.weight(1f)) {
                    AppText(position.ticker, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    AppText(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = returnColor.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppIcon(trend, null, Modifier.size(16.dp), tint = returnColor)
                        FinancialText(rate?.let { (if (it > 0) "+" else "") + percent(it) }
                            ?: if (gain == null) "Unpriced" else "—",
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = returnColor)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText(if (valuation.closingPrice == null) "Purchase cost" else "Market value",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FinancialText(money(valuation.estimatedValue, position.currency),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppText("Since purchase", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val signedGain = gain?.let {
                        position.currency + " " + (if (it > 0) "+" else "") + money(it, position.currency).removePrefix("${position.currency} ")
                    } ?: "—"
                    FinancialText(signedGain, color = returnColor, textAlign = TextAlign.End,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            FinancialText("${formatNumber(position.shares)} shares · Total cost ${money(position.cashCost, position.currency)}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

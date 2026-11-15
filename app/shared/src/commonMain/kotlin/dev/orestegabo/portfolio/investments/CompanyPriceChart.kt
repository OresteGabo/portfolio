package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun CompanyPriceChart(
    symbol: String,
    name: String,
    quote: JsonRecord?,
    quotes: JsonRecord?,
    history: JsonRecord?,
    failed: Boolean,
    loading: Boolean,
    period: ChartPeriod = ChartPeriod.ALL,
    onPeriod: (ChartPeriod) -> Unit,
    onRetry: () -> Unit,
) {
    val observations = stockPriceHistory(history, quotes, symbol, period, Today)
    Panel(Modifier.smoothContentSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            InvestmentLogo(ticker = symbol)
            Column(Modifier.weight(1f)) {
                AppText(symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                AppText(name, style = MaterialTheme.typography.bodySmall)
            }
            if (observations.isEmpty()) FinancialText(quote?.let { money(it.optDouble("closing")) } ?: "—",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        ChoiceRow(ChartPeriod.entries, period, onPeriod)
        Box(Modifier.fillMaxWidth().heightIn(min = 360.dp).clip(MaterialTheme.shapes.medium)) {
            // Decorative identity stays with each pager page, including loading and empty states.
            val logo = investmentLogoResource(symbol)
            if (logo != null) {
                Image(painterResource(logo), contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth(.72f).height(200.dp),
                    contentScale = ContentScale.Fit, alpha = .08f)
            } else {
                Text(symbol, modifier = Modifier.align(Alignment.Center),
                    fontSize = 64.sp, fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppLoadingLine(loading, reserveSpace = true)
                if (observations.isNotEmpty()) AppFadeIn("$symbol/${period.name.lowercase()}") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InvestmentChart(observations.map { it.price }, observations.map { it.date.toString() },
                            notes = observations.map { it.note },
                            dateRange = if (period == ChartPeriod.YEAR) yearlyPriceChartRange(Today) else null
                        )
                        AppText(if (observations.any { it.contract }) "Closes & trades · known dates only"
                        else "Exchange closes · known dates only",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (history != null && !history.optBoolean("tradeHistoryAvailable")) AppText("Contract prices unavailable",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (history != null) {
                    Box(Modifier.fillMaxWidth().height(276.dp), contentAlignment = Alignment.Center) {
                        EmptyState("No price history", EmptyArtwork.CHARTS, compact = true, framed = false)
                    }
                }
                AppReveal(failed) { TextButton(onClick = onRetry) { AppText("Retry price history") } }
            }
        }
    }
}

package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.orestegabo.portfolio.design.StructColors

/** A dated closing-price event; it never substitutes the current quote for the saved event values. */
@Composable
fun PriceNotificationCard(item: JsonRecord, onRead: () -> Unit) {
    var expanded by rememberSaveable(item.text("id")) { mutableStateOf(false) }
    val up=item.optDouble("impact")>=0
    val color =
        if (up) StructColors.Financial.notificationGain else MaterialTheme.colorScheme.error
    Card(onClick={ expanded=!expanded;if(expanded)onRead() },
        colors=CardDefaults.cardColors(containerColor=if(item.optBoolean("read")) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer),
        elevation=CardDefaults.cardElevation(defaultElevation=1.dp,pressedElevation=1.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                InvestmentLogo(ticker=item.text("ticker"))
                Column(Modifier.weight(1f)) {
                    AppText(item.text("ticker"),fontWeight=FontWeight.Bold)
                    AppText("Closing price change",style=MaterialTheme.typography.labelSmall)
                }
                FinancialText((if(up) "+" else "")+"${kotlin.math.round(item.optDouble("percent")*100)/100}%",color=color,fontWeight=FontWeight.Bold)
            }
            AppText(item.text("date").takeIf { it.isNotBlank() }?.let {
                localized("Market close · {0}").replace("{0}",displayDate(it))
            } ?: localized("Market date unavailable"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            FinancialText("${money(item.optDouble("previousPrice"))} → ${money(item.optDouble("closingPrice"))}",style=MaterialTheme.typography.titleMedium)
            if(item.text("previousDate").isNotBlank()) {
                AppText(localized("Compared with {0}").replace("{0}",displayDate(item.text("previousDate"))),style=MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                AppText("Holding value change",modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                FinancialText((if(up) "+" else "")+money(item.optDouble("impact")),color=color,fontWeight=FontWeight.Bold)
            }
            if(expanded) {
                HorizontalDivider()
                AppText("Historical close; the latest market price may differ.",style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    AppText("Estimated total gain/loss",modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    FinancialText(
                        money(item.optDouble("totalGain")),
                        color = if (item.optDouble("totalGain") >= 0) StructColors.Financial.notificationGain else MaterialTheme.colorScheme.error
                    )
                }
                AppText("Based on your holdings at this market date. Unrealised, before selling costs.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Owner account totals come from the server; no private holdings or formulas are needed by this row. */
@Composable
internal fun OwnerAccountRow(account: JsonRecord, busy: Boolean, changePublisher: (JsonRecord) -> Unit) {
    var expanded by rememberSaveable(account.text("id")) { mutableStateOf(false) }
    val valuation = account.optJSONObject("portfolio")?.optJSONObject("valuation")
    val currencies = valuation?.rows("currencies")?.ifEmpty {
        listOf(JsonRecord().put("currency", "RWF").put("estimatedNetWorth", 0).put("bondValue", 0).put("shareValue", 0))
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
            onClickLabel = localized(if (expanded) "Collapse account" else "Expand account"), onClick = { expanded = !expanded })
            .semantics { stateDescription = localized(if (expanded) "Expanded" else "Collapsed") }
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(account.text("email"), fontWeight = FontWeight.SemiBold)
                AppText("Estimated net worth", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (currencies == null) AppText("Unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else currencies.forEach { value ->
                    FinancialText("≈ ${money(value.optDouble("estimatedNetWorth"), value.text("currency"))}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            AppIcon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        if (expanded) {
            currencies?.forEach { value ->
                Fact("Bond principal", money(value.optDouble("bondValue"), value.text("currency")))
                Fact("Shares value", money(value.optDouble("shareValue"), value.text("currency")))
                if (value.optInt("unpricedShares") > 0) AppText("Unpriced shares use recorded cost.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            valuation?.text("priceDate")?.takeIf { it.isNotBlank() }?.let { date ->
                AppText("${localized(if (valuation.optBoolean("stale")) "Cached closing prices" else "Closing prices")} · ${displayDate(date)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppText("Includes pending investments.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Fact("Account", "${account.text("kind")} · ${account.text("status")}")
            account.optJSONObject("portfolio")?.let { Fact("Bond purchases", it.text("purchaseCount")) }
            if (account.has("canPublishPublicData")) {
                AppText(if (account.optBoolean("canPublishPublicData")) "Can publish shared data" else "Personal account only", style = MaterialTheme.typography.bodySmall)
                if (account.text("id") != "owner" && account.text("status") == "active") TextButton(
                    enabled = !busy, onClick = { changePublisher(account) }) {
                    AppText(if (account.optBoolean("canPublishPublicData")) "Remove publication permission" else "Allow shared-data publication")
                }
            }
        }
        HorizontalDivider()
    }
}

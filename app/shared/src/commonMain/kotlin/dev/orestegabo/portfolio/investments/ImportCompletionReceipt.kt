package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import kotlinx.datetime.toLocalDateTime
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Identity comes from actual saved records. A broker never supplies a share issuer's logo. */
@Composable
internal fun ImportCompletionReceipt(entry: ImportReceipt, document: JsonRecord?, investment: JsonRecord?, addedTime: ImportAddedTime? = null) {
    val colours = MaterialTheme.colorScheme
    val type = when (entry.kind) {
        "equity" -> "Share contract"
        "bond" -> "Bond confirmation"
        "bond-issuance" -> "Issuance report"
        "statement" -> "Account statement"
        else -> "PDF document"
    }
    val identity = JsonRecord(document?.toString() ?: "{}").put("category", if (entry.kind == "bond-issuance") "Bond issuance" else type)
    // Holdings can supply an explicit symbol when the document metadata only has the security name.
    listOf("ticker", "securityName").forEach { field ->
        if (identity.text(field).isBlank() && investment?.text(field)?.isNotBlank() == true) identity.put(field, investment.text(field))
    }
    val logo = documentInvestmentLogo(identity)
    val title = document?.text("instrumentName")?.takeIf(String::isNotBlank)
        ?: investment?.text(if (entry.kind == "equity") "securityName" else "bondName")?.takeIf(String::isNotBlank)
        ?: logo?.name ?: document?.text("label")?.takeIf(String::isNotBlank) ?: entry.fileName
    val issuer = document?.text("issuer").orEmpty()
    val date = document?.text("documentDate")?.takeIf(String::isNotBlank)
        ?: investment?.text(if (entry.kind == "equity") "tradeDate" else "purchaseDate").orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = colours.primary)
            AppText(if (entry.state == ImportState.EXISTS) "Already in Library" else "Saved to Library", style = MaterialTheme.typography.labelMedium, color = colours.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(width = 60.dp, height = 72.dp)) {
                PdfCover(colours.primary)
                if (logo != null) Surface(Modifier.align(Alignment.BottomEnd).size(32.dp)
                    .semantics { contentDescription = localized("Logo: ${logo.name}") },
                    shape = CircleShape, color = colours.surface, border = BorderStroke(2.dp, colours.surface)) {
                    InvestmentLogo(logo.ticker, logo.bond, Modifier.fillMaxSize().padding(2.dp), compact = true)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(type, style = MaterialTheme.typography.labelMedium, color = colours.primary)
                FinancialText(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (issuer.isNotBlank() && issuer != title) FinancialText(issuer, style = MaterialTheme.typography.bodySmall,
                    color = colours.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (date.isNotBlank()) AppText(date, style = MaterialTheme.typography.labelSmall, color = colours.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = colours.outlineVariant.copy(alpha = .3f))
        AppText(if (entry.state == ImportState.EXISTS) when (entry.outcome) {
            ImportOutcome.PURCHASE -> "This PDF and its purchase details are already saved. No further action is needed."
            ImportOutcome.ISSUANCE -> "This PDF and its bond terms are already saved. No further action is needed."
            ImportOutcome.STATEMENT -> "This PDF is already saved. Review its reconciliation results for any records that need attention."
            null -> "This PDF is already in Library. No additional copy is needed."
        } else when (entry.outcome) {
            ImportOutcome.PURCHASE -> "Your purchase is confirmed."
            ImportOutcome.ISSUANCE -> "Bond terms saved. No personal allocation was assumed."
            ImportOutcome.STATEMENT -> "Statement records checked."
            null -> "Your PDF is available in Library."
        }, style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
        if (entry.state == ImportState.EXISTS && addedTime != null) {
            val date = runCatching {
                kotlin.time.Instant.parse(addedTime.at).toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
            }.getOrNull()
            if (date != null) AppText("${localized(if (addedTime.onThisDevice) "First saved on this device" else "First added")} · " +
                "${date.date} · ${date.hour.toString().padStart(2, '0')}:${date.minute.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.labelSmall, color = colours.onSurfaceVariant)
        }
    }
}

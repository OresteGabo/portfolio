package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.orestegabo.portfolio.design.InziraSpacing

/** One dated purchase, with independent purchase and document actions and no enclosing card. */
@Composable
internal fun SharePurchaseTimelineTile(
    model: PortfolioModel,
    entry: ShareHistoryEntry,
    valuation: ValuedSharePosition?,
    last: Boolean,
    canOpen: Boolean = true,
    uploadContract: (() -> Unit)? = null,
    open: () -> Unit,
) {
    val lot = entry.record
    val pending = entry is ShareHistoryEntry.PendingOrder
    val colours = MaterialTheme.colorScheme
    val currency = lot.text("currency")
    Row(Modifier.fillMaxWidth().drawBehind {
        val x = 5.dp.toPx()
        val y = 11.dp.toPx()
        // Continue through the parent list's section spacing, ending at the final purchase.
        if (!last) drawLine(colours.outlineVariant, Offset(x, y),
            Offset(x, size.height + InziraSpacing.section.toPx() + y), 1.dp.toPx())
        drawCircle(colours.background, 5.dp.toPx(), Offset(x, y))
        if (pending) drawCircle(colours.primary, 3.dp.toPx(), Offset(x, y), style = Stroke(1.dp.toPx()))
        else drawCircle(colours.primary, 3.dp.toPx(), Offset(x, y))
    }, horizontalArrangement = Arrangement.spacedBy(InziraSpacing.medium)) {
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InziraSpacing.tight)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(InziraSpacing.small),
                verticalArrangement = Arrangement.spacedBy(InziraSpacing.tight)) {
                if (pending) PendingStatusBadge()
                AppText((if (pending) "${localized("Request date")} · " else "") +
                    (entry.date?.let(::detailDate) ?: localized("Unavailable")),
                    style = MaterialTheme.typography.labelMedium, color = colours.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(enabled = canOpen, role = Role.Button,
                    onClickLabel = localized(if (pending) "Pending share order" else "View purchase"), onClick = open)
                .padding(vertical = InziraSpacing.small), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InziraSpacing.small)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InziraSpacing.small)) {
                    FinancialText("${formatNumber(lot.optDouble("shares"))} ${localized("Shares").lowercase()} × ${money(lot.optDouble("pricePerShare"), currency)}",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(InziraSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(InziraSpacing.tight)) {
                        val amountKey = if (pending) "estimatedTotal" else "netAmountPayable"
                        FinancialText("${localized(if (pending) "Estimated total" else "Total paid")} ${if (lot.has(amountKey)) money(lot.optDouble(amountKey), currency) else "—"}",
                            style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                        val rate = if (pending) null else valuation?.gainLossRate
                        if (rate != null) FinancialText(
                            "${if (rate > 0) "+" else ""}${percent(rate)} · ${localized("Since purchase").lowercase()}",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                            color = if (rate < 0) colours.error else colours.primary)
                        else if (!pending) DetailNote(if (lot.optDouble("remainingShares", lot.optDouble("shares")) <= 0)
                            "No shares remaining" else "Return unavailable")
                    }
                    if (!pending && lot.optDouble("soldShares", 0.0) > 0) FinancialText(
                        "${localized("Shares remaining")} ${formatNumber(lot.optDouble("remainingShares"))}",
                        style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                }
                AppIcon(Icons.Outlined.ChevronRight, if (pending) "Pending share order" else "View purchase", Modifier.size(20.dp), tint = colours.onSurfaceVariant)
            }
            if (pending) {
                uploadContract?.let { upload ->
                    TextButton(onClick = upload, enabled = !model.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        AppIcon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(InziraSpacing.small))
                        AppText("Upload contract")
                    }
                }
            } else DetailDocumentRows(model, lot, compact = true)
            HorizontalDivider(color = colours.outlineVariant.copy(alpha = .5f))
        }
    }
}

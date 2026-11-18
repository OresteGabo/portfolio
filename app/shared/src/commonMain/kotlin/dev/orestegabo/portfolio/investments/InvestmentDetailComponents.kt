package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

@Composable
internal fun DetailHeading(title: String, supporting: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        supporting?.let { AppText(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun DetailAmount(label: String, value: Double?, currency: String, prominent: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val style = if (prominent && maxWidth >= 300.dp && LocalDensity.current.fontScale < 1.4f)
            MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppText(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FinancialText(currency, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FinancialText(displayAmountOrUnavailable(value), style = style.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun DetailColumns(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp || LocalDensity.current.fontScale >= 1.4f) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { first(); second() }
        } else Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.weight(1f)) { first() }; Box(Modifier.weight(1f)) { second() }
        }
    }
}

@Composable
internal fun DetailStatistic(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppText(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FinancialText(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun DetailValueRow(label: String, value: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.4f) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FinancialText(value, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            AppText(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                FinancialText(value, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
            }
        }
    }
}

@Composable
internal fun DetailNote(text: String) {
    AppText(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun DetailDisclosure(title: String, expanded: Boolean, toggle: () -> Unit) {
    TextButton(onClick = toggle, contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
            stateDescription = localized(if (expanded) "Expanded" else "Collapsed")
        }) {
        AppText(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        AppIcon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
    }
}

internal fun detailDate(date: LocalDate) = "${date.day} ${date.monthLabel().take(3)} ${date.year}"

@Composable
internal fun DetailDocumentRows(model: PortfolioModel, record: JsonRecord, compact: Boolean = false) {
    DetailDocumentRows(model, listOf(record), compact)
}

@Composable
internal fun DetailDocumentRows(model: PortfolioModel, records: List<JsonRecord>, compact: Boolean = false) {
    var downloading by remember { mutableStateOf<String?>(null) }
    val files = investmentEvidence(records, model.documents)
    if (files.isEmpty()) DetailNote("No linked document")
    files.forEach { evidence ->
        val actions = LocalNativeActions.current
        val doc = evidence.document
        Column {
            Row(Modifier.fillMaxWidth().clickable(enabled = !model.busy, onClickLabel = localized("Open PDF")) {
                model.openDocumentPreview(doc)
            }.padding(vertical = if (compact) 0.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(Icons.Outlined.Description, null, Modifier.size(if (compact) 18.dp else 24.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FinancialText(doc.text("label"), style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        maxLines = if (compact) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                    if (!compact) DetailNote(listOf("PDF", calendarDate(doc.text("documentDate"))?.let(::detailDate).orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
                }
                IconButton(enabled = !model.busy, onClick = { model.documentAction {
                    downloading = doc.text("id")
                    try { if (shareInvestmentDocument(model, actions, doc)) model.message = "PDF ready to share." }
                    finally { downloading = null }
                } }, modifier = Modifier.size(48.dp)) {
                    if (downloading == doc.text("id")) InziraLoadingMark(active = true, modifier = Modifier.size(24.dp)
                        .semantics { contentDescription = localized("Downloading document") })
                    else AppIcon(Icons.Outlined.Download, "Download ${privateFinancialValue(doc.text("label"))}")
                }
            }
            if (!compact) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
    }
}

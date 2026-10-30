package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
internal fun DocumentHeader(section: String, account: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (section == "Import") StructMark(Modifier.size(32.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(if (section == "Import") "Struct Capital" else "Documents",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            FinancialText(account, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun DocumentsHub(model: PortfolioModel, engine: InvestmentEngine, review: JsonRecord?, target: JsonRecord?,
    section: String, changeSection: (String) -> Unit, showHint: Boolean, dismissHint: () -> Unit, beginImport: () -> Unit, reviewed: (JsonRecord) -> Unit, done: () -> Unit,
    openPurchase: (String) -> Unit, openMissing: () -> Unit) {
    val account = model.user?.text("id").orEmpty()
    var focusDocument by rememberSaveable(account) { mutableStateOf<String?>(null) }
    var pendingDocument by rememberSaveable(account) { mutableStateOf<String?>(null) }
    val holder = rememberSaveableStateHolder()
    LaunchedEffect(pendingDocument, section, model.busy, model.documents) {
        if (section == "Library" && !model.busy) pendingDocument?.let { id ->
            model.documents.firstOrNull { it.text("id") == id }?.let { document ->
                pendingDocument = null
                model.openDocumentPreview(document)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        holder.SaveableStateProvider(section) {
            if (section == "Library") DocumentsScreen(model, initialDocumentId = focusDocument)
            else ImportConversation(model, engine, review, target, beginImport, reviewed, done,
                showHint = showHint, dismissHint = dismissHint,
                openDocument = { id ->
                    focusDocument = id; pendingDocument = id; changeSection("Library")
                    if (model.documents.none { it.text("id") == id } && !model.busy) model.refreshPortfolio()
                }, openPurchase = openPurchase, openMissing = openMissing)
        }
    }
}

@Composable
private fun ImportConversation(model: PortfolioModel, engine: InvestmentEngine, review: JsonRecord?, target: JsonRecord?,
    beginImport: () -> Unit, reviewed: (JsonRecord) -> Unit, done: () -> Unit,
    showHint: Boolean, dismissHint: () -> Unit, openDocument: (String) -> Unit, openPurchase: (String) -> Unit, openMissing: () -> Unit) {
    val entries = model.importHistory
    val scroll = rememberLazyListState()
    val actions = LocalNativeActions.current
    val colours = MaterialTheme.colorScheme
    val active = model.activeImportId
    val activeEntry = entries.firstOrNull { it.id == active }
    LaunchedEffect(entries.size, active, activeEntry?.state) {
        if (entries.isNotEmpty()) {
            val index = entries.indexOfFirst { it.id == active }.takeIf { it >= 0 } ?: entries.lastIndex
            // The reading card is short. Scroll again when it grows into review or a final receipt.
            scroll.animateScrollToItem(index)
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        ImportWallpaper(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize()) {
            if (showHint) Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp), color = colours.onSurface.copy(alpha = .035f)) {
                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top) {
                    AppIcon(Icons.Outlined.Info, null, Modifier.padding(top = 12.dp).size(18.dp), tint = colours.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (entries.isNotEmpty()) AppText("Attach a PDF to identify its type and review the details before saving.",
                            style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                        AppText("Import activity is kept on this device. Saved files remain in Library.",
                            style = MaterialTheme.typography.labelSmall, color = colours.onSurfaceVariant)
                    }
                    IconButton(onClick = dismissHint) { AppIcon(Icons.Outlined.Close, "Dismiss import information", Modifier.size(18.dp), tint = colours.onSurfaceVariant) }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = scroll, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (entries.isEmpty() && !model.importHistoryStorageFailed) item(key = "empty-imports") {
                    ImportEmptyState(Modifier.fillParentMaxHeight())
                }
                items(entries, key = { it.id }) { entry ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(Modifier.align(Alignment.End).widthIn(max = 360.dp).fillMaxWidth(.84f), shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                            color = colours.primaryContainer.copy(alpha = .75f)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AppIcon(Icons.Outlined.PictureAsPdf, "PDF attachment", Modifier.size(24.dp), tint = colours.primary)
                                Column(Modifier.weight(1f)) {
                                    FinancialText(entry.fileName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    AppText(importTime(entry.createdAt), Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall, color = colours.onSurfaceVariant)
                                }
                            }
                        }
                        Row(Modifier.align(Alignment.Start).widthIn(max = 520.dp).fillMaxWidth(.90f),
                            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StructMark(Modifier.padding(bottom = 4.dp).size(22.dp))
                            SystemMessage(Modifier.weight(1f)) {
                                val live = entry.id == active
                                val inProgress = entry.state in listOf(ImportState.READING, ImportState.SAVING) && live
                                if (entry.state !in listOf(ImportState.SAVED, ImportState.EXISTS)) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (inProgress) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else AppIcon(when (entry.state) {
                                        ImportState.SAVED -> Icons.Outlined.CheckCircle
                                        ImportState.FAILED -> Icons.Outlined.ErrorOutline
                                        ImportState.REVIEW -> Icons.Outlined.FactCheck
                                        else -> Icons.Outlined.Info
                                    }, null, Modifier.size(18.dp), tint = if (entry.state == ImportState.FAILED) colours.error else colours.primary)
                                    AppText(importStatus(entry), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleSmall)
                                }
                                if (inProgress) {
                                    AppText(if (model.pdfImportProgress?.stage == PdfImportStage.SAVING) when (entry.kind) {
                                        "statement" -> "Checking statement records"
                                        "bond-issuance" -> if (entry.targetId.isBlank()) "Saving bond terms" else "Confirming purchase"
                                        else -> "Saving purchase"
                                    } else model.pdfImportProgress?.stage?.label ?: "Checking details", style = MaterialTheme.typography.bodyMedium)
                                    model.pdfImportProgress?.detail?.let(::safePdfProgressDetail)?.takeIf(String::isNotBlank)?.let { AppText(it, style = MaterialTheme.typography.labelSmall) }
                                    TextButton(onClick = model::cancelPdfImport) { AppText("Cancel") }
                                } else if (entry.state in listOf(ImportState.SAVED, ImportState.EXISTS)) {
                                    ImportCompletionReceipt(entry, model.documents.firstOrNull { it.text("id") == entry.documentId },
                                        (if (entry.kind == "equity") model.holdings else model.purchases).firstOrNull { it.text("id") == entry.purchaseId },
                                        firstImportAddedTime(model.documents.firstOrNull { it.text("id") == entry.documentId }, entries))
                                    if (entry.outcome == ImportOutcome.STATEMENT) {
                                        AppText("${entry.recorded} ${localized("recorded")} · ${entry.missing} ${localized("missing PDFs")}", style = MaterialTheme.typography.labelMedium)
                                        TextButton(onClick = { model.showStatementDocumentReview(entry.documentId) }) { AppText("Review statement records") }
                                        if (entry.missing > 0) TextButton(onClick = openMissing) { AppText("View missing documents") }
                                    }
                                } else {
                                    if (entry.error.isNotBlank()) AppText(entry.error, color = colours.error, style = MaterialTheme.typography.bodyMedium)
                                    if (entry.documentId.isNotBlank()) AppText("This PDF is already in Library. The existing file will be reused.", style = MaterialTheme.typography.bodySmall)
                                    if (live && review != null) {
                                        ConversationReview(model, review, target, entry, done)
                                        TextButton(enabled = !model.busy, onClick = { model.cancelImportReview(review); done() }) { AppText("Cancel import") }
                                    } else if (entry.state in listOf(ImportState.FAILED, ImportState.INTERRUPTED, ImportState.CANCELLED)) {
                                        if (entry.state == ImportState.INTERRUPTED) AppText("This import was interrupted. Attach the same PDF again to continue.", style = MaterialTheme.typography.bodyMedium)
                                        OutlinedButton(enabled = !model.busy && review == null, onClick = { model.retryImport(entry.id, actions, engine, reviewed) }) { AppText("Retry import") }
                                    }
                                }
                                if (entry.attempts > 1) AppText("${localized("Attempts")}: ${entry.attempts}", style = MaterialTheme.typography.labelSmall, color = colours.onSurfaceVariant)
                                if (entry.events.isNotEmpty()) {
                                    var showAttempts by rememberSaveable(entry.id) { mutableStateOf(false) }
                                    TextButton(onClick = { showAttempts = !showAttempts }) { AppText("Previous attempts") }
                                    if (showAttempts) entry.events.forEach { event ->
                                        AppText(importTime(event.at), style = MaterialTheme.typography.labelSmall, color = colours.onSurfaceVariant)
                                        AppText(if (event.error.isNotBlank()) event.error else when (event.state) {
                                            ImportState.CANCELLED -> "Import cancelled"
                                            else -> "Import interrupted"
                                        }, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (entry.documentId.isNotBlank()) TextButton(enabled = !model.busy, onClick = { openDocument(entry.documentId) }) { AppText("Open document") }
                                if (entry.purchaseId.isNotBlank() && entry.kind in listOf("bond", "bond-issuance"))
                                    TextButton(enabled = !model.busy, onClick = { openPurchase(entry.purchaseId) }) { AppText("View purchase") }
                            }
                        }
                    }
                }
                if (model.importHistoryStorageFailed) item { AppText("Import activity could not be saved on this device. Saved documents remain in Library.", color = colours.error, style = MaterialTheme.typography.bodySmall) }
                if (entries.size == 1000) item { AppText("Showing the latest 1,000 imports on this device.", style = MaterialTheme.typography.labelSmall) }
            }
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(onClick = beginImport, enabled = !model.busy && review == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp)) {
                        AppIcon(Icons.Outlined.AttachFile, null); Spacer(Modifier.width(8.dp)); AppText("Attach PDF")
                    }
                    if (review == null) AppText("Read on your device · Up to 10 MB",
                        Modifier.fillMaxWidth().padding(top = 5.dp), style = MaterialTheme.typography.labelSmall, color = colours.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SystemMessage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colours = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), color = colours.surface,
        border = BorderStroke(1.dp, colours.outlineVariant.copy(alpha = .25f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

private fun importTime(raw: String): String = runCatching {
    val date = Instant.parse(raw).toLocalDateTime(TimeZone.currentSystemDefault())
    "${date.day} ${date.date.monthLabel().take(3)} ${date.year} · ${date.hour.toString().padStart(2, '0')}:${date.minute.toString().padStart(2, '0')}"
}.getOrDefault("")

@Composable
private fun importStatus(entry: ImportReceipt) = when (entry.state) {
    ImportState.READING -> "Reading your PDF"
    ImportState.SAVING -> "Saving your import"
    ImportState.REVIEW -> when (entry.kind) { "bond" -> "Bond confirmation detected"; "equity" -> "Share contract detected"; "statement" -> "Statement detected"; "bond-issuance" -> "Issuance report detected"; else -> "PDF needs review" }
    ImportState.SAVED -> "Import complete"
    ImportState.EXISTS -> "Already in Library"
    ImportState.FAILED -> "Import needs attention"
    ImportState.CANCELLED -> "Import cancelled"
    ImportState.INTERRUPTED -> "Import interrupted"
}

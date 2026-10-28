package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DocumentsScreen(model: PortfolioModel, initialDocumentId: String? = null) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf<String?>(null) }
    var annualToExport by rememberSaveable { mutableStateOf<Int?>(null) }
    var previewAnnual by rememberSaveable { mutableStateOf(false) }
    val actions = LocalNativeActions.current
    val focus = LocalFocusManager.current
    val feedback = LocalSelectionFeedback.current
    val reducedMotion = LocalReducedMotion.current
    val filters = rememberLazyListState()
    val libraryScroll = rememberLazyListState()
    var focusConsumed by rememberSaveable(initialDocumentId) { mutableStateOf(false) }
    LaunchedEffect(initialDocumentId) {
        if (initialDocumentId != null && !focusConsumed) { search = ""; category = "all"; expanded = initialDocumentId }
    }
    val colours = MaterialTheme.colorScheme
    fun save(document: JsonRecord) = model.action {
        saving = document.text("id")
        try {
            val path = document.text("downloadUrl")
            require(path.matches(Regex("/api/bonds/documents/[A-Za-z0-9_-]+"))) { "Invalid document URL." }
            val data = model.api.bytes(path)
            require(data.take(5).toByteArray().decodeToString() == "%PDF-") { "The server did not return a PDF." }
            if (actions.sharePdf(document.text("originalFileName").ifBlank { "investment.pdf" }, data)) model.message = "PDF ready to share."
        } finally { saving = null }
    }
    var historyRequested by remember(model.user?.text("id")) { mutableStateOf(false) }
    LaunchedEffect(model.user?.text("id"), model.busy) {
        if (!model.busy && !historyRequested) {
            historyRequested = true
            model.loadInvestmentHistory()
        }
    }
    val libraryDocuments = model.documents + annualReportDocuments(model.portfolio, bondSubscriptionDate(kotlin.time.Clock.System.now()))
    val categories = listOf("all") + libraryDocuments.map { it.text("category") }.filter { it.isNotBlank() }.distinct()
    LaunchedEffect(categories) { if (category !in categories) category = "all" }
    LaunchedEffect(category, categories, AppLanguage.code) {
        val index = categories.indexOf(category)
        if (categories.size > 1 && index >= 0) {
            if (reducedMotion) filters.scrollToItem(index) else filters.animateScrollToItem(index)
        }
    }
    val filtered = libraryDocuments.filter { document ->
        (category == "all" || document.text("category") == category) &&
            (listOf("label", "instrumentName", "description", "issuer", "originalFileName").any { document.text(it).contains(search, true) } ||
                (document.optInt("annualReportYear") > 0 && localized("Annual report").contains(search, true)))
    }.sortedByDescending { calendarDate(it.text("documentDate")) }
    val groups = filtered.groupBy { document ->
        calendarDate(document.text("documentDate"))?.let { "${it.year}-${it.month}" }.orEmpty()
    }
    LaunchedEffect(initialDocumentId, groups, category) {
        if (initialDocumentId != null && !focusConsumed && category == "all") {
            var index = if (libraryDocuments.isNotEmpty()) 2 + if (categories.size > 1) 1 else 0 else 0
            for ((_, rows) in groups) {
                index++
                val at = rows.indexOfFirst { it.text("id") == initialDocumentId }
                if (at >= 0) { libraryScroll.scrollToItem(index + at); focusConsumed = true; break }
                index += rows.size
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = libraryScroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (libraryDocuments.isNotEmpty()) {
            item { DocumentLibraryHeader(libraryDocuments.size, model.busy, model.busy && saving == null && model.annualReportYear == null,
                { historyRequested = false; model.refresh() }) }
            item {
                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(),
                    placeholder = { AppText("Search documents") }, singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = { AppIcon(Icons.Outlined.Search, "Search documents") },
                    trailingIcon = if (search.isNotEmpty()) { { IconButton(onClick = { search = "" }) { AppIcon(Icons.Outlined.Close, "Clear search") } } } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = colours.surface,
                        focusedContainerColor = colours.surface,
                        unfocusedBorderColor = colours.outlineVariant.copy(alpha = .6f)))
            }
            if (categories.size > 1) item {
                LazyRow(Modifier.selectableGroup(), state = filters, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { value ->
                        val selected = category == value
                        Surface(shape = RoundedCornerShape(14.dp),
                            color = if (selected) colours.primaryContainer else colours.surface,
                            border = BorderStroke(1.dp, if (selected) colours.primary.copy(alpha = .3f) else colours.outlineVariant.copy(alpha = .5f)),
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).selectable(selected, role = Role.Tab, onClick = {
                                if (!selected) { category = value; expanded = null; feedback() }
                            })) {
                            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                AppText(if (value == "all") "All" else value, style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) colours.onPrimaryContainer else colours.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (initialDocumentId != null && !model.busy && libraryDocuments.none { it.text("id") == initialDocumentId }) item {
            AppText("This document is not available in Library. Refresh to check whether it was removed.", color = colours.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (filtered.isEmpty()) item {
            when {
                model.busy -> LoadingState()
                libraryDocuments.isEmpty() -> EmptyState("No documents yet", EmptyArtwork.DOCUMENTS, actionLabel = "Refresh", onAction = model::refresh)
                else -> EmptyState("No matches", EmptyArtwork.SEARCH, actionLabel = "Clear filters", onAction = { search = ""; category = "all" })
            }
        }
        groups.forEach { (month, documents) ->
            item(key = "month:$month") {
                val date = calendarDate(documents.first().text("documentDate"))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppText(date?.let { "${it.monthLabel()} ${it.year}" } ?: localized("Other files"),
                        Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FinancialText(documents.size.toString(), style = MaterialTheme.typography.labelMedium, color = colours.onSurfaceVariant)
                }
            }
            items(documents, key = { "document:${it.text("id")}" }) { document ->
                DocumentCard(document, expanded == document.text("id"), saving == document.text("id") || model.annualReportYear == document.optInt("annualReportYear", -1), !model.busy,
                    open = { if (document.optInt("annualReportYear") > 0) { previewAnnual = true; annualToExport = document.optInt("annualReportYear") } else model.openDocumentPreview(document) },
                    toggle = { expanded = if (expanded == document.text("id")) null else document.text("id") },
                    download = { if (document.optInt("annualReportYear") > 0) { previewAnnual = false; annualToExport = document.optInt("annualReportYear") } else save(document) })
            }
        }
    }
    annualToExport?.let { year ->
        AlertDialog(onDismissRequest = { annualToExport = null }, title = { AppText("${localized("Annual report")} · $year") },
            text = { AppText("Generate a calendar-year PDF with your financial figures, even when Hide numbers is on. The current year is limited to today. Original documents are not included.") },
            confirmButton = { TextButton(enabled = !model.busy, onClick = { annualToExport = null
                if (previewAnnual) libraryDocuments.firstOrNull { it.optInt("annualReportYear") == year }?.let(model::openDocumentPreview)
                else model.exportAnnualReport(year, actions) }) { AppText("Generate PDF") } },
            dismissButton = { TextButton(onClick = { annualToExport = null }) { AppText("Cancel") } })
    }
    if (model.annualReportYear != null) model.reportProgress?.let { progress ->
        AlertDialog(onDismissRequest = {}, title = { AppText("Annual report") }, text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp))
                AppText(progress, Modifier.weight(1f))
            }
        }, confirmButton = {})
    }

}

@Composable
private fun DocumentLibraryHeader(count: Int, busy: Boolean, refreshing: Boolean, refresh: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    val dark = colours.surface.luminance() < .5f
    val start = colours.primary.copy(alpha = if (dark) .15f else .09f).compositeOver(colours.surface)
    Surface(shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, colours.primary.copy(alpha = .14f))) {
        Box(Modifier.fillMaxWidth().heightIn(min = 142.dp).background(Brush.linearGradient(listOf(start, colours.surface)))) {
            DocumentStack(Modifier.align(Alignment.CenterEnd).padding(end = 30.dp).size(112.dp), colours.primary, colours.surface)
            Column(Modifier.padding(22.dp).padding(end = 132.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText("Documents", style = MaterialTheme.typography.labelLarge, color = colours.onSurfaceVariant)
                FinancialText(count.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                AppText("PDF", style = MaterialTheme.typography.labelSmall, color = colours.primary)
            }
            IconButton(onClick = refresh, enabled = !busy, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else AppIcon(Icons.Outlined.Refresh, "Refresh documents", Modifier.size(20.dp))
            }
        }
    }
}

/** Decorative paper, never a fabricated preview of the user's actual file. */
@Composable
private fun DocumentStack(modifier: Modifier, ink: Color, paper: Color) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width; val h = size.height
        rotate(-12f) {
            drawRoundRect(ink.copy(alpha = .10f), Offset(w * .19f, h * .14f), Size(w * .56f, h * .76f), CornerRadius(w * .07f))
        }
        rotate(7f) {
            drawRoundRect(paper, Offset(w * .26f, h * .13f), Size(w * .56f, h * .76f), CornerRadius(w * .07f))
            drawRoundRect(ink.copy(alpha = .32f), Offset(w * .26f, h * .13f), Size(w * .56f, h * .76f), CornerRadius(w * .07f), style = Stroke(1.5.dp.toPx()))
            for (i in 0..2) drawLine(ink.copy(alpha = .24f), Offset(w * .37f, h * (.37f + i * .11f)), Offset(w * (if (i == 2) .59f else .7f), h * (.37f + i * .11f)), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(ink.copy(alpha = .85f), w * .1f, Offset(w * .72f, h * .79f))
            drawLine(paper, Offset(w * .68f, h * .79f), Offset(w * .71f, h * .82f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(paper, Offset(w * .71f, h * .82f), Offset(w * .77f, h * .76f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun DocumentCard(document: JsonRecord, expanded: Boolean, saving: Boolean, canDownload: Boolean, open: () -> Unit, toggle: () -> Unit, download: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    val category = document.text("category")
    val annualYear = document.optInt("annualReportYear")
    val generated = annualYear > 0
    val logo = remember(document) { documentInvestmentLogo(document) }
    val shares = category.contains("share", true) || category.contains("equity", true) || (logo != null && !logo.bond)
    val dark = colours.surface.luminance() < .5f
    val accent = if (shares) (if (dark) Color(0xFF9CCBBA) else Color(0xFF397562)) else colours.primary
    val title = if (generated) "${localized("Annual report")} · $annualYear" else document.text("label").ifBlank { document.text("originalFileName").ifBlank { "PDF" } }
    val date = calendarDate(document.text("documentDate"))
    Surface(shape = RoundedCornerShape(22.dp), color = colours.surface,
        border = BorderStroke(1.dp, if (expanded) accent.copy(alpha = .4f) else colours.outlineVariant.copy(alpha = .5f))) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
                enabled = canDownload, onClickLabel = localized("Open PDF"), onClick = open)
                .padding(start = 16.dp, top = 18.dp, end = 10.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 58.dp, height = 72.dp)) {
                    PdfCover(accent)
                    if (generated || logo != null || shares) {
                        Surface(Modifier.align(Alignment.BottomEnd).size(36.dp)
                            .then(if (logo != null) Modifier.semantics { contentDescription = localized("Logo: ${logo.name}") } else Modifier),
                            shape = androidx.compose.foundation.shape.CircleShape, color = colours.surface,
                            border = BorderStroke(2.dp, colours.surface), shadowElevation = 1.dp) {
                            Box(Modifier.padding(2.dp), contentAlignment = Alignment.Center) {
                                if (generated) StructMark(Modifier.fillMaxSize().padding(3.dp))
                                else if (logo != null) InvestmentLogo(ticker = logo.ticker, bond = logo.bond,
                                    modifier = Modifier.fillMaxSize(), compact = true)
                                else AppIcon(Icons.AutoMirrored.Outlined.TrendingUp, "Shares", Modifier.size(20.dp), tint = accent)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (category.isNotBlank()) AppText(category, style = MaterialTheme.typography.labelSmall, color = accent)
                    FinancialText(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (generated) AppText(document.text("description"), style = MaterialTheme.typography.labelSmall, color = colours.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppText(date?.let { "${it.day} ${it.monthLabel().take(3)} ${it.year}" } ?: document.text("documentDate"),
                            Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colours.onSurfaceVariant)
                    }
                }
                FilledTonalIconButton(onClick = download, enabled = canDownload, modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = accent.copy(alpha = .09f), contentColor = accent)) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = localized("Downloading document") }, strokeWidth = 2.dp)
                    else AppIcon(Icons.Outlined.Share, "${localized("Share PDF")}: ${privateFinancialValue(title)}", Modifier.size(22.dp))
                }
            }
            TextButton(onClick = toggle, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)
                .semantics { stateDescription = localized(if (expanded) "Expanded" else "Collapsed") }) {
                AppText(if (expanded) "Hide details" else "Details", style = MaterialTheme.typography.labelMedium)
                AppIcon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
            }
            AppAnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = colours.outlineVariant.copy(alpha = .5f))
                    FinancialText(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (document.text("instrumentName").isNotBlank() && document.text("instrumentName") != title)
                        FinancialText(document.text("instrumentName"), style = MaterialTheme.typography.bodyMedium)
                    if (document.text("description").isNotBlank()) FinancialText(document.text("description"), style = MaterialTheme.typography.bodySmall)
                    if (document.text("issuer").isNotBlank()) Fact("Issuer", document.text("issuer"))
                    if (document.text("originalFileName").isNotBlank()) Fact("File name", document.text("originalFileName"))
                    document.optJSONObject("details")?.let { details ->
                        if (details.text("summary").isNotBlank()) FinancialText(details.text("summary"), style = MaterialTheme.typography.bodySmall)
                        listOf("facts", "charges", "flow").forEach { key -> details.rows(key).forEach { Fact(it.text("label"), it.text("value")) } }
                        details.rows("sections").forEach { section ->
                            AppText(section.text("title"), Modifier.semantics { heading() }, fontWeight = FontWeight.SemiBold)
                            section.rows("items").forEach { Fact(it.text("label"), it.text("value")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PdfCover(accent: Color) {
    val paper = MaterialTheme.colorScheme.surface
    Box(Modifier.size(width = 46.dp, height = 60.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val fold = size.width * .28f
            val outline = Path().apply {
                moveTo(5.dp.toPx(), 0f); lineTo(size.width - fold, 0f); lineTo(size.width, fold)
                lineTo(size.width, size.height - 5.dp.toPx()); quadraticTo(size.width, size.height, size.width - 5.dp.toPx(), size.height)
                lineTo(5.dp.toPx(), size.height); quadraticTo(0f, size.height, 0f, size.height - 5.dp.toPx())
                lineTo(0f, 5.dp.toPx()); quadraticTo(0f, 0f, 5.dp.toPx(), 0f); close()
            }
            drawPath(outline, accent.copy(alpha = .09f))
            drawPath(outline, accent.copy(alpha = .2f), style = Stroke(1.dp.toPx()))
            drawPath(Path().apply { moveTo(size.width - fold, 0f); lineTo(size.width - fold, fold); lineTo(size.width, fold); close() }, accent.copy(alpha = .16f))
            for (i in 0..1) drawLine(accent.copy(alpha = .2f), Offset(size.width * .23f, size.height * (.7f + i * .1f)), Offset(size.width * .72f, size.height * (.7f + i * .1f)), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
        }
        AppText("PDF", Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent)
    }
}

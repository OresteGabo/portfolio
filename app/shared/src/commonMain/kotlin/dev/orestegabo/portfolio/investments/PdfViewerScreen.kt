package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.abs
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PDF_DISPLAY_ERROR = "This PDF could not be displayed. It may be damaged or password-protected."

@Composable
internal fun PdfViewerScreen(model: PortfolioModel, preview: DocumentPreview) {
    val loader = rememberPdfDocumentLoader()
    var document by remember(preview) { mutableStateOf<PdfDocumentPages?>(null) }
    var failure by remember(preview) { mutableStateOf(false) }
    LaunchedEffect(preview.bytes) {
        val bytes = preview.bytes ?: return@LaunchedEffect
        try {
            // Own the native handle even when Back cancels the coroutine during creation.
            val opened = withContext(NonCancellable) { loader(bytes) }
            if (isActive) document = opened else opened.close()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = true }
    }
    DisposableEffect(document) { val handle = document; onDispose { handle?.close() } }
    when {
        failure || preview.bytes == null && !model.busy -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            EmptyState("Could not open PDF", EmptyArtwork.DOCUMENTS, compact = true,
                description = if (failure) PDF_DISPLAY_ERROR else model.error ?: "Try opening this document again.",
                actionLabel = "Retry", onAction = { model.openDocumentPreview(preview.source) })
        }
        preview.bytes == null || document == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp))
                AppText(model.reportProgress ?: "Opening PDF", style = MaterialTheme.typography.bodyMedium)
            }
        }
        model.captureProtectionEnabled && !nativePdfCaptureProtectionAvailable -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            EmptyState("PDF hidden for privacy", EmptyArtwork.DOCUMENTS, compact = true,
                description = "The PDF will appear when the privacy shield is ready.")
        }
        else -> {
            val pages = document!!
            if (model.captureProtectionEnabled) CaptureRedactionRegion(Modifier.fillMaxSize(), masked = {
                Surface(Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { AppIcon(Icons.Outlined.Lock, "PDF hidden for privacy", Modifier.size(40.dp)) } }
            }) { PdfPageList(pages) { failure = true } }
            else PdfPageList(pages) { failure = true }
        }
    }
}

@Composable
private fun PdfPageList(document: PdfDocumentPages, failed: () -> Unit) {
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val zoom = remember(document) { mutableStateMapOf<Int, Float>() }
    val current by remember(document, scroll) { derivedStateOf {
        val layout = scroll.layoutInfo
        layout.visibleItemsInfo.maxByOrNull { item ->
            (minOf(item.offset + item.size, layout.viewportEndOffset) - maxOf(item.offset, layout.viewportStartOffset)).coerceAtLeast(0)
        }?.index?.coerceIn(0, document.pageCount - 1) ?: 0
    } }
    fun changeZoom(page: Int, value: Float) {
        val old = zoom[page] ?: 1f
        val next = value.coerceIn(1f, 6f)
        val layout = scroll.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == page }
        zoom[page] = next
        if (item != null && old != next) {
            // Keep the point under the reader's centre in view as the page grows/shrinks.
            val centre = (layout.viewportEndOffset + layout.viewportStartOffset) / 2f
            val anchor = (centre - item.offset).coerceIn(0f, item.size.toFloat())
            val newOffset = (anchor * (next / old) - centre).roundToInt()
            scope.launch { scroll.scrollToItem(page, newOffset) }
        }
    }
    val reducedMotion = LocalReducedMotion.current
    fun move(index: Int) { scope.launch { if (reducedMotion) scroll.scrollToItem(index) else scroll.animateScrollToItem(index) } }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF30332D)), state = scroll,
            contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0 until document.pageCount).toList(), key = { it }) { page ->
                val viewport by remember(scroll, page) { derivedStateOf {
                    val layout = scroll.layoutInfo
                    val item = layout.visibleItemsInfo.firstOrNull { it.index == page }
                    val top = if (item == null) 0 else (layout.viewportStartOffset - item.offset).coerceAtLeast(0)
                    val bottom = if (item == null) 0 else (layout.viewportEndOffset - item.offset).coerceIn(0, item.size)
                    top to (bottom - top).coerceAtLeast(0)
                } }
                PdfPageImage(document, page, zoom[page] ?: 1f, viewport.first, viewport.second,
                    { changeZoom(page, it) }, { scroll.dispatchRawDelta(-it) }, failed)
            }
        }
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(enabled = current > 0, onClick = { move(current - 1) }) { AppIcon(Icons.AutoMirrored.Outlined.NavigateBefore, "Previous page") }
                AppText("${current + 1} / ${document.pageCount}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                IconButton(enabled = current < document.pageCount - 1, onClick = { move(current + 1) }) { AppIcon(Icons.AutoMirrored.Outlined.NavigateNext, "Next page") }
                IconButton(enabled = (zoom[current] ?: 1f) > 1f, onClick = { changeZoom(current, ((zoom[current] ?: 1f) - .5f).coerceAtLeast(1f)) }) { AppIcon(Icons.Outlined.ZoomOut, "Zoom out") }
                IconButton(onClick = { zoom.clear(); move(current) }) { AppIcon(Icons.Outlined.SwapHoriz, "Fit to width") }
                IconButton(enabled = (zoom[current] ?: 1f) < 6f, onClick = { changeZoom(current, ((zoom[current] ?: 1f) + .5f).coerceAtMost(6f)) }) { AppIcon(Icons.Outlined.ZoomIn, "Zoom in") }
            }
        }
    }
}

@Composable
private fun PdfPageImage(document: PdfDocumentPages, page: Int, zoom: Float, visibleTop: Int, visibleHeight: Int,
    changeZoom: (Float) -> Unit, scrollVertically: (Float) -> Unit, failed: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }
        var image by remember(document, page, width) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(document, page, width) {
            try { image = document.renderPage(page, width) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed() }
        }
        val bitmap = image
        var offsetX by remember(document, page) { mutableFloatStateOf(0f) }
        val horizontalLimit = width * (zoom - 1) / 2
        val panX = offsetX.coerceIn(-horizontalLimit, horizontalLimit)
        LaunchedEffect(zoom, width) { offsetX = panX }
        val transform = rememberTransformableState { _, zoomChange, pan, _ ->
            val scale = (zoom * zoomChange).coerceIn(1f, 6f)
            if (scale != zoom) changeZoom(scale)
            val limit = width * (scale - 1) / 2
            offsetX = (panX + pan.x).coerceIn(-limit, limit)
            if (pan.y != 0f) scrollVertically(pan.y)
        }
        if (bitmap == null) Box(Modifier.fillMaxWidth().height(300.dp).background(Color.White), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        } else {
            val pageHeight = maxWidth * (bitmap.height.toFloat() / bitmap.width)
            val baseHeight = with(LocalDensity.current) { pageHeight.toPx() }
            val region = if (visibleHeight > 0) pdfViewportRegion(zoom, panX, width, baseHeight, visibleTop, visibleHeight) else null
            var detail by remember(document, page, width) { mutableStateOf<Pair<PdfPageRegion, ImageBitmap>?>(null) }
            LaunchedEffect(document, page, width, region, zoom) {
                if (region == null || zoom <= 1f) { detail = null; return@LaunchedEffect }
                // Render only the portion inside the reader viewport, even for a very tall zoomed page.
                delay(70)
                try { detail = region to document.renderPage(page, width, region) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed() }
            }
            val description = "${localized("PDF page")} ${page + 1}"
            Canvas(Modifier.fillMaxWidth().height(pageHeight * zoom).clipToBounds().background(Color.White)
                .semantics { contentDescription = description }
                .transformable(transform, canPan = { zoom > 1f && abs(it.x) > abs(it.y) })
                .pointerInput(zoom) { detectTapGestures(onDoubleTap = { changeZoom(if (zoom > 1f) 1f else 2f); offsetX = 0f }) }) {
                // The page's layout height grows with zoom. Vertical motion belongs to the
                // outer scroll view; horizontal panning stays within the full reader width.
                val fullWidth = size.width * zoom
                drawImage(bitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - fullWidth) / 2 + panX).roundToInt(), 0),
                    dstSize = IntSize(fullWidth.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.High)
                detail?.takeIf { it.first == region && zoom > 1f }?.second?.let {
                    drawImage(it, dstOffset = androidx.compose.ui.unit.IntOffset(0, visibleTop),
                        dstSize = IntSize(size.width.roundToInt(), visibleHeight), filterQuality = FilterQuality.High)
                }
            }
        }
    }
}

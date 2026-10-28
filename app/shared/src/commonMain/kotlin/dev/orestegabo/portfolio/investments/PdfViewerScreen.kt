package dev.orestegabo.portfolio.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
    val reducedMotion = LocalReducedMotion.current
    fun move(index: Int) { scope.launch { if (reducedMotion) scroll.scrollToItem(index) else scroll.animateScrollToItem(index) } }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF30332D)), state = scroll,
            contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0 until document.pageCount).toList(), key = { it }) { page ->
                PdfPageImage(document, page, zoom[page] ?: 1f, { zoom[page] = it }, failed)
            }
        }
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                IconButton(enabled = current > 0, onClick = { move(current - 1) }) { AppIcon(Icons.AutoMirrored.Outlined.NavigateBefore, "Previous page") }
                AppText("${current + 1} / ${document.pageCount}", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                IconButton(enabled = current < document.pageCount - 1, onClick = { move(current + 1) }) { AppIcon(Icons.AutoMirrored.Outlined.NavigateNext, "Next page") }
                IconButton(enabled = (zoom[current] ?: 1f) > 1f, onClick = { zoom[current] = ((zoom[current] ?: 1f) - .5f).coerceAtLeast(1f) }) { AppIcon(Icons.Outlined.ZoomOut, "Zoom out") }
                IconButton(onClick = { zoom.clear() }) { AppIcon(Icons.Outlined.SwapHoriz, "Fit to width") }
                IconButton(enabled = (zoom[current] ?: 1f) < 6f, onClick = { zoom[current] = ((zoom[current] ?: 1f) + .5f).coerceAtMost(6f) }) { AppIcon(Icons.Outlined.ZoomIn, "Zoom in") }
            }
        }
    }
}

@Composable
private fun PdfPageImage(document: PdfDocumentPages, page: Int, zoom: Float, changeZoom: (Float) -> Unit, failed: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }
        var image by remember(document, page, width) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(document, page, width) {
            try { image = document.renderPage(page, width) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed() }
        }
        val bitmap = image
        var offset by remember { mutableStateOf(Offset.Zero) }
        var height by remember { mutableIntStateOf(0) }
        LaunchedEffect(zoom, width, height) {
            val limitX = width * (zoom - 1) / 2
            val limitY = height * (zoom - 1) / 2
            offset = Offset(offset.x.coerceIn(-limitX, limitX), offset.y.coerceIn(-limitY, limitY))
        }
        val transform = rememberTransformableState { _, zoomChange, pan, _ ->
            val scale = (zoom * zoomChange).coerceIn(1f, 6f)
            changeZoom(scale)
            val limitX = width * (scale - 1) / 2
            val limitY = height * (scale - 1) / 2
            offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX), (offset.y + pan.y).coerceIn(-limitY, limitY))
        }
        if (bitmap == null) Box(Modifier.fillMaxWidth().height(300.dp).background(Color.White), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        } else {
            val pageHeight = maxWidth * (bitmap.height.toFloat() / bitmap.width)
            val region = pdfVisibleRegion(zoom, offset.x, offset.y, width, height)
            var detail by remember(document, page, width) { mutableStateOf<Pair<PdfPageRegion, ImageBitmap>?>(null) }
            LaunchedEffect(document, page, width, region) {
                if (zoom <= 1f) { detail = null; return@LaunchedEffect }
                // Keep gestures immediate, then redraw just the visible region at display resolution.
                // Never allocate a giant zoomed page or stretch the initial bitmap as the final result.
                delay(70)
                try { detail = region to document.renderPage(page, width, region) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed() }
            }
            val description = "${localized("PDF page")} ${page + 1}"
            Canvas(Modifier.fillMaxWidth().height(pageHeight).clipToBounds().background(Color.White)
                .semantics { contentDescription = description }
                .onSizeChanged { height = it.height }
                .transformable(transform, canPan = { zoom > 1f })
                .pointerInput(zoom) { detectTapGestures(onDoubleTap = { changeZoom(if (zoom > 1f) 1f else 2f); offset = Offset.Zero }) }) {
                val destination = IntSize(size.width.roundToInt(), size.height.roundToInt())
                withTransform({
                    translate(offset.x, offset.y)
                    scale(zoom, zoom, center)
                }) {
                    drawImage(bitmap, dstSize = destination, filterQuality = FilterQuality.High)
                }
                detail?.takeIf { it.first == region && zoom > 1f }?.second?.let {
                    drawImage(it, dstSize = destination, filterQuality = FilterQuality.High)
                }
            }
        }
    }
}

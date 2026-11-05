package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.design.StructColors

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only existing, authenticated document endpoints; never generate a report just for its cover. */
internal fun documentThumbnailPath(document: JsonRecord): String? = document.text("downloadUrl")
    .takeIf { document.optInt("annualReportYear") <= 0 && it.matches(Regex("/api/bonds/documents/[A-Za-z0-9_-]+")) }

/** Memory only, session-scoped, serialized and bounded. Off-screen callers cancel their own work. */
internal class DocumentThumbnailCache {
    private val mutex = Mutex()
    private val entries = linkedMapOf<String, ImageBitmap?>()
    private var revision = 0L
    fun clear() { revision++; entries.clear() }

    // Called on the model/UI dispatcher, including clear(); native decoding dispatches off the UI thread.
    suspend fun get(key: String, load: suspend () -> ImageBitmap): ImageBitmap? {
        val current = revision
        return mutex.withLock {
            if (current != revision) return@withLock null
            if (entries.containsKey(key)) return@withLock entries.remove(key).also { entries[key] = it }
            val image = try { withTimeout(8_000) { load() } }
            catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null } // Optional preview failures never become a global import/session error.
            if (current != revision) return@withLock null
            entries[key] = image
            while (entries.size > 8 || entries.values.sumOf { it?.let { image -> image.width.toLong() * image.height * 4 } ?: 0L } > 4 * 1024 * 1024) {
                entries.remove(entries.keys.first())
            }
            image
        }
    }
}

internal suspend fun renderDocumentThumbnail(bytes: ByteArray, loader: suspend (ByteArray) -> PdfDocumentPages): ImageBitmap {
    require(bytes.size in 5..24 * 1024 * 1024 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-")
    val document = withContext(NonCancellable) { loader(bytes) }
    try {
        currentCoroutineContext().ensureActive()
        // A genuine first-page excerpt, rather than a tiny unreadable entire page.
        return document.renderPage(0, 480, PdfPageRegion(height = .4))
    } finally { withContext(NonCancellable + Dispatchers.Default) { document.close() } }
}

@Composable
internal fun DocumentThumbnail(model: PortfolioModel, document: JsonRecord, title: String, open: () -> Unit) {
    val path = if (model.isGuest) document.text("id").takeIf { it.startsWith("guest:document:") } ?: return else documentThumbnailPath(document) ?: return
    val loader = rememberPdfDocumentLoader()
    val cache = model.documentThumbnails
    val api = if (model.isGuest) null else model.api
    val hidden = LocalNumbersHidden.current || model.captureProtectionEnabled && !nativePdfCaptureProtectionAvailable
    var image by remember(cache, path, api, hidden) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(cache, path, api, hidden) { mutableStateOf(true) }
    LaunchedEffect(cache, path, api, hidden) {
        if (hidden || model.locked || model.user == null && !model.isGuest) return@LaunchedEffect
        // LazyColumn creates work only near the viewport; leaving it cancels pending downloads/decoding.
        try { image = cache.get(path) { renderDocumentThumbnail(if (model.isGuest) model.documentBytes(document) else requireNotNull(api).bytes(path), loader) } }
        finally { loading = false }
    }
    val frame = Modifier.fillMaxWidth().height(120.dp)
        .clip(RoundedCornerShape(13.dp))
        .clickable(enabled = !model.busy, role = Role.Button, onClickLabel = localized("Open PDF"), onClick = open)
    @Composable fun placeholder(masked: Boolean = hidden) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(if (masked) Icons.Outlined.VisibilityOff else Icons.Outlined.Description, null, Modifier.size(20.dp))
                AppText(if (masked) "PDF hidden for privacy" else if (loading) "Preparing preview" else "Open PDF to view content",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    @Composable fun preview() {
        val bitmap = image
        if (hidden || bitmap == null) placeholder()
        else Box(Modifier.fillMaxSize().background(StructColors.Document.paper)) {
            Image(bitmap, "${localized("PDF preview")}: ${privateFinancialValue(title)}", Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth, alignment = Alignment.TopCenter)
        }
    }
    Box(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp).drawWithCache {
        val depth = 6.dp.toPx()
        val inset = 13.dp.toPx()
        val shadow = Brush.verticalGradient(
            listOf(StructColors.Document.thumbnailShadow.copy(alpha = .14f), Color.Transparent),
            startY = size.height, endY = size.height + depth)
        onDrawBehind {
            // Confine the shadow below the paper; never outline its top or sides.
            drawRect(shadow, topLeft = Offset(inset, size.height), size = Size((size.width - inset * 2).coerceAtLeast(0f), depth))
        }
    }) {
        if (model.captureProtectionEnabled) CaptureRedactionRegion(frame, masked = { placeholder(true) }) { preview() }
        else Box(frame) { preview() }
    }
    RefreshCapturedUi(image, hidden)
}

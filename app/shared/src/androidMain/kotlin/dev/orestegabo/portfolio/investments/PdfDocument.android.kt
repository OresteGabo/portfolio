package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal actual val nativePdfCaptureProtectionAvailable = true

@Composable
internal actual fun rememberPdfDocumentLoader(): suspend (ByteArray) -> PdfDocumentPages {
    val context = LocalContext.current
    return remember(context) { { bytes -> withContext(Dispatchers.IO) { AndroidPdfDocument(bytes, context.cacheDir) } } }
}

class AndroidPdfDocument(bytes: ByteArray, cache: File) : PdfDocumentPages {
    private val lock = Any()
    private val file: File
    private val descriptor: ParcelFileDescriptor
    private val renderer: PdfRenderer
    private var closed = false
    override val pageCount: Int
    init {
        val directory = File(cache, "pdf-preview").apply { check(mkdirs() || isDirectory) }
        directory.listFiles()?.filter { it.name.startsWith("document-") && it.extension == "pdf" }?.forEach { it.delete() }
        file = File.createTempFile("document-", ".pdf", directory)
        var fd: ParcelFileDescriptor? = null
        var pdf: PdfRenderer? = null
        try {
            file.writeBytes(bytes)
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdf = PdfRenderer(fd)
            require(pdf.pageCount in 1..500)
            descriptor = fd; renderer = pdf; pageCount = pdf.pageCount
        } catch (failure: Exception) {
            pdf?.close(); fd?.close(); file.delete(); throw failure
        }
    }
    override suspend fun renderPage(index: Int, width: Int, region: PdfPageRegion): ImageBitmap = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(!closed)
            renderer.openPage(index).use { page ->
                val (w, h) = pdfPageRenderSize(width, page.width.toDouble(), page.height.toDouble())
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    val matrix = Matrix().apply {
                        setScale((w / (page.width * region.width)).toFloat(), (h / (page.height * region.height)).toFloat())
                        postTranslate((-region.left * w / region.width).toFloat(), (-region.top * h / region.height).toFloat())
                    }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                } catch (failure: Exception) { bitmap.recycle(); throw failure }
            }
        }
    }
    override fun close() = synchronized(lock) {
        if (!closed) { closed = true; try { renderer.close() } finally { descriptor.close(); file.delete() } }
    }
}

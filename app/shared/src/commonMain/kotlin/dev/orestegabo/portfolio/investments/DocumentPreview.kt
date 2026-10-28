package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

internal data class DocumentPreview(val source: JsonRecord, val title: String, val fileName: String, val bytes: ByteArray? = null)

/** Native page rendering only: no remote viewer, JavaScript, public URL or document upload. */
interface PdfDocumentPages {
    val pageCount: Int
    suspend fun renderPage(index: Int, width: Int, region: PdfPageRegion = PdfPageRegion()): ImageBitmap
    fun close()
}
@Composable internal expect fun rememberPdfDocumentLoader(): suspend (ByteArray) -> PdfDocumentPages
internal expect val nativePdfCaptureProtectionAvailable: Boolean

/** Normalized visible rectangle, measured from the displayed page's top left. */
data class PdfPageRegion(val left: Double = 0.0, val top: Double = 0.0, val width: Double = 1.0, val height: Double = 1.0) {
    init {
        require(listOf(left, top, width, height).all { it.isFinite() })
        require(left >= 0.0 && top >= 0.0 && width > 0.0 && height > 0.0)
        require(left + width <= 1.000001 && top + height <= 1.000001)
    }
}

internal fun pdfVisibleRegion(zoom: Float, offsetX: Float, offsetY: Float, width: Int, height: Int): PdfPageRegion {
    val scale = zoom.coerceIn(1f, 6f).toDouble()
    val fraction = 1.0 / scale
    return PdfPageRegion(
        ((1.0 - fraction) / 2 - offsetX / (width.coerceAtLeast(1) * scale)).coerceIn(0.0, 1.0 - fraction),
        ((1.0 - fraction) / 2 - offsetY / (height.coerceAtLeast(1) * scale)).coerceIn(0.0, 1.0 - fraction),
        fraction, fraction)
}

/** Bound native page allocations, including malformed/extreme page dimensions. */
internal fun pdfPageRenderSize(widthHint: Int, pageWidth: Double, pageHeight: Double): Pair<Int, Int> {
    require(pageWidth.isFinite() && pageHeight.isFinite() && pageWidth > 0 && pageHeight > 0) { "This PDF could not be displayed." }
    val ratio = pageHeight / pageWidth
    require(ratio.isFinite() && ratio > 0) { "This PDF could not be displayed." }
    val width = widthHint.coerceIn(128, 2048).toDouble()
    val height = (width * ratio).coerceAtLeast(1.0)
    val factor = minOf(1.0, kotlin.math.sqrt(3_000_000.0 / (width * height)), 4096.0 / maxOf(width, height))
    return (width * factor).toInt().coerceAtLeast(1) to (height * factor).toInt().coerceAtLeast(1)
}

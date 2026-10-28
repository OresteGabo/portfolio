@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.useContents
import kotlinx.cinterop.autoreleasepool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreGraphics.*
import platform.Foundation.NSRecursiveLock
import platform.PDFKit.PDFDocument
import platform.UIKit.*

internal actual val nativePdfCaptureProtectionAvailable get() = !captureProtectionRequiresMaskedNumbers

@Composable
internal actual fun rememberPdfDocumentLoader(): suspend (ByteArray) -> PdfDocumentPages = remember {
    { bytes -> withContext(Dispatchers.Default) { IosPdfDocument(bytes) } }
}

internal class IosPdfDocument(bytes: ByteArray) : PdfDocumentPages {
    private val lock = NSRecursiveLock()
    private var document: PDFDocument? = PDFDocument(data = bytes.toData())
    override val pageCount: Int
    init {
        val pdf = requireNotNull(document)
        require(!pdf.isLocked() && pdf.pageCount in 1uL..500uL)
        pageCount = pdf.pageCount.toInt()
    }
    override suspend fun renderPage(index: Int, width: Int, region: PdfPageRegion): ImageBitmap = withContext(Dispatchers.Default) { autoreleasepool {
        lock.lock()
        try {
            val page = requireNotNull(document?.pageAtIndex(index.toULong()))
            val ref = requireNotNull(page.pageRef)
            val bounds = CGPDFPageGetBoxRect(ref, kCGPDFCropBox)
            val rotated = kotlin.math.abs(CGPDFPageGetRotationAngle(ref)) % 180 == 90
            val (pageWidth, pageHeight) = bounds.useContents {
                if (rotated) size.height to size.width else size.width to size.height
            }
            val (w, h) = pdfPageRenderSize(width, pageWidth * region.width, pageHeight * region.height)
            val format = UIGraphicsImageRendererFormat().apply { scale = 1.0; opaque = true }
            val renderer = UIGraphicsImageRenderer(size = CGSizeMake(w.toDouble(), h.toDouble()), format = format)
            val thumbnail = renderer.imageWithActions { output ->
                val context = output!!.CGContext
                val target = CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble())
                UIColor.whiteColor.setFill()
                CGContextFillRect(context, target)
                CGContextTranslateCTM(context, 0.0, h.toDouble())
                CGContextScaleCTM(context, 1.0, -1.0)
                // CoreGraphics' page transform can retain 1 point per pixel when the target
                // is larger than the PDF. Explicitly scale from page points into output pixels.
                // Normalize rotation/crop origin in page-point space first.
                val fullWidth = w / region.width
                val fullHeight = h / region.height
                CGContextTranslateCTM(context, -region.left * fullWidth, -(1.0 - region.top - region.height) * fullHeight)
                CGContextScaleCTM(context, fullWidth / pageWidth, fullHeight / pageHeight)
                CGContextConcatCTM(context, CGPDFPageGetDrawingTransform(ref, kCGPDFCropBox,
                    CGRectMake(0.0, 0.0, pageWidth, pageHeight), 0, true))
                CGContextDrawPDFPage(context, ref)
            }
            val encoded = requireNotNull(UIImagePNGRepresentation(thumbnail)).toBytes()
            Image.makeFromEncoded(encoded).toComposeImageBitmap()
        } finally { lock.unlock() }
    } }
    override fun close() {
        lock.lock()
        try { document = null } finally { lock.unlock() }
    }
}

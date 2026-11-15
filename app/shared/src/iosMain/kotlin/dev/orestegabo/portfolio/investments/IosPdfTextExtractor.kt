@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.design.StructColors
import dev.orestegabo.portfolio.design.uiColor
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.PDFKit.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.*
import platform.Vision.*

/**
 * Native PDF interpretation boundary: validates the PDF, extracts text and uses Vision when needed.
 * Does not identify investment formats, calculate financial values, execute JS or persist records.
 * Each call owns its document, page text and OCR counters; progress belongs only to that call.
 * Cancellation and dispatcher ownership remain with the calling engine/import operation.
 */
internal class IosPdfTextExtractor {
    suspend fun extract(bytes: ByteArray, onProgress: (String) -> Unit = {}): Pair<String, Boolean> = withContext(Dispatchers.Default) {
        validatePdfEnvelope(bytes)
        val document = PDFDocument(data = bytes.toData())
        require(!document.isLocked() && document.pageCount in 1uL..MAX_PDF_PAGES.toULong()) { PDF_READ_ERROR }
        validateDocument(document)
        var usedOcr = false
        var scans = 0
        val texts = mutableListOf<String>()
        for (index in 0uL until document.pageCount) {
            ensureActive()
            withContext(Dispatchers.Main) { onProgress("Reading page ${index + 1u} of ${document.pageCount}…") }
            val page = document.pageAtIndex(index) ?: error(PDF_READ_ERROR)
            require(page.annotations.all { annotation ->
                annotation is PDFAnnotation && annotation.type == "Widget" && annotation.widgetFieldType == PDFAnnotationWidgetSubtypeSignature && annotation.action == null && annotation.valueForAnnotationKey("AA") == null
            }) { PDF_READ_ERROR }
            page.pageRef?.let { pageRef -> require(!hasKey(CGPDFPageGetDictionary(pageRef), "AA")) { PDF_READ_ERROR } }
            var text = page.string.orEmpty()
            // iOS 18 PDFKit can return an entire statement's date/type columns
            // before its opening balance. Rebuild this recognized table layout
            // from word selections, without OCR or changes to financial parsing.
            if (Regex("\\bBK\\s+CAPITAL\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
                Regex("\\bCLIENT\\s+STATEMENT\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                require(text.length <= MAX_PDF_TEXT && page.numberOfCharacters <= MAX_PDF_TEXT.toULong()) { PDF_READ_ERROR }
                require(page.numberOfCharacters.toInt() == text.length) { PDF_READ_ERROR }
                val blocks = Regex("\\S+").findAll(text).map { word ->
                    val selection = page.selectionForRange(NSMakeRange(word.range.first.toULong(), word.value.length.toULong()))
                        ?: error(PDF_READ_ERROR)
                    selection.boundsForPage(page).useContents {
                        require(origin.x.isFinite() && origin.y.isFinite() && size.height.isFinite() && size.height > 0) { PDF_READ_ERROR }
                        OcrTextBlock(word.value, origin.x, origin.y, size.height)
                    }
                }.toList()
                text = layoutOcrText(blocks)
            }
            if (text.count { !it.isWhitespace() } < 80) {
                require(++scans <= MAX_OCR_PAGES) { PDF_READ_ERROR }
                usedOcr = true
                withContext(Dispatchers.Main) { onProgress("Recognizing page ${index + 1u} on this device…") }
                text = recognize(page)
            }
            require(text.length <= MAX_PDF_TEXT && texts.sumOf { it.length } + text.length <= MAX_PDF_TEXT) { PDF_READ_ERROR }
            texts += text
        }
        texts.joinToString("\n") to usedOcr
    }
    private fun validateDocument(document: PDFDocument) {
        val catalog = document.documentRef?.let { CGPDFDocumentGetCatalog(it) } ?: error(PDF_READ_ERROR)
        require(!hasKey(catalog, "OpenAction") && !hasKey(catalog, "AA")) { PDF_READ_ERROR }
        dictionary(catalog, "Names")?.let { names -> require(!hasKey(names, "JavaScript") && !hasKey(names, "EmbeddedFiles")) { PDF_READ_ERROR } }
        dictionary(catalog, "AcroForm")?.let { require(!hasKey(it, "XFA")) { PDF_READ_ERROR } }
    }
    private fun hasKey(dictionary: CGPDFDictionaryRef?, key: String): Boolean = memScoped {
        dictionary != null && CGPDFDictionaryGetObject(dictionary, key, alloc<CGPDFObjectRefVar>().ptr)
    }
    private fun dictionary(parent: CGPDFDictionaryRef?, key: String): CGPDFDictionaryRef? = memScoped {
        if (parent == null) return@memScoped null
        val result = alloc<CGPDFDictionaryRefVar>()
        if (CGPDFDictionaryGetDictionary(parent, key, result.ptr)) result.value else null
    }
    private fun recognize(page: PDFPage): String = autoreleasepool {
        val bounds = page.boundsForBox(kPDFDisplayBoxMediaBox)
        val dimensions = bounds.useContents { size.width to size.height }
        require(dimensions.first > 0 && dimensions.second > 0) { PDF_READ_ERROR }
        val scale = minOf(3.0, kotlin.math.sqrt(8_000_000.0 / (dimensions.first * dimensions.second)), 4096 / dimensions.first, 4096 / dimensions.second)
        val format = UIGraphicsImageRendererFormat().apply { this.scale = 1.0; opaque = true }
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(dimensions.first * scale, dimensions.second * scale), format = format)
        val image = renderer.imageWithActions { output ->
            val cg = output!!.CGContext
            StructColors.Document.paper.uiColor().setFill()
            CGContextFillRect(cg, CGRectMake(0.0, 0.0, dimensions.first * scale, dimensions.second * scale))
            CGContextTranslateCTM(cg, 0.0, dimensions.second * scale)
            CGContextScaleCTM(cg, scale, -scale)
            page.drawWithBox(kPDFDisplayBoxMediaBox, toContext = cg)
        }
        val request = VNRecognizeTextRequest(completionHandler = null).apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            regionOfInterest = CGRectMake(0.0, 0.0, 1.0, 1.0)
            minimumTextHeight = 0.005f
            recognitionLanguages = listOf("en-US")
            usesLanguageCorrection = false
        }
        val handler = VNImageRequestHandler(data = requireNotNull(UIImagePNGRepresentation(image)), options = emptyMap<Any?, Any?>())
        require(handler.performRequests(listOf(request), error = null)) { PDF_READ_ERROR }
        val blocks = request.results.orEmpty().filterIsInstance<VNRecognizedTextObservation>().mapNotNull { observation ->
            val text = (observation.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string ?: return@mapNotNull null
            observation.boundingBox.useContents { OcrTextBlock(text, origin.x, origin.y, size.height) }
        }
        layoutOcrText(blocks).also { require(it.isNotBlank()) { PDF_READ_ERROR } }
    }
}

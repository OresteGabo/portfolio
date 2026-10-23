@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.JavaScriptCore.JSContext
import platform.PDFKit.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.*
import platform.Vision.*

/** Native PDFKit/Vision extraction; the financial and field-parsing rules are the shared JS bundle. */
class IosInvestmentEngine : InvestmentEngine {
    override var onProgress: (String) -> Unit = {}
    private var context: JSContext? = null
    private var destroyed = false
    private val mutex = Mutex()
    private var job: Job? = null
    override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = mutex.withLock {
        check(!destroyed) { "The local engine is closed." }
        coroutineScope {
            job = coroutineContext[Job]
            try {
                withTimeout(150_000) {
                    if (command == "pdf") {
                        val (text, ocr) = extract(requireNotNull(pdf))
                        evaluate("PortfolioLogic.parse(${JsonRecord.quote(text)},$ocr)", pdfImport = true)
                    } else {
                        require(command == "projection") { "Unknown investment operation." }
                        evaluate("PortfolioLogic.project($payload)")
                    }
                }
            } finally { job = null }
        }
    }
    private suspend fun evaluate(expression: String, pdfImport: Boolean = false): JsonRecord = withContext(Dispatchers.Main) {
        val js = context ?: JSContext().also {
            it.evaluateScript(readSharedAsset("engine/logic.js").decodeToString())
            context = it
        }
        js.exception = null
        // Keep only our typed error code, never JavaScript errors containing recognized document text.
        val value = js.evaluateScript("""(function(){try{return JSON.stringify({result:($expression)})}catch(e){return JSON.stringify({errorCode:typeof e.code==='string'?e.code:'operation_failed'})}})()""")
        val generic = if (pdfImport) "Could not read this purchase PDF. Try a clearer copy or enter the purchase manually."
            else "The investment operation could not be completed. Check the input and try again."
        check(js.exception == null && value != null && !value.isUndefined()) { generic }
        val envelope = JsonRecord(value.toString())
        envelope.optJSONObject("result") ?: error(if (pdfImport && envelope.text("errorCode") == "not_bond_result")
            "Could not recognize a bond result or share purchase. Try a clearer PDF or enter the purchase manually." else generic)
    }
    private suspend fun extract(bytes: ByteArray): Pair<String, Boolean> = withContext(Dispatchers.Default) {
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
                annotation is PDFAnnotation && annotation.type == "Widget" && annotation.widgetFieldType == "Sig" && annotation.action == null && annotation.valueForAnnotationKey("AA") == null
            }) { PDF_READ_ERROR }
            page.pageRef?.let { pageRef -> require(!hasKey(CGPDFPageGetDictionary(pageRef), "AA")) { PDF_READ_ERROR } }
            var text = page.string.orEmpty()
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
            UIColor.whiteColor.setFill()
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
    override fun cancel() { job?.cancel() }
    override fun destroy() { destroyed = true; cancel(); context = null }
}

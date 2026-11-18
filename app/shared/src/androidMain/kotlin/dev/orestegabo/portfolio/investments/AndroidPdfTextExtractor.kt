package dev.orestegabo.portfolio.investments

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import dev.orestegabo.portfolio.design.StructColors
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/** PDFBox reads text/structure; Android renders scans; bundled ML Kit recognizes them locally.
 * No JavaScript, browser, cloud recognition, document logging or named plaintext PDF persists.
 */
internal class AndroidPdfTextExtractor(private val context: Context) {
    suspend fun extract(bytes: ByteArray, progress: (String) -> Unit): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        validatePdfEnvelope(bytes)
        PDFBoxResourceLoader.init(context)
        val texts = mutableListOf<String>()
        var characters = 0
        var scans = 0
        var renderer: PdfRenderer? = null
        var fd: ParcelFileDescriptor? = null
        val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
        var usedRecognizer = false
        try {
            PDDocument.load(ByteArrayInputStream(bytes), MemoryUsageSetting.setupMainMemoryOnly(32L * 1024 * 1024)).use { document ->
                require(!document.isEncrypted && document.numberOfPages in 1..MAX_PDF_PAGES) { PDF_READ_ERROR }
                validateStructure(document)
                for (index in 0 until document.numberOfPages) {
                    ensureActive()
                    progress("Reading page ${index + 1} of ${document.numberOfPages}…")
                    val stripper = PDFTextStripper().apply { sortByPosition = true; startPage = index + 1; endPage = index + 1 }
                    var text = stripper.getText(document)
                    if (text.count { !it.isWhitespace() } < 80) {
                        require(++scans <= MAX_OCR_PAGES) { PDF_READ_ERROR }
                        if (renderer == null) {
                            fd = openUnlinkedPdf(bytes)
                            renderer = PdfRenderer(requireNotNull(fd))
                            require(renderer!!.pageCount == document.numberOfPages) { PDF_READ_ERROR }
                        }
                        progress("Recognizing page ${index + 1} on this device…")
                        val bitmap = renderer!!.openPage(index).use { page ->
                            require(page.width > 0 && page.height > 0) { PDF_READ_ERROR }
                            val scale = minOf(3.0, sqrt(8_000_000.0 / (page.width.toDouble() * page.height)), 4096.0 / page.width, 4096.0 / page.height)
                            Bitmap.createBitmap(max(1, (page.width * scale).toInt()), max(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888).also { image ->
                                image.eraseColor(StructColors.Document.paper.toArgb())
                                try { page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                                catch (failure: Throwable) { image.recycle(); throw failure }
                            }
                        }
                        usedRecognizer = true
                        // ML Kit's task has no cancellation token. Own the bitmap until it completes,
                        // even if the caller cancels, then honor cancellation before accepting output.
                        val result = try {
                            withContext(NonCancellable) {
                                suspendCancellableCoroutine { continuation ->
                                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                                        .addOnSuccessListener { continuation.resumeWith(Result.success(it)) }
                                        .addOnFailureListener { continuation.resumeWith(Result.failure(IllegalStateException(PDF_READ_ERROR))) }
                                }
                            }
                        } finally { bitmap.recycle() }
                        ensureActive()
                        val words = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }
                        text = layoutOcrText(words.mapNotNull { word -> word.boundingBox?.let { box -> OcrTextBlock(word.text, box.left.toDouble(), -box.bottom.toDouble(), box.height().toDouble()) } })
                    }
                    require(text.length <= MAX_PDF_TEXT && characters + text.length <= MAX_PDF_TEXT) { PDF_READ_ERROR }
                    texts.add(text); characters += text.length
                }
            }
            texts.joinToString("\n") to (scans > 0)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw IllegalArgumentException(PDF_READ_ERROR) }
        finally {
            renderer?.close(); fd?.close()
            if (usedRecognizer) recognizer.close()
        }
    }
    private fun openUnlinkedPdf(bytes: ByteArray): ParcelFileDescriptor {
        val directory = File(context.cacheDir, "native-pdf-import").apply { check(mkdirs() || isDirectory) }
        val file = File.createTempFile("import-", ".pdf", directory)
        var descriptor: ParcelFileDescriptor? = null
        try {
            FileOutputStream(file).use { output ->
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                check(file.delete()) { PDF_READ_ERROR }
                output.write(bytes)
            }
            return requireNotNull(descriptor)
        } catch (failure: Throwable) { descriptor?.close(); throw failure }
        finally { file.delete() }
    }
    private fun validateStructure(document: PDDocument) {
        fun key(name: String) = COSName.getPDFName(name)
        fun passive(dictionary: COSDictionary) = !dictionary.containsKey(key("A")) && !dictionary.containsKey(key("AA"))
        val catalog = document.documentCatalog.cosObject
        require(!catalog.containsKey(key("OpenAction")) && !catalog.containsKey(key("AA"))) { PDF_READ_ERROR }
        (catalog.getDictionaryObject(key("Names")) as? COSDictionary)?.let { require(!it.containsKey(key("JavaScript")) && !it.containsKey(key("EmbeddedFiles"))) { PDF_READ_ERROR } }
        val form = document.documentCatalog.acroForm
        require(form == null || !form.cosObject.containsKey(key("XFA"))) { PDF_READ_ERROR }
        val signatures = mutableSetOf<COSDictionary>()
        form?.fieldTree?.forEach { field ->
            require(passive(field.cosObject)) { PDF_READ_ERROR }
            when (field) {
                is PDSignatureField -> { require(field.widgets.isNotEmpty()) { PDF_READ_ERROR }; field.widgets.forEach { signatures.add(it.cosObject) } }
                is PDNonTerminalField -> Unit
                else -> throw IllegalArgumentException(PDF_READ_ERROR)
            }
            require(signatures.size <= 500) { PDF_READ_ERROR }
        }
        val observed = mutableSetOf<COSDictionary>()
        document.pages.forEach { page ->
            require(!page.cosObject.containsKey(key("AA"))) { PDF_READ_ERROR }
            page.annotations.forEach { annotation ->
                require(annotation.subtype == "Widget" && annotation.cosObject in signatures && passive(annotation.cosObject)) { PDF_READ_ERROR }
                observed.add(annotation.cosObject)
            }
        }
        require(observed == signatures) { PDF_READ_ERROR }
    }
}

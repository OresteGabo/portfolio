package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ReceivedPdf(val id: Long, val bytes: ByteArray, val fileName: String)

/** Main-thread mailbox: retain one external file through onboarding/sign-in, never overwrite it. */
open class PdfImportInbox {
    var pending: ReceivedPdf? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    private var sequence = 0L
    private val seen = ArrayDeque<String>()

    fun offer(bytes: ByteArray, deliveryId: String, fileName: String = "Shared PDF"): Boolean {
        if (deliveryId in seen) return false
        if (pending != null) { reject("Finish the waiting PDF before sharing another one."); return false }
        require(bytes.size in 1..MAX_PDF_BYTES) { "Choose a PDF of up to 10 MB." }
        require(bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-") { "The selected file is not a PDF." }
        seen.addLast(deliveryId)
        if (seen.size > 32) seen.removeFirst()
        error = null
        pending = ReceivedPdf(++sequence, bytes, fileName.take(180))
        return true
    }
    fun take(id: Long): ReceivedPdf? = pending?.takeIf { it.id == id }?.also { pending = null }
    fun dismiss() { pending = null; error = null }
    fun reject(message: String) { error = safeErrorMessage(message) }
    fun dismissError() { error = null }
}

object NativePdfInbox : PdfImportInbox()

/** Reuse the exact picker-import validation/parser, without asking for a second file selection. */
internal class ReceivedPdfActions(private val original: NativeActions, private val bytes: ByteArray, override val pickedPdfName: String? = null) : NativeActions by original {
    override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return bytes }
}

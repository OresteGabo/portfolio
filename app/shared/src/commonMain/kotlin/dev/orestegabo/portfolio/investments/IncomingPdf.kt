package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ReceivedPdf(val id: Long, val bytes: ByteArray, val fileName: String)

/** Main-thread mailbox: retain one external file through onboarding/sign-in, never overwrite it.
 * An accepted offer transfers buffer ownership: callers must not reuse/mutate that buffer.
 * Dismiss clears it; take transfers ownership to the normal import workflow. Rejected offers retain
 * caller ownership and leave both the supplied buffer and any pending document untouched.
 */
class PdfImportInbox {
    var pending: ReceivedPdf? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var requiresPreparation: Boolean by mutableStateOf(false)
        private set
    private var sequence = 0L
    private val seen = ArrayDeque<String>()

    fun offer(bytes: ByteArray, deliveryId: String, fileName: String = "Shared PDF", prepareFirst: Boolean = false): Boolean {
        if (deliveryId in seen) return false
        if (pending != null) { reject("Finish the waiting PDF before sharing another one."); return false }
        require(bytes.size in 1..MAX_PDF_BYTES) { "Choose a PDF of up to 10 MB." }
        require(bytes.size >= 5 && bytes.copyOfRange(0, 5).decodeToString() == "%PDF-") { "The selected file is not a PDF." }
        seen.addLast(deliveryId)
        if (seen.size > 32) seen.removeFirst()
        error = null
        requiresPreparation = prepareFirst
        pending = ReceivedPdf(++sequence, bytes, fileName.take(180))
        return true
    }
    fun continueImport(id: Long) { if (pending?.id == id) requiresPreparation = false }
    /** Transfer byte ownership to the import workflow; dismiss must not erase a taken document. */
    fun take(id: Long): ReceivedPdf? = pending?.takeIf { it.id == id && !requiresPreparation }?.also { pending = null }
    fun dismiss() { pending?.bytes?.fill(0); pending = null; requiresPreparation = false; error = null }
    fun reject(message: String) { error = safeErrorMessage(message) }
    fun dismissError() { error = null }
}

/** Stable native/Swift singleton facade; the mailbox owns delivery and preparation state. */
object NativePdfInbox {
    private val inbox = PdfImportInbox()
    val pending: ReceivedPdf? get() = inbox.pending
    val error: String? get() = inbox.error
    val requiresPreparation: Boolean get() = inbox.requiresPreparation

    fun offer(bytes: ByteArray, deliveryId: String, fileName: String = "Shared PDF", prepareFirst: Boolean = false): Boolean =
        inbox.offer(bytes, deliveryId, fileName, prepareFirst)
    fun continueImport(id: Long) = inbox.continueImport(id)
    /** Transfer byte ownership to the import workflow; dismiss must not erase a taken document. */
    fun take(id: Long): ReceivedPdf? = inbox.take(id)
    fun dismiss() = inbox.dismiss()
    fun reject(message: String) = inbox.reject(message)
    fun dismissError() = inbox.dismissError()
}

/** Reuse the exact picker-import validation/parser, without asking for a second file selection. */
internal class ReceivedPdfActions(private val original: NativeActions, private val bytes: ByteArray, override val pickedPdfName: String? = null) : NativeActions by original {
    override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return bytes }
    override suspend fun pickPrivatePdf(onSelected: () -> Unit): ByteArray = pickPdf(onSelected)
}

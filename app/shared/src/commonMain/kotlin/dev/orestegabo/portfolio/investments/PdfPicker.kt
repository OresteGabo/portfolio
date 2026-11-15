package dev.orestegabo.portfolio.investments

/** Selects a PDF for review, without granting export, sharing or other device capabilities.
 * A null result means selection was cancelled. [onSelected] runs when selection completes,
 * before potentially expensive reading; the filename is available after a successful read.
 * Implementations may also supply an already-received document without opening a picker.
 * Reading failures may throw; they must not be reported as a successful empty document.
 * A successful selection reports [onSelected] once. Cancellation does not report selection.
 */
interface PdfPicker {
    val pickedPdfName: String? get() = null
    suspend fun pickPdf(onSelected: () -> Unit = {}): ByteArray?

    /** Avoid optional export/cache copies made by ordinary selection.
     * Implementations with no such copies can use the default path.
     */
    suspend fun pickPrivatePdf(onSelected: () -> Unit = {}): ByteArray? = pickPdf(onSelected)
}

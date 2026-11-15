package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*

/** Preview byte ownership, validation and stale-request protection, independent of navigation/session state. */
internal class PortfolioDocumentViewer(
    private val readAccountPdf: suspend (String) -> ByteArray,
    private val readGuestPdf: suspend (String) -> ByteArray,
    private val readAnnualReport: suspend (Int) -> ByteArray,
) {
    val thumbnails = DocumentThumbnailCache()
    var documentPreview by mutableStateOf<DocumentPreview?>(null); private set
    private var loadingDocumentPreview = false
    private var documentPreviewRequest = 0L
    data class GuestRequest(val document: JsonRecord, val preview: DocumentPreview, val revision: Long)

    fun beginGuest(document: JsonRecord): GuestRequest {
        val request = ++documentPreviewRequest
        val preview = DocumentPreview(document, document.text("label").ifBlank { "Local PDF" }, document.text("originalFileName"))
        documentPreview = preview
        return GuestRequest(document, preview, request)
    }
    suspend fun loadGuest(request: GuestRequest, isGuest: () -> Boolean) {
        val bytes = readGuestPdf(request.document.text("id"))
        if (isGuest() && documentPreviewRequest == request.revision && documentPreview != null)
            documentPreview = request.preview.copy(bytes = bytes)
        else bytes.fill(0)
    }
    suspend fun openAccount(document: JsonRecord) {
        val year = document.optInt("annualReportYear")
        val title = if (year > 0) "${localized("Annual report")} · $year" else document.text("label").ifBlank { document.text("originalFileName").ifBlank { "PDF" } }
        val preview = DocumentPreview(document, title, importDisplayName(document.text("originalFileName").ifBlank { "investment.pdf" }))
        val request = ++documentPreviewRequest
        documentPreview = preview
        loadingDocumentPreview = true
        try {
            val data = if (year > 0) readAnnualReport(year) else {
                val path = document.text("downloadUrl")
                require(path.matches(Regex("/api/bonds/documents/[A-Za-z0-9_-]+"))) { "Invalid document URL." }
                readAccountPdf(path)
            }
            require(data.size in 5..24 * 1024 * 1024 && data.copyOfRange(0, 5).decodeToString() == "%PDF-") { "The server did not return a PDF." }
            if (documentPreviewRequest == request && documentPreview != null) documentPreview = preview.copy(bytes = data)
        } finally { loadingDocumentPreview = false }
    }
    fun close(isGuest: Boolean, cancelGuest: () -> Unit, cancelAccount: () -> Unit) {
        documentPreviewRequest++
        if (isGuest) {
            documentPreview?.bytes?.fill(0)
            if (documentPreview != null && documentPreview?.bytes == null) cancelGuest()
        }
        if (loadingDocumentPreview) cancelAccount()
        documentPreview = null
    }
    fun release() { documentPreview = null }
    fun clear() { thumbnails.clear(); documentPreviewRequest++; documentPreview = null; loadingDocumentPreview = false }
}

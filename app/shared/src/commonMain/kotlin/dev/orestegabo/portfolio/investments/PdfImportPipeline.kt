package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val PDF_PROGRESS_RENDER_MILLIS = 100L

/** Shared selection/parsing lifecycle. Reviews are proposals, not authorized financial writes.
 * Workflow-specific acceptance runs through [validateReview], before selected bytes are published.
 * Callers may instead validate the returned proposal after their own cancellation/ownership checks.
 */
internal suspend fun readInvestmentPdf(
    actions: PdfPicker,
    parser: InvestmentDocumentParser,
    selectedPdf: (ByteArray) -> Unit = {},
    openedPdf: suspend (ByteArray) -> Unit = {},
    progress: (PdfImportProgress?) -> Unit = {},
    timeSource: TimeSource = TimeSource.Monotonic,
    validateReview: (JsonRecord) -> Unit = {},
): JsonRecord? {
    var selectedAt: TimeMark? = null
    fun remaining(minimum: Long) = (minimum - (selectedAt?.elapsedNow()?.inWholeMilliseconds ?: minimum)).coerceAtLeast(0L)
    try {
        val bytes = actions.pickPdf {
            selectedAt = timeSource.markNow()
            progress(PdfImportProgress(PdfImportStage.OPENING))
        } ?: return null
        // Give the UI a frame before parsing can occupy the main thread.
        delay(remaining(PDF_PROGRESS_RENDER_MILLIS))
        require(bytes.size in 1..MAX_PDF_BYTES) { "Choose a PDF of up to 10 MB." }
        openedPdf(bytes)
        progress(PdfImportProgress(PdfImportStage.EXTRACTING))
        parser.onProgress = { progress(PdfImportProgress(PdfImportStage.EXTRACTING, it)) }
        val review = parser.parse(bytes)
        progress(PdfImportProgress(PdfImportStage.CHECKING))
        validateReview(review)
        selectedPdf(bytes)
        return review
    } catch (cancelled: CancellationException) {
        parser.cancel()
        throw cancelled
    } finally {
        parser.onProgress = {}
        try {
            // Fast results and errors must not dismiss feedback before it is visible.
            // Slow imports incur no additional hold; cancellation stays immediate.
            if (selectedAt != null && currentCoroutineContext().isActive) delay(remaining(MIN_PDF_PROGRESS_MILLIS))
        } finally { progress(null) }
    }
}

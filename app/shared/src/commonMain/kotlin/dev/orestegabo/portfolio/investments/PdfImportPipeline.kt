package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val PDF_PROGRESS_RENDER_MILLIS = 100L

/** Shared selection/parsing lifecycle. Reviews are proposals, not authorized financial writes.
 * Workflow-specific acceptance runs through [validateReview], before selected bytes are published.
 * Cancellation is checked across picker/parser/callback boundaries, including non-cooperative adapters.
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
    val context = currentCoroutineContext()
    var selectedAt: TimeMark? = null
    fun remaining(minimum: Long) = (minimum - (selectedAt?.elapsedNow()?.inWholeMilliseconds ?: minimum)).coerceAtLeast(0L)
    try {
        val bytes = actions.pickPdf {
            if (context.isActive) {
                selectedAt = timeSource.markNow()
                progress(PdfImportProgress(PdfImportStage.OPENING))
            }
        } ?: return null
        context.ensureActive()
        // Give the UI a frame before parsing can occupy the main thread.
        delay(remaining(PDF_PROGRESS_RENDER_MILLIS))
        require(bytes.size in 1..MAX_PDF_BYTES) { "Choose a PDF of up to 10 MB." }
        openedPdf(bytes)
        context.ensureActive()
        progress(PdfImportProgress(PdfImportStage.EXTRACTING))
        parser.onProgress = { if (context.isActive) progress(PdfImportProgress(PdfImportStage.EXTRACTING, it)) }
        context.ensureActive()
        val review = parser.parse(bytes)
        context.ensureActive()
        progress(PdfImportProgress(PdfImportStage.CHECKING))
        context.ensureActive()
        validateReview(review)
        context.ensureActive()
        selectedPdf(bytes)
        context.ensureActive()
        return review
    } catch (cancelled: CancellationException) {
        parser.cancel()
        throw cancelled
    } finally {
        parser.onProgress = {}
        try {
            // Fast results and errors must not dismiss feedback before it is visible.
            // Slow imports incur no additional hold; cancellation stays immediate.
            if (selectedAt != null && context.isActive) delay(remaining(MIN_PDF_PROGRESS_MILLIS))
        } finally { progress(null) }
    }
}

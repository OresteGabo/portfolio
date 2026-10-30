package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.TimeMark
import kotlin.time.TimeSource

enum class PdfImportStage(val label: String) {
    OPENING("Opening file"), EXTRACTING("Extracting data"), CHECKING("Checking details"), UPLOADING("Saving PDF"), SAVING("Confirming purchase")
}
const val MIN_PDF_PROGRESS_MILLIS = 1_000L
private const val PDF_PROGRESS_RENDER_MILLIS = 100L

data class PdfImportProgress(val stage: PdfImportStage, val detail: String = "")

/** All entry points use the same native picker, parser and document checks. */
suspend fun readPurchasePdf(
    actions: NativeActions,
    engine: InvestmentEngine,
    bondOnly: Boolean = false,
    expectedStatement: JsonRecord? = null,
    selectedPdf: (ByteArray) -> Unit = {},
    openedPdf: suspend (ByteArray) -> Unit = {},
    progress: (PdfImportProgress?) -> Unit = {},
): JsonRecord? {
    return readPurchasePdf(actions, EngineDocumentParser(engine), bondOnly, expectedStatement, selectedPdf, openedPdf, progress)
}

/** Parser-specific entry point: native or broker adapters can be supplied without a simulation engine. */
suspend fun readPurchasePdf(
    actions: NativeActions,
    parser: InvestmentDocumentParser,
    bondOnly: Boolean = false,
    expectedStatement: JsonRecord? = null,
    selectedPdf: (ByteArray) -> Unit = {},
    openedPdf: suspend (ByteArray) -> Unit = {},
    progress: (PdfImportProgress?) -> Unit = {},
): JsonRecord? {
    var selectedAt: TimeMark? = null
    fun remaining(minimum: Long) = (minimum - (selectedAt?.elapsedNow()?.inWholeMilliseconds ?: minimum)).coerceAtLeast(0L)
    try {
        val bytes = actions.pickPdf {
            selectedAt = TimeSource.Monotonic.markNow()
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
        val publicIssuance = review.text("kind") == "bond-issuance"
        require(!bondOnly || review.text("kind") == "bond" || publicIssuance) { "Use a BNR bond confirmation PDF." }
        require(expectedStatement == null || publicIssuance && expectedStatement.text("kind") == "bond" || statementConfirmationMatches(expectedStatement, review)) {
            "This PDF does not match the statement purchase. Choose its confirmation document."
        }
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

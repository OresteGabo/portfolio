package dev.orestegabo.portfolio.investments

import kotlin.time.TimeSource

enum class PdfImportStage(val label: String) {
    OPENING("Opening file"), EXTRACTING("Extracting data"), CHECKING("Checking details"), UPLOADING("Saving PDF"), SAVING("Confirming purchase")
}
const val MIN_PDF_PROGRESS_MILLIS = 1_000L

data class PdfImportProgress(val stage: PdfImportStage, val detail: String = "")

/** All entry points use the same native picker, parser and document checks. */
suspend fun readPurchasePdf(
    actions: PdfPicker,
    engine: InvestmentEngine,
    bondOnly: Boolean = false,
    expectedStatement: JsonRecord? = null,
    selectedPdf: (ByteArray) -> Unit = {},
    openedPdf: suspend (ByteArray) -> Unit = {},
    progress: (PdfImportProgress?) -> Unit = {},
    timeSource: TimeSource = TimeSource.Monotonic,
): JsonRecord? {
    return readPurchasePdf(actions, EngineDocumentParser(engine), bondOnly, expectedStatement, selectedPdf, openedPdf, progress, timeSource)
}

/** Parser-specific entry point: native or broker adapters can be supplied without a simulation engine. */
suspend fun readPurchasePdf(
    actions: PdfPicker,
    parser: InvestmentDocumentParser,
    bondOnly: Boolean = false,
    expectedStatement: JsonRecord? = null,
    selectedPdf: (ByteArray) -> Unit = {},
    openedPdf: suspend (ByteArray) -> Unit = {},
    progress: (PdfImportProgress?) -> Unit = {},
    timeSource: TimeSource = TimeSource.Monotonic,
): JsonRecord? {
    return readInvestmentPdf(actions, parser, selectedPdf, openedPdf, progress, timeSource) { review ->
        validatePurchasePdfReview(review, bondOnly, expectedStatement)
    }
}

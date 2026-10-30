package dev.orestegabo.portfolio.investments

/** Local PDF-to-review adapter. Parsing proposes records; it never persists a financial transaction.
 * Keep broker detection in adapters and financial validation in the application/backend boundaries.
 * Adapters enforce document envelope/page/text/OCR limits before extraction. An instance serves one active import.
 * JsonRecord preserves the existing review contract during gradual typed-model migration.
 */
interface InvestmentDocumentParser {
    var onProgress: (String) -> Unit
    suspend fun parse(pdf: ByteArray): JsonRecord
    fun cancel()
}

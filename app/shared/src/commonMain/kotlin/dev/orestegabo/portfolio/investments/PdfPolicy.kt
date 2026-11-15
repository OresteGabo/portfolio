package dev.orestegabo.portfolio.investments

const val MAX_PDF_BYTES = 10 * 1024 * 1024
const val MAX_PDF_PAGES = 10
const val MAX_OCR_PAGES = 5
const val MAX_PDF_TEXT = 200_000
const val PDF_READ_ERROR = "Could not read this purchase PDF. Use a BNR result, share purchase contract or BK Capital statement, up to 10 MB and 10 pages."
private val PDF_HEADER = Regex("^%PDF-(?:1\\.[0-7]|2\\.0)[\\r\\n\\t ]")

fun validatePdfEnvelope(bytes: ByteArray) {
    require(bytes.size in 1..MAX_PDF_BYTES) { PDF_READ_ERROR }
    var end = bytes.size
    while (end > 0) {
        // PDF whitespace plus NUL padding; do not accept other control bytes after %%EOF.
        when (bytes[end - 1].toInt()) {
            0, 9, 10, 12, 13, 32 -> end--
            else -> break
        }
    }
    require(PDF_HEADER.containsMatchIn(bytes.decodeToString(endIndex = minOf(16, bytes.size)))) { PDF_READ_ERROR }
    require(bytes.decodeToString(startIndex = (end - 1024).coerceAtLeast(0), endIndex = end).endsWith("%%EOF")) { PDF_READ_ERROR }
}

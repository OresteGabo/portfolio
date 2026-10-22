package dev.orestegabo.portfolio.investments

const val MAX_PDF_BYTES = 10 * 1024 * 1024
const val MAX_PDF_PAGES = 10
const val MAX_OCR_PAGES = 5
const val MAX_PDF_TEXT = 200_000
const val PDF_READ_ERROR = "Could not read this purchase PDF. Use a BNR result or RSE purchase contract note, up to 10 MB and 10 pages. You can enter the purchase manually."
fun validatePdfEnvelope(bytes: ByteArray) {
    require(bytes.size in 1..MAX_PDF_BYTES) { PDF_READ_ERROR }
    var end = bytes.size
    while (end > 0 && bytes[end - 1].toInt() in listOf(0, 9, 10, 12, 13, 32)) end--
    require(Regex("^%PDF-(?:1\\.[0-7]|2\\.0)[\\r\\n\\t ]").containsMatchIn(bytes.take(16).toByteArray().decodeToString())) { PDF_READ_ERROR }
    require(bytes.copyOfRange((end - 1024).coerceAtLeast(0), end).decodeToString().endsWith("%%EOF")) { PDF_READ_ERROR }
}

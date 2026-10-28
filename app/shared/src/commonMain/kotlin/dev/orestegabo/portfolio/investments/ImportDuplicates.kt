package dev.orestegabo.portfolio.investments

/** Metadata narrows the download check; it never establishes that two PDFs are identical. */
internal fun duplicatePdfCandidate(document: JsonRecord, review: JsonRecord, fileName: String): Boolean {
    fun normalized(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
    val name = document.text("originalFileName")
    if (name.isNotBlank() && normalized(importDisplayName(name)) == normalized(importDisplayName(fileName))) return true
    val fields = review.optJSONObject("fields") ?: review
    val category = document.text("category").lowercase()
    val compatible = when (review.text("kind")) {
        "statement" -> category.contains("statement")
        "equity" -> category.contains("share") || category.contains("contract") || category.contains("equity")
        "bond-issuance" -> category.contains("issuance")
        "bond" -> category.contains("bond") && !category.contains("issuance")
        else -> false
    }
    if (!compatible) return false
    val dates = (listOf("purchaseDate", "tradeDate", "settlementDate", "issuanceDate").map { fields.text(it).take(10) } + review.text("toDate").take(10))
        .filter(String::isNotBlank)
    if (document.text("documentDate").take(10).takeIf(String::isNotBlank) in dates) return true
    val instrument = normalized(document.text("instrumentName"))
    return instrument.isNotBlank() && listOf("bondName", "securityName").any { normalized(fields.text(it)) == instrument }
}

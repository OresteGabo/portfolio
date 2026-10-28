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

/** An existing file alone never proves a personal purchase has been confirmed. */
internal fun completedImportPurchase(document: JsonRecord, review: JsonRecord, records: List<JsonRecord>, targetId: String?): JsonRecord? {
    val id = document.text("id")
    if (id.isBlank()) return null
    val kind = review.text("kind")
    val input = if (kind == "equity") runCatching { importedPurchaseInput(review) }.getOrNull() else null
    return records.singleOrNull { record ->
        val status = record.text("status").lowercase()
        if ((kind != "equity" && status.isBlank()) || status in listOf("submitted", "pending", "cancelled", "canceled", "rejected") ||
            !targetId.isNullOrBlank() && record.text("id") != targetId) false
        else when (kind) {
            "bond" -> record.text("confirmationDocumentId") == id
            "bond-issuance" -> record.text("issuanceDocumentId") == id && record.text("confirmationMethod") == "manual"
            "equity" -> input != null && input.text("contractNote").isNotBlank() &&
                listOf("contractNote", "ticker", "tradeDate", "broker").all { input.text(it).equals(record.text(it), true) } &&
                listOf("shares", "pricePerShare", "netAmountPayable").all { input.optDouble(it) == record.optDouble(it) }
            else -> false
        }
    }
}

internal data class ImportAddedTime(val at: String, val onThisDevice: Boolean)

internal fun firstImportAddedTime(document: JsonRecord?, history: List<ImportReceipt>): ImportAddedTime? {
    fun instant(value: String) = runCatching { kotlin.time.Instant.parse(value) }.getOrNull()
    val serverTime = listOf("createdAt", "uploadedAt").map { document?.text(it).orEmpty() }
        .firstOrNull { instant(it) != null }
    if (serverTime != null) return ImportAddedTime(serverTime, false)
    val id = document?.text("id")?.takeIf(String::isNotBlank) ?: return null
    val original = history.filter { it.documentId == id && it.state == ImportState.SAVED }
        .mapNotNull { entry -> instant(entry.updatedAt)?.let { it to entry.updatedAt } }.minByOrNull { it.first }
    return original?.let { ImportAddedTime(it.second, true) }
}

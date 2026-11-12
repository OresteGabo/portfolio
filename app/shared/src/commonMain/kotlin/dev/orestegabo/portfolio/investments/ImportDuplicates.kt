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
            "equity" -> record.text("contractDocumentId") == id && input != null && input.text("contractNote").isNotBlank() &&
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

/** A public auction is identified by its security and settlement, not by PDF encoding or uploader. */
internal fun sharedIssuanceDocument(review: JsonRecord, documents: List<JsonRecord>): JsonRecord? {
    if (review.text("kind") != "bond-issuance") return null
    val incoming = runCatching { importedIssuanceInput(review) }.getOrNull() ?: return null
    fun security(p: JsonRecord) = p.text("isin").uppercase().filterNot(Char::isWhitespace).removeSuffix("/MN")
    val candidates = documents.filter { doc ->
        val saved = doc.optJSONObject("issuance")
        doc.text("category") == "Bond issuance" && saved != null &&
            security(saved) == security(incoming) && saved.text("settlementDate") == incoming.text("settlementDate")
    }
    require(candidates.size <= 1) { "Multiple saved issuance reports require review." }
    val document = candidates.singleOrNull() ?: return null
    val saved = document.getJSONObject("issuance")
    require(listOf("issuanceDate","maturityDate","firstCouponDate").all { saved.text(it) == incoming.text(it) } &&
        listOf("tenorYears","pricePercent","couponRate","yieldRate","amountOffered","appliedAmount","amountSold","bidCount").all {
            saved.optDouble(it, Double.NaN) == incoming.optDouble(it, Double.NaN)
        } && saved.getJSONArray("couponDates").toString() == incoming.getJSONArray("couponDates").toString()) {
        "This bond issuance is already saved with different figures. Review the existing report."
    }
    return document
}


/** Exact holding identity only. Legacy web records lack confirmationMethod and a personal proof link. */
internal fun legacyBondEvidenceTarget(input: JsonRecord, records: List<JsonRecord>): JsonRecord? = records.filter { saved ->
    saved.text("status") == "active" && saved.text("confirmationMethod").isBlank() &&
        saved.text("confirmationDocumentId").isBlank() && saved.text("isin").isNotBlank() &&
        listOf("instrumentType", "issuer", "currency", "market", "bondName", "isin", "settlementDate", "maturityDate", "firstCouponDate", "broker").all {
            saved.text(it).trim() == input.text(it).trim()
        } && listOf("faceValue", "pricePercent", "couponRate", "tenorYears", "couponFrequency").all {
            saved.optDouble(it, Double.NaN) == input.optDouble(it, Double.NaN)
        }
}.singleOrNull()

/** Absence in the PDF is not evidence of zero acquisition costs or a different tax election. */
internal fun legacyBondEvidenceInput(review: JsonRecord, saved: JsonRecord): JsonRecord {
    val fields = JsonRecord(requireNotNull(review.optJSONObject("fields")).toString())
    for (key in listOf("feesPaid", "accruedInterestPaid", "withholdingTaxRate")) {
        if (!fields.has(key) && saved.has(key)) fields.put(key, saved.optDouble(key, Double.NaN))
    }
    return importedPurchaseInput(JsonRecord(review.toString()).put("fields", fields))
}

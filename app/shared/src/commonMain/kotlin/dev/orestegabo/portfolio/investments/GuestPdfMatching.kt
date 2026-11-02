package dev.orestegabo.portfolio.investments

internal class GuestPdfReviewException(message: String) : IllegalArgumentException(message)

internal fun JsonRecord.hasDocument(id: String): Boolean = optJSONArray("documentIds")?.let { refs ->
    (0 until refs.length()).any { refs.getString(it) == id }
} == true

/** Candidate only: security identity + dates + quantity, never just similar dates/amounts.
 * User must select a candidate; the save boundary also checks financial equality. */
internal fun guestPdfCandidates(body: JsonRecord, kind: String, records: List<JsonRecord>): List<JsonRecord> {
    fun security(p: JsonRecord) = p.text("isin").uppercase().filterNot(Char::isWhitespace).removeSuffix("/MN")
    if (security(body).isBlank()) return emptyList()
    return records.filter { record ->
        security(record) == security(body) && record.text("currency") == body.text("currency") &&
            record.text("settlementDate") == body.text("settlementDate") &&
            (if (kind == "bond") record.text("purchaseDate") == body.text("purchaseDate") && record.optDouble("faceValue") == body.optDouble("faceValue")
            else record.text("tradeDate") == body.text("tradeDate") && record.optDouble("shares") == body.optDouble("shares")) &&
            listOf("broker", "accountReference", "cdsAccountNo", "contractNote").all { key ->
                record.text(key).isBlank() || body.text(key).isBlank() || record.text(key).equals(body.text(key), true)
            }
    }
}

@androidx.compose.runtime.Composable expect fun rememberGuestInvestmentEngine(): InvestmentEngine

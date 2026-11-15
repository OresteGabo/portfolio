package dev.orestegabo.portfolio.investments

import kotlin.time.Instant

/** Privacy-limited receipt schema and restart recovery; no Compose, clock or storage dependency. */
internal fun encodeImportHistory(entries: List<ImportReceipt>): String = JsonList(entries.map(ImportReceipt::json)).toString()

internal fun encodeImportReceipt(receipt: ImportReceipt): JsonRecord = with(receipt) {
    JsonRecord().put("id", id).put("fileName", fileName).put("createdAt", createdAt)
        .put("state", state.name).put("updatedAt", updatedAt).put("kind", kind).put("digest", digest).put("documentId", documentId)
        .put("purchaseId", purchaseId).put("targetId", targetId).put("statementId", statementId)
        .put("error", error).put("outcome", outcome?.name.orEmpty()).put("recorded", recorded).put("missing", missing)
        .put("attempts", attempts).put("events", JsonList(events.map {
            JsonRecord().put("at", it.at).put("state", it.state.name).put("error", it.error)
        }))
}

internal fun decodeImportHistory(raw: String?): List<ImportReceipt> = runCatching {
    if (raw.isNullOrBlank() || raw.length > 4_000_000) return emptyList()
    JsonList(raw).objects().takeLast(1000).mapNotNull { row -> runCatching {
        require(row.text("id").matches(Regex("[a-f0-9]{32}")))
        Instant.parse(row.text("createdAt"))
        val state = ImportState.valueOf(row.text("state"))
        ImportReceipt(row.text("id"), importDisplayName(row.text("fileName")), row.text("createdAt"),
            if (state in listOf(ImportState.READING, ImportState.REVIEW, ImportState.SAVING)) ImportState.INTERRUPTED else state,
            kind = row.text("kind").takeIf { it in listOf("bond", "equity", "statement", "bond-issuance") }.orEmpty(),
            digest = row.text("digest").takeIf { it.matches(Regex("[a-f0-9]{64}")) }.orEmpty(),
            documentId = row.text("documentId").take(120), purchaseId = row.text("purchaseId").take(120),
            targetId = row.text("targetId").take(120), statementId = row.text("statementId").take(120),
            error = row.text("error").takeIf { it.isNotBlank() }?.let(::safeErrorMessage).orEmpty(),
            outcome = runCatching { ImportOutcome.valueOf(row.text("outcome")) }.getOrNull(),
            recorded = row.optInt("recorded").coerceIn(0, 500), missing = row.optInt("missing").coerceIn(0, 500),
            attempts = row.optInt("attempts", 1).coerceIn(1, 10000),
            events = row.rows("events").takeLast(8).mapNotNull { event -> runCatching {
                Instant.parse(event.text("at"))
                val phase = ImportState.valueOf(event.text("state"))
                require(phase in listOf(ImportState.FAILED, ImportState.CANCELLED, ImportState.INTERRUPTED))
                ImportEvent(event.text("at"), phase, event.text("error").takeIf(String::isNotBlank)?.let(::safeErrorMessage).orEmpty())
            }.getOrNull() },
            updatedAt = row.text("updatedAt").takeIf { runCatching { Instant.parse(it) }.isSuccess } ?: row.text("createdAt"))
    }.getOrNull() }.distinctBy { it.id }
}.getOrDefault(emptyList())

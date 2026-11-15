package dev.orestegabo.portfolio.investments

internal enum class ImportState { READING, REVIEW, SAVING, SAVED, EXISTS, FAILED, CANCELLED, INTERRUPTED }
internal enum class ImportOutcome { PURCHASE, ISSUANCE, STATEMENT }
internal data class ImportEvent(val at: String, val state: ImportState, val error: String)
internal data class ImportReceipt(
    val id: String,
    val fileName: String,
    val createdAt: String,
    val state: ImportState,
    val kind: String = "",
    val digest: String = "",
    val documentId: String = "",
    val purchaseId: String = "",
    val targetId: String = "",
    val statementId: String = "",
    val error: String = "",
    val outcome: ImportOutcome? = null,
    val recorded: Int = 0,
    val missing: Int = 0,
    val attempts: Int = 1,
    val events: List<ImportEvent> = emptyList(),
    val updatedAt: String = createdAt,
) {
    fun json() = encodeImportReceipt(this)
}

/** Applies journal retry bookkeeping, without performing storage or UI updates.
 * Other transitions remain accepted: this records workflow outcomes, not financial authorization.
 */
internal fun updateImportReceipt(previous: ImportReceipt, changed: ImportReceipt, at: String): ImportReceipt {
    val next = changed.copy(updatedAt = at)
    return if (previous.state in listOf(ImportState.FAILED, ImportState.CANCELLED, ImportState.INTERRUPTED) &&
        next.state in listOf(ImportState.READING, ImportState.SAVING))
        next.copy(attempts = previous.attempts + 1,
            events = (previous.events + ImportEvent(previous.updatedAt, previous.state, previous.error)).takeLast(8))
    else next
}

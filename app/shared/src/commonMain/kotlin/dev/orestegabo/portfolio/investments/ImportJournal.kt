package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

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
    fun json() = JsonRecord().put("id", id).put("fileName", fileName).put("createdAt", createdAt)
        .put("state", state.name).put("updatedAt", updatedAt).put("kind", kind).put("digest", digest).put("documentId", documentId)
        .put("purchaseId", purchaseId).put("targetId", targetId).put("statementId", statementId)
        .put("error", error).put("outcome", outcome?.name.orEmpty()).put("recorded", recorded).put("missing", missing)
        .put("attempts", attempts).put("events", JsonList(events.map {
            JsonRecord().put("at", it.at).put("state", it.state.name).put("error", it.error)
        }))
}

/** Account-scoped receipts only. PDF bytes, extracted fields, amounts and credentials are never serialized. */
internal class ImportJournal(private val settings: ServerSettings) {
    var entries: List<ImportReceipt> by mutableStateOf(emptyList())
        private set
    var persistenceFailed by mutableStateOf(false)
        private set
    private var owner: String? = null
    fun attach(account: String?) {
        if (account == owner) return
        owner = account
        persistenceFailed = false
        entries = if (account == null) emptyList() else runCatching { decodeImportHistory(settings.readImportHistory(account)) }
            .getOrElse { persistenceFailed = true; emptyList() }
    }
    fun detach() { owner = null; entries = emptyList() }
    fun begin(fileName: String, targetId: String = "", statementId: String = ""): ImportReceipt {
        val entry = ImportReceipt(Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') },
            importDisplayName(fileName), Clock.System.now().toString(), ImportState.READING,
            targetId = targetId, statementId = statementId)
        entries = (entries + entry).takeLast(1000)
        persist()
        return entry
    }
    fun update(id: String, change: (ImportReceipt) -> ImportReceipt) {
        entries = entries.map { previous ->
            if (previous.id != id) previous else {
                val next = change(previous).copy(updatedAt = Clock.System.now().toString())
                if (previous.state in listOf(ImportState.FAILED, ImportState.CANCELLED, ImportState.INTERRUPTED) &&
                    next.state in listOf(ImportState.READING, ImportState.SAVING))
                    next.copy(attempts = previous.attempts + 1,
                        events = (previous.events + ImportEvent(previous.updatedAt, previous.state, previous.error)).takeLast(8))
                else next
            }
        }
        persist()
    }
    fun find(id: String?) = entries.firstOrNull { it.id == id }
    private fun persist() { owner?.let { account ->
        runCatching { settings.writeImportHistory(account, JsonList(entries.map(ImportReceipt::json)).toString()) }
            .onSuccess { persistenceFailed = false }.onFailure { persistenceFailed = true }
    } }
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

internal expect fun importPdfDigest(bytes: ByteArray): String

internal fun importDisplayName(value: String) = value.substringAfterLast('/').substringAfterLast('\\')
    .map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().take(180).ifBlank { "Investment PDF" }

package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import kotlin.time.Clock

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
                updateImportReceipt(previous, change(previous), Clock.System.now().toString())
            }
        }
        persist()
    }
    fun find(id: String?) = entries.firstOrNull { it.id == id }
    private fun persist() { owner?.let { account ->
        runCatching { settings.writeImportHistory(account, encodeImportHistory(entries)) }
            .onSuccess { persistenceFailed = false }.onFailure { persistenceFailed = true }
    } }
}

internal expect fun importPdfDigest(bytes: ByteArray): String

internal fun importDisplayName(value: String) = value.substringAfterLast('/').substringAfterLast('\\')
    .map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().take(180).ifBlank { "Investment PDF" }

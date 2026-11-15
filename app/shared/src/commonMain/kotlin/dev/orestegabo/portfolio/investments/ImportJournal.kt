package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlin.random.Random
import kotlin.time.Clock

/** Account-scoped receipts only. PDF bytes, extracted fields, amounts and credentials are never serialized. */
internal class ImportJournal(private val settings: ServerSettings) {
    var entries: List<ImportReceipt> by mutableStateOf(emptyList())
        private set
    /** In-memory diagnostics only: never serialize/log this cause or show its raw message. */
    var persistenceFailure: Exception? by mutableStateOf(null)
        private set
    val persistenceFailed: Boolean get() = persistenceFailure != null
    private var owner: String? = null
    fun attach(account: String?) {
        if (account == owner) return
        // Do not expose the previous account's receipts if the replacement read is cancelled.
        detach()
        var failure: Exception? = null
        val loaded = if (account == null) emptyList() else try {
            decodeImportHistory(settings.readImportHistory(account))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (storageFailure: Exception) {
            failure = storageFailure
            emptyList()
        }
        // Publish ownership only after a read that was not cancelled.
        owner = account
        entries = loaded
        persistenceFailure = failure
    }
    fun detach() { owner = null; entries = emptyList(); persistenceFailure = null }
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
        try {
            settings.writeImportHistory(account, encodeImportHistory(entries))
            persistenceFailure = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // The optional journal must not erase an otherwise valid import; retry on the next write.
            persistenceFailure = failure
        }
    } }
}

internal expect fun importPdfDigest(bytes: ByteArray): String

internal fun importDisplayName(value: String) = value.substringAfterLast('/').substringAfterLast('\\')
    .map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().take(180).ifBlank { "Investment PDF" }

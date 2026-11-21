package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable

/** Native implementations protect bytes at rest and replace the whole file atomically, or throw.
 * Missing is distinct from unreadable. Never return null on corruption, key loss or access denial. */
interface GuestSnapshotStorage {
    suspend fun read(): ByteArray?
    suspend fun write(bytes: ByteArray)
}
const val GUEST_STORE_MAX_BYTES = 4 * 1024 * 1024

/** Full history, not the server's compact overview. Copies cross the boundary, not mutable cached records. */
data class GuestPortfolio(val portfolio: PortfolioSnapshot, val receipts: List<JsonRecord>, val metadata: JsonRecord, val revision: Int)
interface GuestDocumentStorage {
    suspend fun read(id: String): ByteArray?
    suspend fun write(id: String, bytes: ByteArray)
    suspend fun delete(id: String)
}
internal class GuestDataDeletionException : IllegalStateException("Guest data deletion was interrupted. Retry local storage to finish removing it.")

/** Full local portfolio capability. Production factories supply snapshot and document storage.
 * Ledger and backup operations are required, not optional fallbacks. Invalid input or storage
 * failure may throw; implementations must not silently discard a successful write.
 * Backup bytes are the private archive payload; encryption is owned by the backup workflow.
 */
interface GuestPortfolioRepository : GuestInvestmentImporter {
    suspend fun deleteAll(): GuestPortfolio
    suspend fun setMonthlyTarget(amount: Long): GuestPortfolio
    suspend fun setInvestmentGoal(goal: InvestmentGoal): GuestPortfolio
    suspend fun deleteDocument(id: String): GuestPortfolio
    suspend fun saveLedgerEntry(entry: JsonRecord): GuestPortfolio
    suspend fun deleteLedgerEntry(id: String): GuestPortfolio
    suspend fun exportBackup(): ByteArray
    suspend fun restoreBackup(bytes: ByteArray): GuestPortfolio
    suspend fun saveBond(record: JsonRecord, id: String? = null): GuestPortfolio
    suspend fun saveShare(record: JsonRecord, id: String? = null): GuestPortfolio
    suspend fun deleteBond(id: String): GuestPortfolio
    suspend fun deleteShare(id: String): GuestPortfolio
    suspend fun setReceipt(purchaseId: String, dueDate: String, received: Boolean): GuestPortfolio
    /** Metadata-only compatibility API; new imports use the atomic file + financial-record path. */
    suspend fun saveDocumentReference(record: JsonRecord, id: String? = null): GuestPortfolio
}
@Composable expect fun rememberGuestPortfolioRepository(): GuestPortfolioRepository

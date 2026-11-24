package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/** Optional native capability. Reads/writes require the current origin's successful device unlock.
 * Bytes are protected at rest, excluded from backup, and never contain passwords, cookies or PDFs.
 * Deletion must work while locked so password fallback/sign-out can remove local account data. */
interface ProtectedAccountPortfolio {
    suspend fun readAccountPortfolio(origin: String): ByteArray?
    suspend fun writeAccountPortfolio(origin: String, bytes: ByteArray)
    fun deleteAccountPortfolio(origin: String)
}

internal const val ACCOUNT_PORTFOLIO_MAX_BYTES = 16 * 1024 * 1024
internal fun accountPortfolioStoreName(origin: String) = "account-" + importPdfDigest(origin.encodeToByteArray())

internal data class SavedAccountPortfolio(
    val user: JsonRecord,
    val snapshot: PortfolioSnapshot,
    val savedAt: String,
    val historyComplete: Boolean,
    val ledger: List<JsonRecord>,
    val ledgerAvailable: Boolean,
    val receipts: List<JsonRecord>,
    val couponTrackingAvailable: Boolean,
    val quotes: JsonRecord?,
)

/** Versioned local read model. Authorization still comes from the server; this only permits viewing. */
internal class AccountPortfolioCache(private val storage: ProtectedAccountPortfolio?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    suspend fun read(origin: String, savedSignIn: JsonRecord): SavedAccountPortfolio? {
        val accountId = savedSignIn.text("accountId")
        if (storage == null || accountId.isBlank()) return null
        val bytes = try { storage.readAccountPortfolio(origin) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return null } ?: return null
        try {
            return withContext(dispatcher) {
                require(bytes.size in 1..ACCOUNT_PORTFOLIO_MAX_BYTES)
                val root = JsonRecord(bytes.decodeToString())
                require(root.optInt("version") == 1 && root.text("origin") == origin)
                val user = root.getJSONObject("user")
                require(user.text("id") == accountId && user.text("email").equals(savedSignIn.text("email"), true))
                val savedAt = root.text("savedAt")
                Instant.parse(savedAt) // A device clock correction does not revoke an otherwise valid saved portfolio.
                val data = root.getJSONObject("portfolio")
                fun rows(record: JsonRecord, key: String): List<JsonRecord> {
                    val list = record.getJSONArray(key)
                    val records = list.objects()
                    require(records.size == list.length())
                    return JsonRecord.readOnlyList(records)
                }
                fun rows(key: String) = rows(data, key)
                fun ids(key: String): Set<String>? = data.optJSONArray(key)?.let { list ->
                    (0 until list.length()).map { list.getString(it) }.toSet()
                }
                val snapshot = PortfolioSnapshot(rows("purchases"), rows("holdings"), rows("documents"),
                    ids("recentPurchaseIds"), ids("recentHoldingIds"),
                    data.optJSONArray("monthlyInvestmentTotals")?.let { rows("monthlyInvestmentTotals") },
                    rows("shareOrders"), rows("couponReferences"))
                // A cached role never enables owner/publisher operations without server verification.
                val identity = JsonRecord().put("id", user.text("id")).put("email", user.text("email")).readOnlyCopy()
                SavedAccountPortfolio(identity, snapshot, savedAt, root.optBoolean("historyComplete"),
                    rows(root, "ledger"), root.optBoolean("ledgerAvailable"),
                    rows(root, "receipts"), root.optBoolean("couponTrackingAvailable"),
                    root.optJSONObject("quotes")?.let { EquityQuoteCache.sanitize(it, Clock.System.now()) })
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return null }
        finally { bytes.fill(0) }
    }

    suspend fun write(origin: String, user: JsonRecord, snapshot: PortfolioSnapshot, historyComplete: Boolean,
        ledger: List<JsonRecord>, ledgerAvailable: Boolean, receipts: List<JsonRecord>, couponTrackingAvailable: Boolean,
        quotes: JsonRecord? = null): String? {
        if (storage == null || user.text("id").isBlank() || user.text("email").isBlank()) return null
        val savedAt = Clock.System.now().toString()
        val bytes = withContext(dispatcher) {
            val identity = JsonRecord().put("id", user.text("id")).put("email", user.text("email"))
            val data = JsonRecord().put("purchases", JsonList(snapshot.purchases)).put("holdings", JsonList(snapshot.holdings))
                .put("documents", JsonList(snapshot.documents)).put("shareOrders", JsonList(snapshot.shareOrders))
                .put("couponReferences", JsonList(snapshot.couponReferences))
                .put("recentPurchaseIds", snapshot.recentPurchaseIds?.let(::JsonList))
                .put("recentHoldingIds", snapshot.recentHoldingIds?.let(::JsonList))
                .put("monthlyInvestmentTotals", snapshot.monthlyInvestmentTotals?.let(::JsonList))
            val root = JsonRecord().put("version", 1).put("origin", origin).put("user", identity).put("savedAt", savedAt)
                .put("portfolio", data).put("historyComplete", historyComplete).put("ledger", JsonList(ledger))
                .put("ledgerAvailable", ledgerAvailable).put("receipts", JsonList(receipts))
                .put("couponTrackingAvailable", couponTrackingAvailable)
                .put("quotes", quotes?.let { EquityQuoteCache.sanitize(it, Clock.System.now()) })
            root.toString().encodeToByteArray()
        }
        try {
            require(bytes.size in 1..ACCOUNT_PORTFOLIO_MAX_BYTES)
            storage.writeAccountPortfolio(origin, bytes)
            return savedAt
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return null } // Optional cache failure never rejects a successful online sync.
        finally { bytes.fill(0) }
    }
}

/** Serializes native cache IO. A sign-out racing a blocked file write still removes its result. */
internal class DeviceUnlockedPortfolioStorage(
    private val authorized: (String) -> Boolean,
    private val storage: (String) -> GuestSnapshotStorage,
    private val erase: (String) -> Unit,
) : ProtectedAccountPortfolio {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val deletions = mutableMapOf<String, Long>()
    override suspend fun readAccountPortfolio(origin: String): ByteArray? {
        mutex.lock()
        try {
            check(authorized(origin)) { "Unlock this device to view saved investments." }
            val revision = deletions[origin]
            val bytes = storage(origin).read()
            if (!authorized(origin) || revision != deletions[origin]) {
                bytes?.fill(0)
                throw CancellationException("Portfolio access was locked.")
            }
            return bytes
        } finally { mutex.unlock() }
    }
    override suspend fun writeAccountPortfolio(origin: String, bytes: ByteArray) {
        mutex.lock()
        try {
            check(authorized(origin)) { "Unlock this device to save investments." }
            val revision = deletions[origin]
            try { storage(origin).write(bytes) }
            finally { if (revision != deletions[origin]) erase(origin) }
        } finally { mutex.unlock() }
    }
    override fun deleteAccountPortfolio(origin: String) {
        deletions[origin] = (deletions[origin] ?: 0) + 1
        erase(origin)
    }
}

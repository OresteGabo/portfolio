package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// Enable only after documenting a licence/permission covering app redistribution and local caching.
// Public visibility and the existing anonymous API are not evidence of redistribution rights.
internal const val GUEST_RSE_DISTRIBUTION_APPROVED = false

@Composable expect fun rememberGuestMarketData(): GuestMarketData

data class GuestQuoteSnapshot(val data: JsonRecord, val receivedAt: Instant) {
    fun stale(now: Instant): Boolean = data.getJSONObject("source").let {
        it.optBoolean("stale") || now < receivedAt || now - Instant.parse(it.text("publishedAt")) > 72.hours ||
            now - Instant.parse(it.text("checkedAt")) > 24.hours
    }
}

/** Separate, reconstructible public cache: never embeds guest records, references or an account origin. */
class GuestMarketData(
    private val storage: GuestSnapshotStorage,
    val enabled: Boolean,
    private val fetch: suspend () -> JsonRecord,
    private val now: () -> Instant = { Clock.System.now() },
    private val clock: TimeSource = TimeSource.Monotonic,
    private val workDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private var attempted: TimeMark? = null
    private var failed = false
    suspend fun load(): GuestQuoteSnapshot? = withContext(workDispatcher) {
        if (!enabled) return@withContext null
        try {
            storage.read()?.let { bytes ->
                try {
                    require(bytes.size <= PUBLIC_QUOTE_MAX_BYTES)
                    val root = JsonRecord(bytes.decodeToString(throwOnInvalidSequence = true))
                    require(root.getDouble("schemaVersion") == 1.0 && root.text("provider") == "rse-equities")
                    val received = Instant.parse(root.text("receivedAt"))
                    require(received <= now() && Instant.parse(root.getJSONObject("data").getJSONObject("source").text("checkedAt")) <= received)
                    GuestQuoteSnapshot(normalizedPublicEquityQuotes(root.getJSONObject("data"), now()), received)
                } finally { bytes.fill(0) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } // A corrupt public cache is unavailable, never a lost private portfolio.
    }
    suspend fun refresh(): GuestQuoteSnapshot? = withContext(workDispatcher) { mutex.withLock {
        if (!enabled) return@withLock null
        val old = load()
        if (attempted?.elapsedNow()?.let { it < 15.minutes } == true) {
            check(!failed) { "Public quotes unavailable." }; return@withLock old
        }
        if (old?.let { now() - it.receivedAt < 15.minutes } == true) return@withLock old
        attempted = clock.markNow()
        try {
            val data = normalizedPublicEquityQuotes(fetch(), now())
            val snapshot = GuestQuoteSnapshot(data, now())
            val bytes = JsonRecord().put("schemaVersion", 1).put("provider", "rse-equities")
                .put("receivedAt", snapshot.receivedAt.toString()).put("data", data).toString().encodeToByteArray()
            require(bytes.size <= PUBLIC_QUOTE_MAX_BYTES)
            withContext(workDispatcher) { storage.write(bytes) }
            failed = false
            snapshot
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { failed = true; throw failure }
    } }
}

/** Lazy transport: disabled builds never even construct a network client. No saved origin/cookies. */
internal suspend fun fetchGuestPublicQuotes(): JsonRecord {
    check(GUEST_RSE_DISTRIBUTION_APPROVED)
    val client = PublicEquityQuotesApi()
    return try { client.fetch() } finally { client.close() }
}

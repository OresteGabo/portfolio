@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import kotlin.test.*

class AccountPortfolioCacheTest {
    private val origin = "https://offline.example.test"
    private val identity = JsonRecord().put("id", "offline-account").put("email", "offline@example.test")
    private fun signIn(id: String = "offline-account", email: String = "offline@example.test") =
        JsonRecord().put("accountId", id).put("email", email)
    private class Store : ProtectedAccountPortfolio {
        var bytes: ByteArray? = null
        override suspend fun readAccountPortfolio(origin: String) = bytes?.copyOf()
        override suspend fun writeAccountPortfolio(origin: String, bytes: ByteArray) { this.bytes = bytes.copyOf() }
        override fun deleteAccountPortfolio(origin: String) { bytes = null }
    }
    private suspend fun AccountPortfolioCache.save(snapshot: PortfolioSnapshot = PortfolioSnapshot(),
        user: JsonRecord = identity) = write(origin, user, snapshot, true,
        listOf(JsonRecord().put("id", "cash").put("amount", 98.76)), true,
        listOf(JsonRecord().put("purchaseId", "bond").put("amount", 12345.67)), true)

    @Test fun roundTripPreservesFinancialRowsHistoryAndAmountsWithoutCredentialsOrPrivileges() = runTest {
        val store = Store(); val cache = AccountPortfolioCache(store, StandardTestDispatcher(testScheduler))
        val bond = JsonRecord().put("id", "bond").put("faceValue", 1234567.89).put("status", "submitted")
        val share = JsonRecord().put("id", "share").put("shares", 1000).put("pricePerShare", 720.12).put("netAmountPayable", 732434.052)
        val pending = JsonRecord().put("id", "pending").put("shares", 100).put("estimatedTotal", 73231.2)
        val snapshot = PortfolioSnapshot(listOf(bond), listOf(share), listOf(JsonRecord().put("id", "pdf")),
            setOf("bond"), emptySet(), listOf(JsonRecord().put("month", "2026-01").put("amount", 4123456.78)),
            listOf(pending), listOf(JsonRecord().put("couponRate", 0.1275)))
        val user = JsonRecord(identity.toString()).put("password", "private-secret").put("session", "cookie-secret").put("isOwner", true)
        assertNotNull(cache.save(snapshot, user))
        val raw = requireNotNull(store.bytes).decodeToString()
        assertFalse(raw.contains("private-secret")); assertFalse(raw.contains("cookie-secret")); assertFalse(raw.contains("isOwner"))
        val restored = requireNotNull(cache.read(origin, signIn()))
        assertEquals(bond.toString(), restored.snapshot.purchases.single().toString())
        assertEquals(share.toString(), restored.snapshot.holdings.single().toString())
        assertEquals(pending.toString(), restored.snapshot.shareOrders.single().toString())
        assertEquals(setOf("bond"), restored.snapshot.recentPurchaseIds); assertEquals(emptySet(), restored.snapshot.recentHoldingIds)
        assertEquals(4123456.78, restored.snapshot.monthlyInvestmentTotals!!.single().getDouble("amount"))
        assertEquals(98.76, restored.ledger.single().getDouble("amount")); assertEquals(12345.67, restored.receipts.single().getDouble("amount"))
        assertTrue(restored.historyComplete); assertTrue(restored.ledgerAvailable); assertTrue(restored.couponTrackingAvailable)
        assertFalse(restored.user.optBoolean("isOwner"))
        assertFailsWith<IllegalStateException> { restored.snapshot.purchases.single().put("faceValue", 0) }
    }

    @Test fun differentOriginAccountOrEmailCannotReadAnotherAccountsSnapshot() = runTest {
        val cache = AccountPortfolioCache(Store(), StandardTestDispatcher(testScheduler)); cache.save()
        assertNull(cache.read("https://different.example.test", signIn()))
        assertNull(cache.read(origin, signIn(id = "other")))
        assertNull(cache.read(origin, signIn(email = "other@example.test")))
        assertNull(cache.read(origin, signIn(id = "")))
    }

    @Test fun corruptUnknownOrIncompleteCacheIsNotMistakenForAnEmptyPortfolio() = runTest {
        val store = Store(); val cache = AccountPortfolioCache(store, StandardTestDispatcher(testScheduler)); cache.save()
        val valid = requireNotNull(store.bytes).copyOf()
        for (mutate in listOf<(JsonRecord) -> Unit>(
            { it.put("version", 99) }, { it.put("savedAt", "not-a-date") },
            { it.put("portfolio", JsonRecord()) },
            { it.getJSONObject("portfolio").also { data -> data.put("holdings", JsonList(listOf("invalid"))); it.put("portfolio", data) } },
        )) {
            val root = JsonRecord(valid.decodeToString()); mutate(root); store.bytes = root.toString().encodeToByteArray()
            assertNull(cache.read(origin, signIn()))
        }
        store.bytes = "broken".encodeToByteArray(); assertNull(cache.read(origin, signIn()))
    }

    @Test fun nativeReadWriteRequireDeviceAuthorizationAndDeletionCannotRaceAWrite() = runTest {
        var unlocked = false; var stored: ByteArray? = null
        var gate: CompletableDeferred<Unit>? = null
        val native = object : GuestSnapshotStorage {
            override suspend fun read() = stored?.copyOf()
            override suspend fun write(bytes: ByteArray) { gate?.await(); stored = bytes.copyOf() }
        }
        val store = DeviceUnlockedPortfolioStorage({ unlocked }, { native }, { stored = null })
        assertFailsWith<IllegalStateException> { store.readAccountPortfolio(origin) }
        assertFailsWith<IllegalStateException> { store.writeAccountPortfolio(origin, byteArrayOf(1)) }
        unlocked = true; store.writeAccountPortfolio(origin, byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), store.readAccountPortfolio(origin))
        gate = CompletableDeferred()
        val writer = async { store.writeAccountPortfolio(origin, byteArrayOf(4, 5)) }
        runCurrent(); store.deleteAccountPortfolio(origin); gate.complete(Unit); writer.await()
        assertNull(stored, "A pending cache commit must not resurrect data after sign-out")
        unlocked = false
        assertFailsWith<IllegalStateException> { store.readAccountPortfolio(origin) }
    }

    @Test fun deviceLockDuringNativeReadCancelsPublicationAndClearsReturnedBytes() = runTest {
        var unlocked = true; val gate = CompletableDeferred<Unit>(); val bytes = byteArrayOf(7, 8)
        val native = object : GuestSnapshotStorage {
            override suspend fun read(): ByteArray { gate.await(); return bytes }
            override suspend fun write(bytes: ByteArray) = Unit
        }
        val store = DeviceUnlockedPortfolioStorage({ unlocked }, { native }, {})
        val reader = async { store.readAccountPortfolio(origin) }
        runCurrent(); unlocked = false; gate.complete(Unit)
        assertFailsWith<CancellationException> { reader.await() }
        assertTrue(bytes.all { it == 0.toByte() })
    }
}

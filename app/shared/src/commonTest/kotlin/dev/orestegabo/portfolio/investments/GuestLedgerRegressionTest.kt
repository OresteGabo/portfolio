@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

/** Atomic-storage double: a failed/cancelled write never replaces the previous durable snapshot. */
class GuestLedgerRegressionTest {
    private class Disk : GuestSnapshotStorage {
        var bytes: ByteArray? = null
        var rejectWrite = false
        var pauseWrite = false
        val enteredWrite = CompletableDeferred<Unit>()
        override suspend fun read(): ByteArray? {
            val snapshot = bytes?.copyOf()
            yield() // Force competing operations to overlap if repository serialization is removed.
            return snapshot
        }
        override suspend fun write(bytes: ByteArray) {
            check(!rejectWrite) { "synthetic storage failure" }
            if (pauseWrite) { enteredWrite.complete(Unit); awaitCancellation() }
            this.bytes = bytes.copyOf()
        }
    }
    private fun repository(disk: Disk, dispatcher: CoroutineDispatcher): GuestPortfolioRepository =
        LocalGuestPortfolioRepository(disk, now = { "2027-02-01T12:00:00Z" },
            today = { LocalDate(2027, 2, 1) }, workDispatcher = dispatcher)
    private fun sale(id: String, holding: String, shares: Int = 100) = JsonRecord().put("id", id)
        .put("kind", "sale").put("holdingId", holding).put("date", "2027-02-01").put("currency", "RWF")
        .put("shares", shares).put("price", 110).put("fees", 100).put("tax", 0)

    @Test fun saleAndDeletionSurviveReconstructionWithoutChangingOriginalPurchaseFacts() = runTest {
        val disk = Disk(); val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = repository(disk, dispatcher)
        val initial = owner.saveShare(guestShareFixture()).portfolio.holdings.single()
        val id = initial.text("id")
        owner.saveLedgerEntry(sale("sale-one", id))
        val reloaded = repository(disk, dispatcher).load()
        val holding = reloaded.portfolio.holdings.single()
        assertEquals(400.0, holding.getDouble("shares"))
        assertEquals(36615.6, holding.getDouble("netAmountPayable"))
        assertEquals(300.0, holding.getDouble("remainingShares"))
        assertEquals(27461.7, holding.getDouble("remainingCost"))
        assertEquals(1746.1, reloaded.metadata.rows("cashLedger").single().getDouble("realizedGain"))
        repository(disk, dispatcher).deleteLedgerEntry("sale-one")
        val restored = repository(disk, dispatcher).load()
        assertTrue(restored.metadata.rows("cashLedger").isEmpty())
        assertEquals(initial.toString(), restored.portfolio.holdings.single().toString())
    }

    @Test fun replayDoesNotDuplicateSaleAndConflictingIdentityCannotOverwriteIt() = runTest {
        val disk = Disk(); val owner = repository(disk, StandardTestDispatcher(testScheduler))
        val id = owner.saveShare(guestShareFixture()).portfolio.holdings.single().text("id")
        val request = sale("sale-one", id)
        val saved = owner.saveLedgerEntry(request).metadata.rows("cashLedger").single()
        val replay = owner.saveLedgerEntry(request)
        assertEquals(listOf(saved.toString()), replay.metadata.rows("cashLedger").map(JsonRecord::toString))
        assertEquals(300.0, replay.portfolio.holdings.single().getDouble("remainingShares"))
        val before = requireNotNull(disk.bytes).copyOf()
        for ((field, value) in listOf("price" to 111, "shares" to 101, "fees" to 101, "tax" to 1, "note" to "different")) {
            assertFailsWith<IllegalArgumentException> {
                owner.saveLedgerEntry(JsonRecord(request.toString()).put(field, value))
            }
            assertContentEquals(before, disk.bytes)
        }
    }

    @Test fun originalCashRequestCanBeReplayedButChangedAmountCannotReplaceIt() = runTest {
        val disk = Disk(); val owner = repository(disk, StandardTestDispatcher(testScheduler))
        val request = JsonRecord().put("id", "deposit-one").put("kind", "deposit")
            .put("currency", "RWF").put("date", "2027-02-01").put("amount", 1000)
        val saved = owner.saveLedgerEntry(request).metadata.rows("cashLedger").single()
        val replay = owner.saveLedgerEntry(request).metadata.rows("cashLedger")
        assertEquals(listOf(saved.toString()), replay.map(JsonRecord::toString))
        val before = requireNotNull(disk.bytes).copyOf()
        assertFailsWith<IllegalArgumentException> { owner.saveLedgerEntry(JsonRecord(request.toString()).put("amount", 1001)) }
        assertContentEquals(before, disk.bytes)
    }

    @Test fun failedSaleWriteLeavesNoCashEventOrChangedBalanceAndRetryCommitsOnce() = runTest {
        val disk = Disk(); val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = repository(disk, dispatcher)
        val initial = owner.saveShare(guestShareFixture())
        val entry = sale("sale-one", initial.portfolio.holdings.single().text("id"))
        val before = requireNotNull(disk.bytes).copyOf()
        disk.rejectWrite = true
        assertFailsWith<IllegalStateException> { owner.saveLedgerEntry(entry) }
        assertContentEquals(before, disk.bytes)
        disk.rejectWrite = false
        assertEquals(initial.revision, repository(disk, dispatcher).load().revision)
        val retried = owner.saveLedgerEntry(entry)
        assertEquals(1, retried.metadata.rows("cashLedger").size)
        assertEquals(300.0, retried.portfolio.holdings.single().getDouble("remainingShares"))
    }

    @Test fun cancellingBlockedWritePreservesCommittedStateAndReleasesRepositoryForRetry() = runTest {
        val disk = Disk(); val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = repository(disk, dispatcher)
        val initial = owner.saveShare(guestShareFixture())
        val entry = sale("sale-one", initial.portfolio.holdings.single().text("id"))
        val before = requireNotNull(disk.bytes).copyOf()
        disk.pauseWrite = true
        val job = launch { owner.saveLedgerEntry(entry) }
        disk.enteredWrite.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertContentEquals(before, disk.bytes)
        disk.pauseWrite = false
        assertEquals(initial.revision, owner.load().revision)
        val retried = owner.saveLedgerEntry(entry)
        assertEquals(1, retried.metadata.rows("cashLedger").size)
        assertEquals(300.0, retried.portfolio.holdings.single().getDouble("remainingShares"))
    }

    @Test fun concurrentSalesCannotOversellAndLinkedPurchaseCannotBeEditedOrDeleted() = runTest {
        val disk = Disk(); val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = repository(disk, dispatcher)
        val id = owner.saveShare(guestShareFixture()).portfolio.holdings.single().text("id")
        val results = listOf("sale-one", "sale-two").map { identity -> async {
            try { owner.saveLedgerEntry(sale(identity, id, 300)); true }
            catch (_: IllegalArgumentException) { false }
        } }.awaitAll()
        assertEquals(1, results.count { it })
        val snapshot = repository(disk, dispatcher).load()
        assertEquals(1, snapshot.metadata.rows("cashLedger").size)
        assertEquals(100.0, snapshot.portfolio.holdings.single().getDouble("remainingShares"))
        val before = requireNotNull(disk.bytes).copyOf()
        assertFailsWith<IllegalArgumentException> { owner.saveShare(guestShareFixture().put("shares", 500), id) }
        assertFailsWith<IllegalArgumentException> { owner.deleteShare(id) }
        assertContentEquals(before, disk.bytes)
    }
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.features

import dev.orestegabo.portfolio.features.transactions.CashLedgerUiState
import dev.orestegabo.portfolio.features.transactions.PortfolioCashLedger
import dev.orestegabo.portfolio.investments.JsonList
import dev.orestegabo.portfolio.investments.JsonRecord
import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class CashLedgerStateTest {
    private class PendingData : PortfolioDataApi {
        val replies = ArrayDeque<CompletableDeferred<JsonRecord>>()
        var ignoresCancellation = false
        override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord {
            val reply = replies.removeFirst()
            return if (ignoresCancellation) withContext(NonCancellable) { reply.await() } else reply.await()
        }
        override suspend fun bytes(path: String, method: String, body: JsonRecord?): ByteArray = error("Unexpected PDF access")
    }
    private fun response(id: String) = JsonRecord().put("entries", JsonList(listOf(JsonRecord().put("id", id))))
    private fun PendingData.enqueue() = CompletableDeferred<JsonRecord>().also { replies += it }

    @Test fun loadingSuccessFailureAndRetryPublishOwnedSnapshots() = runTest {
        val data = PendingData(); val ledger = PortfolioCashLedger({ data }) {}
        assertEquals(CashLedgerUiState(), ledger.state)
        val first = data.enqueue(); val request = launch { ledger.reload() }; runCurrent()
        assertTrue(ledger.state.isRefreshing); assertFalse(ledger.state.available); assertNull(ledger.state.error)
        first.complete(response("saved")); request.join()
        val previous = ledger.state
        assertTrue(previous.available); assertFalse(previous.isRefreshing)
        assertFailsWith<IllegalStateException> { previous.entries.single().put("id", "changed") }
        assertTrue(runCatching { (previous.entries as MutableList<JsonRecord>).clear() }.isFailure)

        val failure = data.enqueue(); val failed = launch { ledger.reload() }; runCurrent()
        assertTrue(ledger.state.isRefreshing); assertSame(previous.entries, ledger.state.entries)
        failure.completeExceptionally(ApiException(503, "synthetic")); failed.join()
        assertFalse(ledger.state.available); assertFalse(ledger.state.isRefreshing)
        assertEquals("saved", ledger.entries.single().text("id")); assertNotNull(ledger.error)

        data.enqueue().complete(response("retried")); ledger.reload()
        assertTrue(ledger.available); assertNull(ledger.error); assertFalse(ledger.state.isRefreshing)
        assertEquals("retried", ledger.entries.single().text("id"))
        assertEquals("saved", previous.entries.single().text("id"))
    }

    @Test fun unsupportedMissingAndEmptyDataRetainTheirDifferentAvailabilityContracts() = runTest {
        val data = PendingData(); val ledger = PortfolioCashLedger({ data }) {}
        data.enqueue().complete(response("old")); ledger.reload()
        data.enqueue().completeExceptionally(ApiException(404, "synthetic")); ledger.reload()
        assertFalse(ledger.available); assertTrue(ledger.entries.isEmpty()); assertNotNull(ledger.error)
        data.enqueue().complete(JsonRecord()); ledger.reload()
        assertFalse(ledger.available); assertTrue(ledger.entries.isEmpty()); assertNull(ledger.error)
        data.enqueue().complete(JsonRecord().put("entries", JsonList())); ledger.reload()
        assertTrue(ledger.available); assertTrue(ledger.entries.isEmpty()); assertNull(ledger.error)
    }

    @Test fun cancellationAndAuthorizationPreserveDataAndStopRefreshing() = runTest {
        val data = PendingData(); val ledger = PortfolioCashLedger({ data }) {}
        data.enqueue().complete(response("saved")); ledger.reload()
        data.enqueue(); val request = launch { ledger.reload() }; runCurrent(); request.cancelAndJoin()
        assertTrue(request.isCancelled); assertFalse(ledger.state.isRefreshing)
        assertTrue(ledger.available); assertNull(ledger.error)
        data.enqueue().completeExceptionally(ApiException(401, "synthetic"))
        assertEquals(401, assertFailsWith<ApiException> { ledger.reload() }.status)
        assertFalse(ledger.state.isRefreshing); assertTrue(ledger.available); assertNull(ledger.error)
    }

    @Test fun olderSuccessOrFailureCannotOverwriteANewerRefresh() = runTest {
        for (failOlder in listOf(false, true)) {
            val data = PendingData(); val ledger = PortfolioCashLedger({ data }) {}
            val first = data.enqueue(); val second = data.enqueue()
            val older = launch { ledger.reload() }; runCurrent()
            val newer = launch { ledger.reload() }; runCurrent()
            second.complete(response("newer")); newer.join()
            if (failOlder) first.completeExceptionally(ApiException(401, "stale")) else first.complete(response("older"))
            older.join()
            assertEquals("newer", ledger.entries.single().text("id")); assertTrue(ledger.available); assertNull(ledger.error)
            assertFalse(ledger.state.isRefreshing)
        }
    }

    @Test fun cancellationOfAnOlderRequestDoesNotStopTheNewLoadingState() = runTest {
        val data = PendingData(); val ledger = PortfolioCashLedger({ data }) {}
        data.enqueue(); val first = launch { ledger.reload() }; runCurrent()
        val reply = data.enqueue(); val second = launch { ledger.reload() }; runCurrent()
        first.cancelAndJoin()
        assertTrue(ledger.state.isRefreshing)
        reply.complete(response("newer")); second.join(); assertFalse(ledger.state.isRefreshing)
    }

    @Test fun clearAndClientReplacementInvalidateLateResponses() = runTest {
        for (replaceClient in listOf(false, true)) {
            val firstData = PendingData(); var current: PortfolioDataApi = firstData
            val ledger = PortfolioCashLedger({ current }) {}
            val reply = firstData.enqueue(); val request = launch { ledger.reload() }; runCurrent()
            if (replaceClient) current = PendingData() else ledger.clear()
            reply.complete(response("old-account")); request.join()
            assertEquals(CashLedgerUiState(), ledger.state)
        }
    }

    @Test fun cancelledReadCannotPublishEvenWhenTheDataProviderReturnsLate() = runTest {
        val data = PendingData(); val ledger = PortfolioCashLedger({ data }) {}
        data.enqueue().complete(response("saved")); ledger.reload()
        data.ignoresCancellation = true
        val reply = data.enqueue(); val request = launch { ledger.reload() }; runCurrent()
        request.cancel(); reply.complete(response("late")); request.join()
        assertTrue(request.isCancelled); assertFalse(ledger.state.isRefreshing)
        assertEquals("saved", ledger.entries.single().text("id")); assertNull(ledger.error)
    }

}

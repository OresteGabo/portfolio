@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

class EquityPriceHistoryTest {
    @Test fun cachedRevisionAvoidsRepeatedRequestsAndRefreshLoadsAgain() = runTest {
        var reads = 0
        val history = EquityPriceHistory { symbol, period ->
            reads++; JsonRecord().put("symbol", symbol).put("period", period)
        }
        repeat(2) { history.load("BOK", "all", 0) }
        assertEquals(1, reads)
        history.load("BOK", "all", 1)
        assertEquals(2, reads)
        assertEquals("BOK", history.values["BOK/all"]?.text("symbol"))
        assertTrue(history.loadingRevisions.isEmpty())
    }

    @Test fun failedRefreshRetainsLastHistoryAndCanBeRetried() = runTest {
        var fail = false
        val history = EquityPriceHistory { _, _ ->
            if (fail) error("unavailable")
            JsonRecord().put("observations", 2)
        }
        history.load("BOK", "all", 0)
        fail = true
        history.load("BOK", "all", 1)
        assertTrue(history.failed["BOK/all"] == true)
        assertEquals(2, history.values["BOK/all"]?.optInt("observations"))
        assertTrue(history.loadingRevisions.isEmpty())
        fail = false
        history.load("BOK", "all", 1)
        assertNull(history.failed["BOK/all"])
        assertEquals(1, history.loadedRevisions["BOK/all"])
    }

    @Test fun cancelledOldRequestCannotClearNewerLoadingState() = runTest {
        val gate = CompletableDeferred<Unit>()
        val history = EquityPriceHistory { _, _ -> gate.await(); JsonRecord() }
        val old = launch { history.load("BOK", "all", 0) }
        runCurrent()
        val fresh = launch { history.load("BOK", "all", 1) }
        runCurrent()
        old.cancelAndJoin()
        assertEquals(1, history.loadingRevisions["BOK/all"])
        assertNull(history.failed["BOK/all"])
        gate.complete(Unit)
        fresh.join()
        assertTrue(history.loadingRevisions.isEmpty())
    }
    @Test fun clearingRetryErrorsPreservesCachedValuesAndOtherErrors() = runTest {
        var fail = false
        val history = EquityPriceHistory { symbol, _ ->
            if (fail) error("unavailable")
            JsonRecord().put("symbol", symbol)
        }
        history.load("BOK", "all", 0); history.load("BLR", "all", 0)
        fail = true
        history.load("BOK", "all", 1); history.load("BLR", "all", 1)
        history.clearFailure("BOK", "all")
        assertNull(history.failed["BOK/all"])
        assertEquals(true, history.failed["BLR/all"])
        assertEquals(setOf("BOK/all", "BLR/all"), history.values.keys.toSet())
        history.clearFailures()
        assertTrue(history.failed.isEmpty())
        assertEquals(2, history.values.size)
    }
}

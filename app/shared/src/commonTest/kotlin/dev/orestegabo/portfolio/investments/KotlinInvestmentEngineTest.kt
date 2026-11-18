package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class KotlinInvestmentEngineTest {
    private val input = JsonRecord().put("assumptions", JsonRecord().put("horizonYears", 1).put("auctionFillRate", 0).put("contributionPeriods", JsonList()))
    @Test fun cancelledNativeCompletionCannotReturnPrivateResultsAndRetryRemainsAvailable() = runTest {
        val started = CompletableDeferred<Unit>(); val complete = CompletableDeferred<Unit>()
        val engine = KotlinInvestmentEngine { _, _ -> started.complete(Unit); withContext(NonCancellable) { complete.await() }; "PRIVATE_TEST_SENTINEL" to false }
        val operation = async { engine.run("pdf", pdf = byteArrayOf(1)) }
        started.await(); engine.cancel(); complete.complete(Unit)
        assertFailsWith<CancellationException> { operation.await() }
        assertEquals(12, engine.run("projection", input).getJSONArray("rows").length())
        engine.destroy()
    }
    @Test fun destructionCancelsActiveNativeWorkAndRejectsQueuedAndLaterRequests() = runTest {
        val started = CompletableDeferred<Unit>()
        val engine = KotlinInvestmentEngine { _, _ -> started.complete(Unit); awaitCancellation() }
        val operation = async { engine.run("pdf", pdf = byteArrayOf(1)) }
        started.await()
        val queued = async { runCatching { engine.run("projection", input) } }
        engine.destroy()
        assertFailsWith<CancellationException> { operation.await() }
        assertIs<IllegalStateException>(queued.await().exceptionOrNull())
        assertFailsWith<IllegalStateException> { engine.run("projection", input) }
    }
    @Test fun parserFailureDoesNotLeakRecognizedDocumentTextOrPoisonTheEngine() = runTest {
        val engine = KotlinInvestmentEngine { _, _ -> "PRIVATE_TEST_SENTINEL ordinary document" to false }
        val error = assertFailsWith<IllegalStateException> { engine.run("pdf", pdf = byteArrayOf(1)) }
        assertTrue(error.message.orEmpty().contains("Could not recognize"))
        assertFalse(error.message.orEmpty().contains("PRIVATE_TEST_SENTINEL"))
        assertEquals(3_600_000.0, engine.run("projection", input).getJSONObject("summary").optDouble("finalAccountValue"))
        engine.destroy()
    }
}

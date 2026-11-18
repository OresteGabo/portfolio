package dev.orestegabo.portfolio.investments

import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Native replacement for renderer recovery: a failed import must not poison later local work. */
class LocalEngineResilienceTest {
    @Test fun failedNativeExtractionCanBeRetriedAndDestroyedEnginesRejectFurtherWork(): Unit = runBlocking {
        ActivityScenario.launch(EngineHostActivity::class.java).use { scene ->
            lateinit var engine: LocalInvestmentEngine
            scene.onActivity { engine = it.engine }
            val input = JsonRecord().put("assumptions", JsonRecord().put("horizonYears", 1).put("auctionFillRate", 0).put("contributionPeriods", JsonList()))
            val failed = runCatching { engine.run("pdf", pdf = "%PDF-1.7 damaged\n%%EOF".encodeToByteArray()) }
            assertTrue(failed.exceptionOrNull() is IllegalArgumentException)
            assertEquals(12, withTimeout(25000) { engine.run("projection", input) }.getJSONArray("rows").length())
            val again = runCatching { engine.run("pdf", pdf = "invalid".encodeToByteArray()) }
            assertTrue(again.isFailure)
            assertEquals(12, engine.run("projection", input).getJSONArray("rows").length())
            scene.onActivity { engine.destroy() }
            assertTrue(runCatching { engine.run("projection", input) }.exceptionOrNull() is IllegalStateException)
        }
    }
}

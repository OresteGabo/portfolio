@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.features

import dev.orestegabo.portfolio.features.documents.PortfolioStatements
import dev.orestegabo.portfolio.investments.JsonList
import dev.orestegabo.portfolio.investments.JsonRecord
import dev.orestegabo.portfolio.network.PortfolioDataApi
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class StatementReviewLifecycleTest {
    private class PendingData : PortfolioDataApi {
        val replies = ArrayDeque<CompletableDeferred<JsonRecord>>()
        var ignoresCancellation = false
        override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord {
            val reply = replies.removeFirst()
            return if (ignoresCancellation) withContext(NonCancellable) { reply.await() } else reply.await()
        }
        override suspend fun bytes(path: String, method: String, body: JsonRecord?): ByteArray = error("Unexpected binary read")
    }
    private fun response(id: String) = JsonRecord().put("items", JsonList(listOf(JsonRecord().put("id", id))))

    @Test fun closedReviewStaysClosedWhenTheDocumentReadFinishes() = runTest {
        val data = PendingData(); val reply = CompletableDeferred<JsonRecord>(); data.replies += reply
        val statements = PortfolioStatements({ data }) {}
        val request = launch { statements.showDocument("document") }; runCurrent()
        statements.closeReview()
        reply.complete(response("late")); request.join()
        assertNull(statements.reviewRows)
    }

    @Test fun newestReviewIntentWinsWhenRequestsCompleteOutOfOrder() = runTest {
        val data = PendingData(); val first = CompletableDeferred<JsonRecord>(); val second = CompletableDeferred<JsonRecord>()
        data.replies += first; data.replies += second
        val statements = PortfolioStatements({ data }) {}
        val older = launch { statements.showDocument("older") }; runCurrent()
        val newer = launch { statements.showDocument("newer") }; runCurrent()
        second.complete(response("newer")); newer.join()
        first.complete(response("older")); older.join()
        assertEquals("newer", statements.reviewRows?.single()?.text("id"))
    }

    @Test fun selectingARecordOrClearingTheAccountInvalidatesPendingReviewPublication() = runTest {
        for (clear in listOf(false, true)) {
            val data = PendingData(); val reply = CompletableDeferred<JsonRecord>(); data.replies += reply
            val statements = PortfolioStatements({ data }) {}
            val request = launch { statements.showDocument("document") }; runCurrent()
            if (clear) statements.clear() else statements.showRecord(JsonRecord().put("id", "selected"))
            reply.complete(response("late")); request.join()
            if (clear) assertNull(statements.reviewRows) else assertEquals("selected", statements.reviewRows?.single()?.text("id"))
        }
    }

    @Test fun cancelledReadDoesNotOpenAReviewWhenAProviderReturnsLate() = runTest {
        val data = PendingData().apply { ignoresCancellation = true }
        val reply = CompletableDeferred<JsonRecord>(); data.replies += reply
        val statements = PortfolioStatements({ data }) {}
        val request = launch { statements.showDocument("document") }; runCurrent()
        request.cancel(); reply.complete(response("late")); request.join()
        assertTrue(request.isCancelled); assertNull(statements.reviewRows)
    }

}

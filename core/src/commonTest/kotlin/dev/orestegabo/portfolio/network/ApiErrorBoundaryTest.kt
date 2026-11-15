package dev.orestegabo.portfolio.network

import dev.orestegabo.portfolio.investments.UnlockPerformance
import io.ktor.client.engine.mock.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ApiErrorBoundaryTest {
    private val secret = "PRIVATE_DIAGNOSTIC https://private.example.test/?token=SECRET"

    private suspend fun diagnostics(block: suspend (List<String>) -> Unit) {
        val enabled = UnlockPerformance.enabled
        val sink = UnlockPerformance.sink
        val events = mutableListOf<String>()
        UnlockPerformance.enabled = true
        UnlockPerformance.sink = events::add
        try {
            block(events)
            assertTrue(events.none { it.contains("PRIVATE_DIAGNOSTIC") || it.contains("SECRET") || it.contains("private.example") })
        } finally { UnlockPerformance.enabled = enabled; UnlockPerformance.sink = sink }
    }

    @Test fun malformedSuccessfulResponsesHaveASafeTypedBoundaryAndKeepDecoderCauses() = runTest {
        for (raw in listOf("<html>$secret</html>", "[]", "null", "123")) {
            val api = PortfolioApi("https://errors.example.test", MockEngine { respond(raw) })
            try {
                val failure = assertFailsWith<ApiResponseException> { api.request("/api/bonds/purchases") }
                assertEquals(ApiResponseException.MESSAGE, failure.message)
                assertIs<IllegalArgumentException>(failure.cause)
                assertFalse(failure.message.orEmpty().contains("SECRET"))
            } finally { api.close() }
        }
        val empty = PortfolioApi("https://errors.example.test", MockEngine { respond("") })
        try { assertEquals("{}", empty.request("/api/bonds/purchases").toString()) } finally { empty.close() }
    }

    @Test fun requestConnectAndSocketTimeoutsHaveSafeMessagesAndPreserveCauses() = runTest {
        for (cause in listOf(HttpRequestTimeoutException(secret, 1), ConnectTimeoutException(secret), SocketTimeoutException(secret))) {
            diagnostics { events ->
                var calls = 0
                val api = PortfolioApi("https://errors.example.test", MockEngine { calls++; throw cause })
                try {
                    val failure = assertFailsWith<ApiTimeoutException> { api.request("/api/bonds/purchases", "POST") }
                    // Ktor/coroutines may copy transport exceptions during stack-trace recovery.
                    val retained = assertNotNull(failure.cause)
                    assertEquals(cause::class, retained::class)
                    assertEquals(cause.message, retained.message)
                    assertEquals(ApiTimeoutException.MESSAGE, failure.message)
                    assertEquals(1, calls) // An uncertain write is never replayed automatically.
                    assertTrue(events.any { it.contains("stage=http_POST") && it.contains("outcome=timeout") })
                } finally { api.close() }
            }
        }
    }

    @Test fun ownerCancellationAndUnexpectedProgrammingErrorsAreNotReclassified() = runTest {
        for (cause in listOf(CancellationException(secret), IllegalStateException(secret), AssertionError(secret))) {
            diagnostics { events ->
                val api = PortfolioApi("https://errors.example.test", MockEngine { throw cause })
                try {
                    val propagated = assertFails { api.request("/api/bonds/purchases") }
                    assertEquals(cause::class, propagated::class)
                    assertEquals(cause.message, propagated.message)
                    val expected = if (cause is CancellationException) "cancelled" else "failed"
                    assertTrue(events.any { it.contains("stage=http_GET") && it.contains("outcome=$expected") })
                } finally { api.close() }
            }
        }
    }

    @Test fun parentDeadlineRemainsCancellationAndTheClientCanHandleAnExplicitLaterRequest() = runTest {
        diagnostics { events ->
            var calls = 0
            val engine = MockEngine(MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { if (++calls == 1) awaitCancellation() else respond("{\"ok\":true}") }
            })
            val api = PortfolioApi("https://errors.example.test", engine)
            try {
                assertFailsWith<TimeoutCancellationException> {
                    withTimeout(5) { api.request("/api/bonds/purchases") }
                }
                assertEquals(1, calls)
                assertTrue(events.any { it.contains("outcome=cancelled") })
                assertTrue(api.request("/api/bonds/purchases").optBoolean("ok"))
                assertEquals(2, calls)
            } finally { api.close() }
        }
    }

    @Test fun retryIsExplicitAndHttpStatusBehaviorRemainsIntact() = runTest {
        diagnostics { events ->
            var calls = 0
            val api = PortfolioApi("https://errors.example.test", MockEngine {
                calls++
                if (calls == 1) respond("{\"error\":\"$secret\"}", HttpStatusCode.ServiceUnavailable) else respond("{\"ok\":true}")
            })
            try {
                assertEquals(503, assertFailsWith<ApiException> { api.request("/api/bonds/purchases") }.status)
                assertEquals(1, calls)
                assertTrue(api.request("/api/bonds/purchases").optBoolean("ok"))
                assertEquals(2, calls)
                assertTrue(events.any { it.contains("outcome=503") })
                assertTrue(events.any { it.contains("outcome=200") })
            } finally { api.close() }
        }
    }

    @Test fun responseReadFailureIsNotReportedAsSuccessfulHttpOutcome() = runTest {
        diagnostics { events ->
            val api = PortfolioApi("https://errors.example.test", MockEngine {
                respond("{}", headers = headersOf(HttpHeaders.ContentLength, "99999999"))
            })
            try {
                assertFailsWith<IllegalArgumentException> { api.request("/api/bonds/documents") }
                assertTrue(events.any { it.contains("stage=http_GET") && it.contains("outcome=failed") })
                assertFalse(events.any { it.contains("outcome=200") })
            } finally { api.close() }
        }
    }

    @Test fun measuredCancellationAndProgrammingFailureKeepTheirIdentityAndOutcome() = runTest {
        diagnostics { events ->
            val cancelled = CancellationException(secret)
            assertSame(cancelled, assertFailsWith<CancellationException> {
                UnlockPerformance.measure("json_decode") { throw cancelled }
            })
            assertSame(cancelled, assertFailsWith<CancellationException> {
                UnlockPerformance.measureSync("json_encode") { throw cancelled }
            })
            val unexpected = IllegalStateException(secret)
            assertSame(unexpected, assertFailsWith<IllegalStateException> {
                UnlockPerformance.measure("json_decode") { throw unexpected }
            })
            assertEquals(2, events.count { it.contains("outcome=cancelled") })
            assertEquals(1, events.count { it.contains("outcome=failed") })
        }
    }
}

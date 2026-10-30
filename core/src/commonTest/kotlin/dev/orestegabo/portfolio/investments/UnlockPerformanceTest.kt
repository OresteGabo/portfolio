package dev.orestegabo.portfolio.investments

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class UnlockPerformanceTest {
    @Test fun diagnosticsCorrelateRequestsWithoutSecretsIdentifiersOrQueryParameters() = runTest {
        val events = mutableListOf<String>()
        val oldSink = UnlockPerformance.sink
        UnlockPerformance.enabled = true; UnlockPerformance.sink = { events += it }
        var id = ""
        val api = PortfolioApi("https://diagnostic.example.test", MockEngine {
            id = it.headers["X-Request-ID"].orEmpty()
            respond("{}", HttpStatusCode.OK)
        })
        try {
            api.request("/api/bonds/documents/private-identifier?account=secret-query", "POST", JsonRecord().put("password", "secret-password"))
            assertTrue(id.matches(Regex("[0-9a-f]{32}")))
            assertTrue(events.any { it.contains("request_id=$id") && it.contains("route=/api/bonds/other") })
            assertTrue(events.any { it.contains("stage=json_encode") })
            assertTrue(events.any { it.contains("stage=json_decode") })
            assertTrue(events.none { it.contains("secret") || it.contains("private-identifier") || it.contains("diagnostic.example") })
            UnlockPerformance.sink = { error("Synthetic logging failure") }
            api.request("/api/bonds/auth/session") // Logging failures cannot reject a successful request.
        } finally { api.close(); UnlockPerformance.enabled = false; UnlockPerformance.sink = oldSink }
    }

    @Test fun disabledDiagnosticsEmitNothing() {
        val oldSink = UnlockPerformance.sink
        UnlockPerformance.enabled = false; UnlockPerformance.sink = { fail("Unexpected diagnostic") }
        try {
            assertNull(UnlockPerformance.begin("native_auth_prompt"))
            assertEquals(3, UnlockPerformance.measureSync("json_decode") { 3 })
        } finally { UnlockPerformance.sink = oldSink }
    }
}

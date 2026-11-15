package dev.orestegabo.portfolio.network

import dev.orestegabo.portfolio.investments.JsonList
import dev.orestegabo.portfolio.investments.JsonRecord
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PortfolioSessionApiTest {
    @Test fun sessionPresenceFollowsSnapshotRestoreAndClearThroughTheContract() = runTest {
        val original: PortfolioSessionApi = PortfolioApi("https://bonds.example.test", MockEngine {
            respond("{}", headers = headersOf(HttpHeaders.SetCookie, "bonds_session_v2=synthetic; Secure; HttpOnly; Path=/; Max-Age=3600"))
        })
        val restored: PortfolioSessionApi = PortfolioApi(original.origin, MockEngine { respond("{}") })
        try {
            assertFalse(original.hasSession())
            original.request("/api/bonds/auth/login", "POST", JsonRecord())
            assertTrue(original.hasSession())
            val opaque = original.sessionSnapshot()
            restored.restoreSession(opaque)
            assertTrue(restored.hasSession())
            assertEquals(opaque, restored.sessionSnapshot())
            restored.clearSession(); assertFalse(restored.hasSession())
            original.clearSession(); assertFalse(original.hasSession())
        } finally { original.close(); restored.close() }
    }

    @Test fun expiredOrUnrelatedCookiesAreNotSessionCredentialsAndOriginChecksRemainEnforced() = runTest {
        var requests = 0
        val client: PortfolioSessionApi = PortfolioApi("https://bonds.example.test", MockEngine { requests++; respond("{}") })
        try {
            fun snapshot(name: String, expiry: Long?) = JsonRecord().put("origin", client.origin).put("cookies", JsonList(listOf(
                JsonRecord().put("name", name).put("value", "synthetic").put("domain", "bonds.example.test")
                    .put("path", "/").put("encoding", "RAW").put("expiresAt", expiry)
            ))).toString()
            client.restoreSession(snapshot("unrelated", null)); assertFalse(client.hasSession())
            client.restoreSession(snapshot("bonds_session_v2", 1)); assertFalse(client.hasSession())
            assertFailsWith<IllegalArgumentException> {
                client.restoreSession(JsonRecord().put("origin", "https://other.example.test").put("cookies", JsonList()).toString())
            }
            assertEquals(0, requests)
        } finally { client.close() }
    }
}

package dev.orestegabo.portfolio.investments

import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PortfolioApiTest {
    @Test fun originAndSessionCookiesAreSharedAcrossPlatforms() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "session=test; HttpOnly; Secure; Path=/"))
        }
        val api = PortfolioApi("https://bonds.example.test", engine)
        try {
            api.request("/api/bonds/auth/login", "POST", JsonRecord().put("email", "test@example.test"))
            api.request("/api/bonds/purchases")
            assertEquals("https://bonds.example.test", requests[0].headers[HttpHeaders.Origin])
            assertEquals("application/json", requests[0].body.contentType.toString())
            assertTrue(requests[1].headers[HttpHeaders.Cookie].orEmpty().contains("session=test"))
            api.clearSession()
            api.request("/api/bonds/documents")
            assertFalse(requests[2].headers[HttpHeaders.Cookie].orEmpty().contains("session=test"))
        } finally { api.close() }
    }
    @Test fun redirectsDoNotForwardPrivateRequests() = runTest {
        var requests = 0
        val api = PortfolioApi("https://bonds.example.test", MockEngine {
            requests++
            respond("{}", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "https://other.example.test/private"))
        })
        try {
            val failure = assertFailsWith<ApiException> { api.request("/api/bonds/purchases") }
            assertEquals(307, failure.status)
            assertEquals(1, requests)
        } finally { api.close() }
    }
    @Test fun serverErrorsRemainVisible() = runTest {
        val api = PortfolioApi("https://bonds.example.test", MockEngine {
            respond("""{"error":"Authentication required."}""", HttpStatusCode.Unauthorized)
        })
        try {
            val failure = assertFailsWith<ApiException> { api.request("/api/bonds/purchases") }
            assertEquals(401, failure.status)
            assertEquals("Authentication required.", failure.message)
        } finally { api.close() }
    }
    @Test fun unsafePathsAreRejectedBeforeAnyRequest() = runTest {
        var requests = 0
        val api = PortfolioApi("https://bonds.example.test", MockEngine { requests++; respond("{}") })
        try {
            for (path in listOf("https://evil.test", "/private", "/api/bonds/../private", "/api/bonds/%2e%2e/private", "/api/bonds/purchases#evil")) {
                assertFailsWith<IllegalArgumentException> { api.request(path) }
            }
            assertEquals(0, requests)
        } finally { api.close() }
    }
    @Test fun productionConnectionRejectsHttpAndOriginsWithCredentials() {
        val invalid = listOf("http://bonds.example.test", "https://user:secret@bonds.example.test", "https://bonds.example.test/private", "https://bonds.example.test?x=1", "https://bonds.example.test#x")
        for (origin in invalid) {
            val engine = MockEngine { respond("{}") }
            try { assertFailsWith<IllegalArgumentException> { PortfolioApi(origin, engine, allowHttp = false) } }
            finally { engine.close() }
        }
    }
    @Test fun responseLimitIsEnforcedBeforeReadingOversizedData() = runTest {
        val api = PortfolioApi("https://bonds.example.test", MockEngine {
            respond("{}", headers = headersOf(HttpHeaders.ContentLength, "99999999"))
        })
        try { assertFailsWith<IllegalArgumentException> { api.request("/api/bonds/documents") } }
        finally { api.close() }
    }
}

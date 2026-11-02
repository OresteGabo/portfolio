package dev.orestegabo.portfolio.investments

import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Instant

class PublicEquityQuotesTest {
    private val now = Instant.parse("2026-11-02T14:00:00Z")
    private fun fixture() = JsonRecord("""{"source":{"url":"https://rse.rw/equities-market","status":"available","publishedAt":"2026-11-02T12:00:00Z","checkedAt":"2026-11-02T13:00:00Z","stale":false},"quotes":[{"ticker":"TEST","name":"Synthetic company","closing":90}]}""")

    @Test fun fixedBoardRequestsNeverCarryAccountCookiesOrPrivateParameters() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val api = PublicEquityQuotesApi("https://quotes.example.test", MockEngine {
            requests += it
            respond(fixture().toString(), headers = headersOf(HttpHeaders.SetCookie, "bonds_session_v2=do-not-return; Secure; Path=/"))
        })
        try {
            repeat(2) { normalizedPublicEquityQuotes(api.fetch(), now) }
            assertEquals(2, requests.size)
            requests.forEach {
                assertEquals(HttpMethod.Get, it.method)
                assertEquals("/api/bonds/rse/quotes", it.url.encodedPath)
                assertEquals("kind=equities", it.url.encodedQuery)
                for (header in listOf(HttpHeaders.Cookie, HttpHeaders.Authorization, HttpHeaders.Origin, "X-Request-ID")) assertNull(it.headers[header])
                assertTrue(it.body is io.ktor.http.content.OutgoingContent.NoContent)
            }
        } finally { api.close() }
    }
    @Test fun redirectsErrorsAndOversizedBodiesFailWithoutFollowingOrLeakingAnything() = runTest {
        for (status in listOf(HttpStatusCode.Found, HttpStatusCode.Unauthorized, HttpStatusCode.ServiceUnavailable, HttpStatusCode.OK)) {
            var calls = 0
            val api = PublicEquityQuotesApi("https://quotes.example.test", MockEngine {
                calls++
                respond(if (status == HttpStatusCode.OK) "x".repeat(PUBLIC_QUOTE_MAX_BYTES + 1) else "{}", status,
                    headersOf(HttpHeaders.Location,"https://external.example.test/secret"))
            })
            try { assertFails { api.fetch() }; assertEquals(1, calls) } finally { api.close() }
        }
    }
    @Test fun normalizationDropsUnknownFieldsAndRejectsUnverifiableOrAmbiguousQuotes() {
        val raw = fixture().put("privateField", "do not cache")
        raw.getJSONObject("source").also { raw.put("source", it.put("account", "do not cache")) }
        val clean = normalizedPublicEquityQuotes(raw, now).toString()
        assertFalse(clean.contains("privateField")); assertFalse(clean.contains("account"))
        for (field in listOf("publishedAt", "checkedAt", "url", "status")) {
            val bad = fixture(); bad.put("source", bad.getJSONObject("source").put(field,"invalid"))
            assertFails { normalizedPublicEquityQuotes(bad, now) }
        }
        val future = fixture(); future.put("source", future.getJSONObject("source").put("publishedAt","2030-01-01T00:00:00Z"))
        assertFails { normalizedPublicEquityQuotes(future, now) }
        for (rows in listOf("[]", "[{\"ticker\":\"TEST\",\"closing\":0}]", "[{\"ticker\":\"TEST\",\"closing\":90},{\"ticker\":\"TEST\",\"closing\":91}]")) {
            assertFails { normalizedPublicEquityQuotes(fixture().put("quotes", JsonList(rows)), now) }
        }
    }
    @Test fun unsafeOriginsAreRejected() {
        for (origin in listOf("http://quotes.example.test", "https://user:pass@quotes.example.test", "https://quotes.example.test/private", "https://quotes.example.test?account=x")) {
            val engine = MockEngine { error("No request allowed") }
            try { assertFails { PublicEquityQuotesApi(origin, engine) } } finally { engine.close() }
        }
    }
}

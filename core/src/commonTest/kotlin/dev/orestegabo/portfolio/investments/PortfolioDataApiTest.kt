package dev.orestegabo.portfolio.investments

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PortfolioDataApiTest {
    @Test fun interfaceRetainsConcreteClientDefaultArgumentsAndWirePayloads() = runTest {
        val calls = mutableListOf<Triple<String, HttpMethod, String?>>()
        val concrete = PortfolioApi("https://data-port.example.test", MockEngine { request ->
            calls += Triple(request.url.encodedPath, request.method,
                (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString())
            assertEquals("https://data-port.example.test", request.headers[HttpHeaders.Origin])
            respond("""{"ok":true}""")
        })
        try {
            val data: PortfolioDataApi = concrete
            assertTrue(data.request("/api/bonds/purchases").optBoolean("ok"))
            assertEquals("""{"ok":true}""", data.bytes("/api/bonds/documents/test").decodeToString())
            val body = JsonRecord().put("shares", 100).put("version", 3)
            data.request("/api/bonds/share-orders/test", "PUT", body)
            assertEquals(listOf(HttpMethod.Get, HttpMethod.Get, HttpMethod.Put), calls.map { it.second })
            assertNull(calls[0].third); assertNull(calls[1].third)
            assertEquals(body.toString(), calls[2].third)
        } finally { concrete.close() }
    }
}

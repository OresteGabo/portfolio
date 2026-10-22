package dev.orestegabo.portfolio.investments

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PortfolioApiTest {
    @Test fun sendsOriginAndRetainsCookieForAuthenticatedRequests() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val origin = "http://127.0.0.1:${server.address.port}"
        var sawOrigin = false
        var sawCookie = false
        server.createContext("/api/bonds/auth/login") { exchange ->
            sawOrigin = exchange.requestHeaders.getFirst("Origin") == origin && exchange.requestMethod == "POST"
            val input = JSONObject(exchange.requestBody.bufferedReader().readText())
            assertEquals("test@example.test", input.getString("email"))
            exchange.responseHeaders.add("Set-Cookie", "bonds_session=test-session; HttpOnly; Path=/")
            val bytes = "{\"authenticated\":true}".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/bonds/purchases") { exchange ->
            sawCookie = exchange.requestHeaders.getFirst("Cookie")?.contains("bonds_session=test-session") == true
            val bytes = "{\"purchases\":[]}".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = PortfolioApi(origin)
            api.request("/api/bonds/auth/login", "POST", JSONObject().put("email", "test@example.test"))
            api.request("/api/bonds/purchases")
            assertTrue(sawOrigin)
            assertTrue(sawCookie)
            api.clearSession()
            sawCookie = false
            api.request("/api/bonds/purchases")
            assertFalse(sawCookie)
        } finally { server.stop(0) }
    }
    @Test fun rejectsUnexpectedApiPathsAndPreservesServerErrors() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/bonds/purchases") { exchange ->
            val bytes = "{\"error\":\"Authentication required.\"}".toByteArray()
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = PortfolioApi("http://127.0.0.1:${server.address.port}")
            try { api.request("/api/bonds/purchases"); fail("Expected 401") }
            catch (error: ApiException) { assertEquals(401, error.status); assertEquals("Authentication required.", error.message) }
            try { api.request("/api/bonds/../private"); fail("Expected path rejection") }
            catch (_: IllegalArgumentException) { }
        } finally { server.stop(0) }
    }
}

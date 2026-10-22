package dev.orestegabo.portfolio.investments

import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeviceApiTest {
    @Test fun androidRequestsCarryOriginAndCookie() = runBlocking {
        val server = ServerSocket(0)
        server.soTimeout = 10000
        val origin = "http://127.0.0.1:${server.localPort}"
        val worker = Executors.newSingleThreadExecutor()
        val captured = worker.submit<List<List<String>>> {
            (0..1).map { index ->
                server.accept().use { client ->
                    client.soTimeout = 10000
                    val reader = client.getInputStream().bufferedReader()
                    val lines = mutableListOf<String>()
                    while (true) { val line = reader.readLine() ?: break; if (line.isBlank()) break; lines += line }
                    val length = lines.find { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
                    repeat(length) { reader.read() }
                    val body = if (index == 0) "{\"authenticated\":true}" else "{\"purchases\":[]}"
                    val cookie = if (index == 0) "Set-Cookie: bonds_session=device-test; HttpOnly; Path=/\r\n" else ""
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\n${cookie}Connection: close\r\n\r\n$body").toByteArray())
                    lines
                }
            }
        }
        try {
            val api = PortfolioApi(origin, allowHttp = true)
            api.request("/api/bonds/auth/login", "POST", JsonRecord().put("email", "demo@example.test"))
            api.request("/api/bonds/purchases")
            val requests = captured.get(15, TimeUnit.SECONDS)
            assertTrue(requests[0].any { it.equals("Origin: $origin", true) })
            assertTrue(requests[1].any { it.startsWith("Cookie:", true) && it.contains("bonds_session=device-test") })
        } finally { server.close(); worker.shutdownNow() }
    }
}

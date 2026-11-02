package dev.orestegabo.portfolio.investments

import android.os.Looper
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real transport, synthetic loopback response. Cancellation originates on Android's UI dispatcher. */
class HttpCancellationTest {
    @Test fun cancellingAStalledPdfResponseFromMainIsSafe() = runBlocking {
        val server=ServerSocket(0)
        val received=CountDownLatch(1)
        val release=CountDownLatch(1)
        val worker=Executors.newSingleThreadExecutor()
        worker.submit {
            try { server.accept().use { client ->
                val reader=client.getInputStream().bufferedReader()
                while(!reader.readLine().isNullOrEmpty()) {}
                client.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: application/pdf\r\nContent-Length: 50000\r\nConnection: close\r\n\r\n%PDF-1.7\n".toByteArray());flush()
                }
                received.countDown();release.await(10,TimeUnit.SECONDS)
            } } catch(_: Exception) {} finally { received.countDown() }
        }
        val api=PortfolioApi("http://127.0.0.1:${server.localPort}",allowHttp=true)
        try {
            val request=async(Dispatchers.Main) { api.bytes("/api/bonds/documents/synthetic-stalled") }
            assertTrue("Loopback response did not begin",received.await(5,TimeUnit.SECONDS))
            withContext(Dispatchers.Main) {
                assertEquals(Looper.getMainLooper(),Looper.myLooper())
                request.cancel()
            }
            withTimeout(3000) { request.join() }
            assertTrue(request.isCancelled)
        } finally { release.countDown();api.close();server.close();worker.shutdownNow() }
    }
}

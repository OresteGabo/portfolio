package dev.orestegabo.portfolio.investments

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DocumentThumbnailTest {
    @Test fun previewsUseOnlyExistingAuthenticatedDocuments() {
        fun source(path: String) = JsonRecord().put("downloadUrl", path)
        assertEquals("/api/bonds/documents/owned-1", documentThumbnailPath(source("/api/bonds/documents/owned-1")))
        for (path in listOf("https://example.test/private.pdf", "/api/bonds/documents/../auth/session", "", "/api/bonds/documents/a?public=true"))
            assertNull(documentThumbnailPath(source(path)))
        assertNull(documentThumbnailPath(source("/api/bonds/documents/generated").put("annualReportYear", 2026)))
    }
    @Test fun failedOrUnavailablePreviewsAreRememberedAndBounded() = runTest {
        val cache = DocumentThumbnailCache()
        var calls = 0
        suspend fun missing(): ImageBitmap { calls++; error("unavailable") }
        repeat(2) { assertNull(cache.get("one", ::missing)) }
        assertEquals(1, calls)
        repeat(8) { cache.get("next-$it", ::missing) }
        cache.get("one", ::missing)
        assertEquals(10, calls, "Only the latest eight outcomes are retained")
        cache.clear(); cache.get("one", ::missing)
        assertEquals(11, calls)
    }
    @Test fun concurrentRequestsDoNotRetryTheSameFailedDownload() = runTest {
        val cache = DocumentThumbnailCache()
        var calls = 0
        suspend fun slow(): ImageBitmap { calls++; delay(100); error("offline") }
        val first = async { cache.get("one", ::slow) }
        val second = async { cache.get("one", ::slow) }
        assertNull(first.await()); assertNull(second.await()); assertEquals(1, calls)
    }
    @Test fun cancellationsPropagateAndDoNotBecomeCachedFailures() = runTest {
        val cache = DocumentThumbnailCache()
        assertFailsWith<CancellationException> { cache.get("one") { throw CancellationException("offscreen") } }
        var calls = 0
        cache.get("one") { calls++; error("offline") }
        assertEquals(1, calls)
    }
    @Test fun slowOptionalPreviewTimesOutWithoutBlockingTheNextDocument() = runTest {
        val cache = DocumentThumbnailCache()
        assertNull(cache.get("slow") { delay(60_000); error("too late") })
        assertEquals(8_000, testScheduler.currentTime)
        var next = false
        cache.get("next") { next = true; error("offline") }
        assertTrue(next)
    }
    @Test fun clearingDuringARequestPreventsLateCachePopulation() = runTest {
        val cache = DocumentThumbnailCache()
        cache.get("one") { cache.clear(); error("old account") }
        var calls = 0
        cache.get("one") { calls++; error("new account") }
        assertEquals(1, calls)
    }
    @Test fun malformedResponsesNeverReachTheNativeDecoder() = runTest {
        var opened = false
        assertFails { renderDocumentThumbnail("<html>error</html>".encodeToByteArray()) { opened = true; error("unexpected") } }
        assertFalse(opened)
    }
    @Test fun aCancelledOrFailedDecoderAlwaysClosesItsNativeHandle() = runTest {
        for (cancelled in listOf(false, true)) {
            var closed = false
            val fake = object : PdfDocumentPages {
                override val pageCount = 1
                override suspend fun renderPage(index: Int, width: Int, region: PdfPageRegion): ImageBitmap {
                    assertEquals(0, index); assertEquals(480, width); assertEquals(.4, region.height)
                    if (cancelled) throw CancellationException("offscreen") else error("damaged")
                }
                override fun close() { closed = true }
            }
            if (cancelled) assertFailsWith<CancellationException> { renderDocumentThumbnail("%PDF-fixture".encodeToByteArray()) { fake } }
            else assertFailsWith<IllegalStateException> { renderDocumentThumbnail("%PDF-fixture".encodeToByteArray()) { fake } }
            assertTrue(closed)
        }
    }
}

package dev.orestegabo.portfolio.investments

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PdfDocumentRenderingTest {
    @Test fun originalPagesRenderAndTemporaryFilesAreRemoved() = runBlocking {
        val cache = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "reader-render-test").apply { mkdirs() }
        val unrelated = File(cache, "keep.txt").apply { writeText("fixture") }
        val document = AndroidPdfDocument(readerFixturePdf(), cache)
        try {
            assertEquals(2, document.pageCount)
            val page = document.renderPage(0, 800)
            assertEquals(800, page.width); assertEquals(1120, page.height)
            val pixels = IntArray(page.width * page.height)
            page.readPixels(pixels)
            assertTrue(pixels.any { it != -1 })
            assertTrue(File(cache, "pdf-preview").listFiles().orEmpty().isNotEmpty())
        } finally { document.close() }
        assertTrue(File(cache, "pdf-preview").listFiles().orEmpty().isEmpty())
        assertTrue(unrelated.exists())
        cache.deleteRecursively()
        Unit
    }
    @Test fun zoomRedrawsTheVisibleRegionFromThePdf() = runBlocking {
        val cache = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "reader-zoom-test").apply { mkdirs() }
        val document = AndroidPdfDocument(readerFixturePdf(), cache)
        try {
            val detail = document.renderPage(0, 800, PdfPageRegion(.375, .375, .25, .25))
            assertEquals(800, detail.width); assertEquals(1120, detail.height)
            val pixels = IntArray(detail.width * detail.height).also { detail.readPixels(it) }
            val viewport = document.renderPage(0, 800, PdfPageRegion(.375, .45, .25, .1))
            assertEquals(800, viewport.width); assertEquals(448, viewport.height)
            val row = detail.height / 2
            val transitions = (1 until detail.width).count {
                ((pixels[row * detail.width + it] and 0xffffff) < 0x808080) !=
                    ((pixels[row * detail.width + it - 1] and 0xffffff) < 0x808080)
            }
            assertTrue("Correct visible region must show fine vector stripes: $transitions", transitions in 95..101)
            assertTrue((0 until detail.width).count { (pixels[row * detail.width + it] and 0xffffff) in 0x010101..0xfefefe } < detail.width / 10)
        } finally { document.close(); cache.deleteRecursively() }
        Unit
    }
    @Test fun damagedPdfDoesNotLeaveATemporaryCopy() {
        val cache = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "reader-invalid-test").apply { mkdirs() }
        try {
            assertThrows(Exception::class.java) { AndroidPdfDocument("%PDF-broken".toByteArray(), cache) }
            assertTrue(File(cache, "pdf-preview").listFiles().orEmpty().isEmpty())
        } finally { cache.deleteRecursively() }
    }
}

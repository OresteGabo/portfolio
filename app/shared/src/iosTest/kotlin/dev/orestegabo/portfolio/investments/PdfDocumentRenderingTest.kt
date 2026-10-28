package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.*

class PdfDocumentRenderingTest {
    @Test fun pagePointsScaleUpToPixelsRatherThanStayingSmallAndCentered() = runBlocking {
        val document = IosPdfDocument(pdfRenderFixture())
        try {
            val page = document.renderPage(0, 1200)
            assertEquals(1200, page.width); assertEquals(1800, page.height)
            val pixels = IntArray(page.width * page.height).also { page.readPixels(it) }
            val nonWhite = pixels.indices.filter { (pixels[it] and 0xffffff) < 0xeeeeee }
            assertTrue(nonWhite.minOf { it % page.width } < page.width / 10)
            assertTrue(nonWhite.maxOf { it % page.width } > page.width * 9 / 10)
            assertTrue(nonWhite.minOf { it / page.width } < page.height / 10)
            assertTrue(nonWhite.maxOf { it / page.width } > page.height * 9 / 10)
        } finally { document.close() }
    }
    @Test fun zoomRendersVectorDetailAtNewResolutionWithinTheSameMemoryBudget() = runBlocking {
        val document = IosPdfDocument(pdfRenderFixture())
        try {
            val detail = document.renderPage(0, 1200, PdfPageRegion(.375, .375, .25, .25))
            assertEquals(1200, detail.width); assertEquals(1800, detail.height)
            val pixels = IntArray(detail.width * detail.height).also { detail.readPixels(it) }
            // Each 1-point stripe is 12 pixels wide at 4x, rendered afresh from vectors.
            val row = detail.height / 2
            val transitions = (1 until detail.width).count {
                ((pixels[row * detail.width + it] and 0xffffff) < 0x808080) !=
                    ((pixels[row * detail.width + it - 1] and 0xffffff) < 0x808080)
            }
            assertTrue(transitions in 95..101, "Zoom must draw the correct visible 100-point region: $transitions transitions")
            assertTrue((0 until detail.width).count { (pixels[row * detail.width + it] and 0xffffff) in 0x010101..0xfefefe } < detail.width / 10,
                "Vector stripe edges should remain sharp")
        } finally { document.close() }
    }
    @Test fun rectangularViewportKeepsItsAspectRatio() = runBlocking {
        val document = IosPdfDocument(pdfRenderFixture())
        try {
            val detail = document.renderPage(0, 1200, PdfPageRegion(.375, .45, .25, .1))
            assertEquals(1200, detail.width); assertEquals(720, detail.height)
            val pixels = IntArray(detail.width * detail.height).also { detail.readPixels(it) }
            assertTrue(pixels.count { (it and 0xffffff) < 0x808080 } in (pixels.size * .45).toInt()..(pixels.size * .55).toInt())
        } finally { document.close() }
    }
    @Test fun cropOriginAndRotationUseTheVisiblePageDimensions() = runBlocking {
        for (rotation in listOf(0, 90, 180, 270)) {
            val document = IosPdfDocument(pdfRenderFixture(rotation, cropped = true))
            try {
                val image = document.renderPage(0, 900)
                assertEquals(900, image.width)
                assertEquals(if (rotation % 180 == 0) 1200 else 675, image.height)
                val pixels = IntArray(image.width * image.height).also { image.readPixels(it) }
                assertTrue(pixels.count { (it and 0xffffff) < 0xeeeeee } > pixels.size / 2)
            } finally { document.close() }
        }
    }
    @Test fun generatedAnnualReportPagesRenderWithRealPdfKitContent() = runBlocking {
        val data = PortfolioReportData(PortfolioSnapshot(), null, emptyList(), "fixture@example.test", 100000, LocalDate(2026,10,28), "en")
        val document = IosPdfDocument(generateAnnualReport(data, 2025))
        try {
            assertTrue(document.pageCount > 0)
            val page = document.renderPage(0, 800)
            assertTrue(page.width > 0 && page.height > 0)
            assertTrue(page.width.toLong() * page.height <= 3_000_000)
            val pixels = IntArray(page.width * page.height)
            page.readPixels(pixels)
            assertTrue(pixels.distinct().size > 2)
        } finally { document.close() }
    }
    @Test fun damagedPdfIsRejected() { assertFails { IosPdfDocument("%PDF-broken".encodeToByteArray()) } }
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import platform.Foundation.NSString
import platform.UIKit.*
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGPointMake
import kotlin.test.*

class IosPdfTextExtractorTest {
    @Suppress("CAST_NEVER_SUCCEEDS") // Kotlin/Native bridges strings for UIKit drawing.
    private fun pdf(marker: String, pages: Int = 1): ByteArray =
        UIGraphicsPDFRenderer(bounds = CGRectMake(0.0, 0.0, 1100.0, 1400.0)).PDFDataWithActions { output ->
            repeat(pages) {
                requireNotNull(output).beginPage()
                val lines = listOf(marker, "Ordinary synthetic document text long enough for PDFKit extraction.",
                    "No financial format, amounts or account information is required.",
                    "The extraction component returns text independently of financial parsing.")
                lines.forEachIndexed { index, line ->
                    (line as NSString).drawAtPoint(CGPointMake(30.0, 40.0 + index * 30.0),
                        withAttributes = mapOf<Any?, Any?>(NSFontAttributeName to UIFont.systemFontOfSize(16.0)))
                }
            }
        }.toBytes()

    @Test fun extractionReturnsTextWithoutClassifyingOrPersistingAndKeepsCallsIndependent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val extractor = IosPdfTextExtractor()
            val bytes = pdf("FIRSTMARKER")
            val original = bytes.copyOf()
            val firstProgress = mutableListOf<String>()
            val (first, firstOcr) = extractor.extract(bytes, firstProgress::add)
            assertContains(first, "FIRSTMARKER")
            assertFalse(firstOcr)
            assertContentEquals(original, bytes)
            assertEquals(listOf("Reading page 1 of 1…"), firstProgress)
            val secondProgress = mutableListOf<String>()
            val (second, secondOcr) = extractor.extract(pdf("SECONDMARKER"), secondProgress::add)
            assertContains(second, "SECONDMARKER")
            assertFalse(second.contains("FIRSTMARKER"))
            assertFalse(secondOcr)
            assertEquals(listOf("Reading page 1 of 1…"), secondProgress)
            assertEquals(1, firstProgress.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun malformedAndOverLimitDocumentsDoNotPoisonTheNextExtraction() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val extractor = IosPdfTextExtractor()
            val tooManyPages = pdf("PAGE_LIMITMARKER", MAX_PDF_PAGES + 1)
            for (bytes in listOf(byteArrayOf(), "%PDF-1.7 damaged\n%%EOF".encodeToByteArray(), ByteArray(MAX_PDF_BYTES + 1), tooManyPages)) {
                assertFails { extractor.extract(bytes) }
            }
            assertContains(extractor.extract(pdf("RECOVEREDMARKER")).first, "RECOVEREDMARKER")
        } finally { Dispatchers.resetMain() }
    }

    @Test fun progressCancellationPropagatesWithoutRetainingTheCallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val extractor = IosPdfTextExtractor()
            val cancelled = CancellationException("Synthetic caller cancellation")
            assertSame(cancelled, assertFailsWith<CancellationException> {
                extractor.extract(pdf("CANCELLEDMARKER")) { throw cancelled }
            })
            assertContains(extractor.extract(pdf("NEXTMARKER")).first, "NEXTMARKER")
        } finally { Dispatchers.resetMain() }
    }
}

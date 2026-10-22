package dev.orestegabo.portfolio.investments
import kotlin.test.*

class SharedRulesTest {
    @Test fun formattingIsIdenticalWithoutPlatformFormatters() {
        assertEquals("RWF 1,000,000", money(1_000_000.0))
        assertEquals("RWF 340,728.5", money(340728.5))
        assertEquals("11.5%", percent(.115))
        assertEquals("RWF −100", money(-100.0))
        assertEquals("Unavailable", formatNumber(Double.NaN))
    }
    @Test fun pdfEnvelopeRejectsTrailersAndAcceptsBrokerPadding() {
        val valid = "%PDF-1.7\nbody\n%%EOF\n".encodeToByteArray()
        validatePdfEnvelope(valid + ByteArray(1000))
        assertFailsWith<IllegalArgumentException> { validatePdfEnvelope(valid + "<script>untrusted()</script>".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { validatePdfEnvelope("not a PDF".encodeToByteArray()) }
    }
}

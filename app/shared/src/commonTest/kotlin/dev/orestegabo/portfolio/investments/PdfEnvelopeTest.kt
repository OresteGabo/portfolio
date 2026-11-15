package dev.orestegabo.portfolio.investments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PdfEnvelopeTest {
    @Test fun supportedVersionsAndHeaderSeparatorsRemainAccepted() {
        for (version in (0..7).map { "1.$it" } + "2.0") {
            for (separator in listOf('\r', '\n', '\t', ' ')) {
                validatePdfEnvelope("%PDF-$version${separator}body\n%%EOF".encodeToByteArray())
            }
        }
    }

    @Test fun acceptedPaddingCanFollowTheEofMarker() {
        val padding = byteArrayOf(0, 9, 10, 12, 13, 32)
        validatePdfEnvelope("%PDF-1.7\nbody\n%%EOF".encodeToByteArray() + padding)
        validatePdfEnvelope("%PDF-2.0\nbody\n%%EOF".encodeToByteArray() + ByteArray(2048))
    }

    @Test fun malformedOrMissingEnvelopesKeepTheControlledError() {
        val invalid = listOf(
            "", "%PDF-1.7\n", "%PDF-1.7\n%%EO", "%PDF-1.8\n%%EOF", "%PDF-2.1\n%%EOF",
            "%PDF-1.7x\n%%EOF", " %PDF-1.7\n%%EOF", "%PDF-1.7\n%%EOFx",
            "%PDF-1.7\n%%EOF\u000b", "%PDF-1.7\n%%EOF\u0080",
        )
        for (input in invalid) {
            val failure = assertFailsWith<IllegalArgumentException> { validatePdfEnvelope(input.encodeToByteArray()) }
            assertEquals(PDF_READ_ERROR, failure.message)
        }
    }

    @Test fun sizeLimitIsAppliedBeforeTrimmingPadding() {
        val prefix = "%PDF-1.7\n%%EOF".encodeToByteArray()
        validatePdfEnvelope(prefix.copyOf(MAX_PDF_BYTES))
        val failure = assertFailsWith<IllegalArgumentException> { validatePdfEnvelope(prefix.copyOf(MAX_PDF_BYTES + 1)) }
        assertEquals(PDF_READ_ERROR, failure.message)
    }
}

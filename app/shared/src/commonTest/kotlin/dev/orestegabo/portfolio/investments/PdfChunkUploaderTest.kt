package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.*

class PdfChunkUploaderTest {
    @Test fun uploadPreservesBytesMetadataAndStableIdentityAcrossChunks() = runTest {
        val original = ByteArray(1_100_000) { (it % 251).toByte() }
        val sent = mutableListOf<JsonRecord>()
        val progress = mutableListOf<Pair<Int,Int>>()
        val uploader = PdfChunkUploader { request -> sent += request; JsonRecord().put("documentId", "stored-pdf") }
        val result = uploader.upload(original, "stable-upload", { JsonRecord().put("category", "Statement") },
            progress = { current, count -> progress += current to count })
        assertEquals("stored-pdf", result.text("documentId"))
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(listOf(0,1,2), sent.map { it.optInt("index") })
        assertTrue(sent.all { it.text("uploadId") == "stable-upload" && it.optInt("count") == 3 })
        assertTrue(sent.all { it.optJSONObject("metadata")?.text("category") == "Statement" })
        val decoded = ByteArray(original.size)
        var offset = 0
        sent.forEach { request ->
            val chunk = Base64.decode(request.text("content"))
            chunk.copyInto(decoded, offset)
            offset += chunk.size
        }
        assertContentEquals(original, decoded)
    }

    @Test fun sharedDocumentReuseStopsEarlyButOrdinaryUploadsFinishAllChunks() = runTest {
        for (early in listOf(false,true)) {
            var requests = 0
            val uploader = PdfChunkUploader { requests++; JsonRecord().put("documentId", "existing").put("duplicate", true) }
            val result = uploader.upload(ByteArray(600_000), "same-id", { JsonRecord() }, stopAtDocument = early)
            assertEquals(if (early) 1 else 2, requests)
            assertTrue(result.optBoolean("duplicate"))
        }
    }

    @Test fun errorsAndCancellationPropagateWithoutSendingLaterChunks() = runTest {
        for (failure in listOf(ApiException(503,"unavailable"), CancellationException("cancelled"))) {
            var requests = 0
            val uploader = PdfChunkUploader { requests++; throw failure }
            val caught = try { uploader.upload(ByteArray(600_000), "retry-id", { JsonRecord() }); null }
                catch (caught: Exception) { caught }
            assertSame(failure, caught)
            assertEquals(1, requests)
        }
    }
}

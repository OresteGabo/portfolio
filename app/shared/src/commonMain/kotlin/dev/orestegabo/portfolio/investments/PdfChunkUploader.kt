package dev.orestegabo.portfolio.investments

import kotlin.io.encoding.Base64

/** Transport-only chunking. Callers retain validation, receipt ownership and retry policy. */
internal class PdfChunkUploader(private val sendChunk: suspend (JsonRecord) -> JsonRecord) {
    suspend fun upload(
        bytes: ByteArray,
        uploadId: String,
        metadata: () -> JsonRecord,
        stopAtDocument: Boolean = false,
        progress: (sent: Int, count: Int) -> Unit = { _, _ -> },
    ): JsonRecord {
        val chunkSize = 512 * 1024
        val count = (bytes.size + chunkSize - 1) / chunkSize
        var response = JsonRecord()
        for (index in 0 until count) {
            progress(index + 1, count)
            val information = metadata()
            response = sendChunk(JsonRecord().put("uploadId", uploadId).put("index", index).put("count", count)
                .put("content", Base64.encode(bytes.copyOfRange(index * chunkSize, minOf(bytes.size, (index + 1) * chunkSize))))
                .put("metadata", information))
            if (stopAtDocument && response.text("documentId").isNotBlank()) return response
        }
        return response
    }
}

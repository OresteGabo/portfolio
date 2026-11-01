package dev.orestegabo.portfolio.investments

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class InvestmentDocumentShareTest {
    private class Actions : NativeActions {
        var name: String? = null
        var content: ByteArray? = null
        var accepted = true
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
        override suspend fun savePdf(fileName: String, bytes: ByteArray): Boolean = error("Use share/save sheet")
        override suspend fun sharePdf(fileName: String, bytes: ByteArray): Boolean {
            name = fileName; content = bytes; return accepted
        }
        override fun openUrl(url: String) = Unit
        override fun shareText(text: String) = Unit
    }

    @Test fun linkedPdfUsesNativeShareSaveAndCancellationDoesNotClaimSuccess() = runTest {
        val content = "%PDF-1.7\nsynthetic\n%%EOF".encodeToByteArray()
        var path = ""
        val api = PortfolioApi("https://documents.example.test", MockEngine {
            path = it.url.encodedPath
            respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/pdf"))
        })
        val document = JsonRecord().put("downloadUrl", "/api/bonds/documents/allocation-1").put("originalFileName", "allocation.pdf")
        val actions = Actions()
        try {
            assertTrue(shareInvestmentDocument(api, actions, document))
            assertEquals("/api/bonds/documents/allocation-1", path)
            assertEquals("allocation.pdf", actions.name)
            assertContentEquals(content, actions.content)
            actions.accepted = false
            assertFalse(shareInvestmentDocument(api, actions, document))
        } finally { api.close() }
    }

    @Test fun arbitraryUrlsAndNonPdfResponsesNeverOpenAShareSheet() = runTest {
        var requests = 0
        val api = PortfolioApi("https://documents.example.test", MockEngine {
            requests++; respond("not a PDF", HttpStatusCode.OK)
        })
        val actions = Actions()
        try {
            for (path in listOf("https://outside.example/file.pdf", "/api/bonds/auth/session", "/api/bonds/documents/../private")) {
                assertFailsWith<IllegalArgumentException> {
                    shareInvestmentDocument(api, actions, JsonRecord().put("downloadUrl", path))
                }
            }
            assertEquals(0, requests)
            assertFailsWith<IllegalArgumentException> {
                shareInvestmentDocument(api, actions, JsonRecord().put("downloadUrl", "/api/bonds/documents/allocation-1"))
            }
            assertNull(actions.content)
        } finally { api.close() }
    }
}

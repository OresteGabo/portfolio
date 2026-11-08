@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class BondApplicationUploadTest {
    private fun issue() = JsonRecord().put("id", "2026-11-5y-new").put("title", "New 5-year bond")
        .put("closingBookDate", "2026-11-11")

    private fun TestScope.model(owner: Boolean, uploads: MutableList<JsonRecord>): PortfolioModel {
        val settings = object : ServerSettings {
            override fun readOrigin() = "https://forms.example.test"
            override fun writeOrigin(origin: String) {}
        }
        return PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
            PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                val body = when (request.url.encodedPath) {
                    "/api/bonds/auth/session" -> """{"authenticated":true,"user":{"id":"test-account","isOwner":$owner,"canPublishPublicData":true}}"""
                    "/api/bonds/documents" -> if (request.method == HttpMethod.Post) {
                        uploads += JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        """{"documentId":"one-shared-form"}"""
                    } else """{"documents":[]}"""
                    "/api/bonds/application-forms" -> """{"forms":[{"id":"one-shared-form","category":"Bond application"}]}"""
                    else -> "{}"
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            }))
        })
    }

    @Test fun staffCannotPublishAnApplicationEvenWhenTheyCanPublishOtherSharedData() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val uploads = mutableListOf<JsonRecord>()
        val store = ViewModelStore()
        val model = model(false, uploads).also { store.put("forms", it) }
        try {
            advanceUntilIdle()
            assertNotNull(model.user)
            assertTrue(model.canPublishPublicData)
            model.publishBondApplicationForm(issue(), "%PDF-1.7\nblank\n%%EOF".encodeToByteArray()) { fail("Non-owner publication must fail") }
            advanceUntilIdle()
            assertTrue(uploads.isEmpty())
            assertEquals("Only the owner can upload bond application forms.", model.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun ownerUsesSharedCategoryAndChunkProtocolWithoutCreatingAPurchase() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val uploads = mutableListOf<JsonRecord>()
        val store = ViewModelStore()
        val model = model(true, uploads).also { store.put("forms", it) }
        try {
            advanceUntilIdle()
            var completed = false
            val pdf = "%PDF-1.7\nblank public application\n%%EOF".encodeToByteArray()
            model.publishBondApplicationForm(issue(), pdf) { completed = true }
            advanceUntilIdle()
            assertTrue(completed)
            assertNull(model.error)
            val upload = uploads.single()
            assertEquals("Bond application", upload.getJSONObject("metadata").text("category"))
            assertEquals("shared", upload.getJSONObject("metadata").text("visibility"))
            assertEquals("2026-11-5y-new", upload.getJSONObject("metadata").text("calendarId"))
            assertContentEquals(pdf, Base64.decode(upload.text("content")))
            assertEquals("one-shared-form", model.documents.single().text("id"))
            assertTrue(model.portfolio.purchases.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}

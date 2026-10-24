@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import androidx.lifecycle.ViewModelStore
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class PdfImportProgressTest {
    private class Actions : NativeActions {
        val file = CompletableDeferred<ByteArray>()
        var picks = 0
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? {
            picks++; onSelected(); return file.await()
        }
        override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
        override fun openUrl(url: String) {}
        override fun shareText(text: String) {}
    }
    private class Engine : InvestmentEngine {
        override var onProgress: (String) -> Unit = {}
        val result = CompletableDeferred<JsonRecord>()
        var cancellations = 0
        override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord {
            onProgress("Recognizing page 1 of 1…")
            return result.await()
        }
        override fun cancel() { cancellations++ }
        override fun destroy() {}
    }
    private fun model(dispatcher: TestDispatcher) = PortfolioModel(object : ServerSettings {
        override fun readOrigin() = "https://synthetic.example.test"
        override fun writeOrigin(origin: String) {}
    }, assets = { "[]".encodeToByteArray() }, apiFactory = { origin ->
        PortfolioApi(origin, MockEngine(MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request -> when (request.url.encodedPath) {
                "/api/bonds/auth/session" -> respond("""{"authenticated":true,"user":{"id":"test"}}""")
                "/api/bonds/mobile-overview" -> respond("{}", HttpStatusCode.NotFound)
                else -> respond("{}")
            } }
        }))
    })
    @Test fun fileReadAndExtractionAreVisibleAndRepeatUploadsAreBlockedUntilAnErrorClearsProgress() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val model = model(dispatcher); store.put("model", model)
        val actions = Actions(); val engine = Engine(); var reviewed = false
        try {
            advanceUntilIdle()
            model.importPurchasePdf(actions, engine, bondOnly = true) { reviewed = true }
            runCurrent()
            assertEquals(PdfImportStage.OPENING, model.pdfImportProgress?.stage)
            assertTrue(model.busy)
            model.importPurchasePdf(actions, engine) { reviewed = true }
            runCurrent()
            assertEquals(1, actions.picks)
            actions.file.complete(byteArrayOf(1)); advanceTimeBy(150); runCurrent()
            assertEquals(PdfImportStage.EXTRACTING, model.pdfImportProgress?.stage)
            assertEquals("Recognizing page 1 of 1…", model.pdfImportProgress?.detail)
            engine.result.completeExceptionally(IllegalArgumentException("Could not read this PDF.")); runCurrent()
            assertNotNull(model.pdfImportProgress)
            assertNull(model.error)
            advanceUntilIdle()
            assertNull(model.pdfImportProgress)
            assertFalse(model.busy)
            assertFalse(reviewed)
            assertEquals("Could not read this PDF.", model.error)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun cancellingExtractionStopsTheEngineClearsProgressAndPreservesThePurchase() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val model = model(dispatcher); store.put("model", model)
        val actions = Actions(); val engine = Engine(); var reviewed = false
        try {
            advanceUntilIdle()
            model.importPurchasePdf(actions, engine) { reviewed = true }; runCurrent()
            actions.file.complete(byteArrayOf(1)); advanceTimeBy(150); runCurrent()
            model.cancelPdfImport(); runCurrent()
            assertEquals(1, engine.cancellations)
            assertNull(model.pdfImportProgress)
            assertNull(model.error)
            assertFalse(model.busy)
            assertFalse(reviewed)
            assertTrue(model.purchases.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}

package dev.orestegabo.portfolio.investments

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.test.core.app.ActivityScenario
import dev.orestegabo.portfolio.network.PortfolioApi
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Fake account/API only. Verifies the real InvestmentApp toolbar, system Back, state and widget route. */
class NavigationUsabilityTest {
    @Test fun draftSurvivesBackErrorAndRetryAndSavingCannotBeSubmittedTwice() {
        val ui = UsabilityTestDriver()
        val writes = AtomicInteger()
        val quoteRequests = AtomicInteger()
        val quoteFails = AtomicBoolean()
        val failSave = AtomicBoolean(true)
        val saveGate = CompletableDeferred<Unit>()
        var saved: JsonRecord? = null
        val settings = object : ServerSettings {
            override fun readOrigin() = "https://ux.example.test"
            override fun writeOrigin(origin: String) = Unit
            override fun readScreenCaptureAllowed() = true
            override fun readLanguage() = "en"
        }
        val api = PortfolioApi(settings.readOrigin(), MockEngine { request ->
            val path = request.url.encodedPath
            if (path.endsWith("/rse/quotes")) {
                quoteRequests.incrementAndGet()
                if (quoteFails.get()) return@MockEngine respond("{}", HttpStatusCode.ServiceUnavailable,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
            if (request.method == HttpMethod.Put && path.contains("/share-orders/")) {
                writes.incrementAndGet()
                if (failSave.getAndSet(false)) return@MockEngine respond("{}", HttpStatusCode.ServiceUnavailable,
                    headersOf(HttpHeaders.ContentType, "application/json"))
                saveGate.await()
                val body = JsonRecord((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                val estimate = shareOrderEstimate(body.getDouble("shares"), body.getDouble("pricePerShare"))
                saved = body.put("id", path.substringAfterLast('/')).put("status", "pending").put("version", 1)
                    .put("currency", "RWF").put("estimatedTotal", estimate.total)
            }
            val body = when {
                path.endsWith("/auth/login") -> """{"user":{"id":"ux-account","email":"ux@example.test","isOwner":false}}"""
                path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"ux-account","email":"ux@example.test"}}"""
                path.endsWith("/mobile-overview") -> """{"version":1,"purchases":[],"holdings":[{"id":"ux-holding","ticker":"BLR","securityName":"Bralirwa","currency":"RWF","status":"active","shares":100,"pricePerShare":571,"grossConsideration":57100,"netAmountPayable":57100,"totalCharges":0,"tradeDate":"$Today","settlementDate":"$Today"}]}"""
                path.endsWith("/equities") -> """{"holdings":[{"id":"ux-holding","ticker":"BLR","securityName":"Bralirwa","currency":"RWF","status":"active","shares":100,"pricePerShare":571,"grossConsideration":57100,"netAmountPayable":57100,"totalCharges":0,"tradeDate":"$Today","settlementDate":"$Today"}]}"""
                path.endsWith("/share-orders") -> JsonRecord().put("orders", JsonList(listOfNotNull(saved))).toString()
                path.contains("/share-orders/") -> JsonRecord().put("order", saved).toString()
                path.endsWith("/rse/quotes") -> """{"source":{"status":"available"},"quotes":[{"ticker":"BOK","name":"Synthetic Bank","closing":720},{"ticker":"BLR","name":"Bralirwa","closing":571}]}"""
                else -> "{}"
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?) = error("No PDF import in this fixture")
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external navigation")
            override fun shareText(text: String) = Unit
        }
        UsabilityPreferences().use {
            ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                lateinit var model: PortfolioModel
                scene.onActivity { host ->
                    model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, apiFactory = { api }, deferAccountStartup = true)
                    host.viewModelStore.put("ux", model)
                    AppLanguage.initialize(settings)
                    host.setContent { CompositionLocalProvider(LocalImportEngine provides engine, LocalNativeActions provides actions) { InvestmentApp(model) } }
                    model.login("ux@example.test", "synthetic-only-password")
                }
                ui.waitUntil("account ready") { model.user != null && model.portfolioReady && !model.busy && model.shareOrdersAvailable }
                ui.scroll("Investment actions"); ui.click("Investment actions"); ui.click("Shares", last = true)
                ui.await("Security name"); ui.click("Security name"); ui.click("Synthetic Bank (BOK)")
                repeat(2) { ui.click("Increase shares") }
                ui.await("RWF 146,462.4")
                ui.click("Back"); ui.await("Discard changes?"); ui.click("Keep editing")
                ui.await("RWF 146,462.4"); assertEquals(0, writes.get())
                scene.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                ui.await("Discard changes?"); ui.click("Discard changes")
                ui.scroll("Investment actions"); assertEquals(0, writes.get())
                ui.click("Investment actions"); ui.click("Shares", last = true)
                // Untouched forms leave immediately, without a discard confirmation.
                ui.await("Security name"); ui.click("Back")
                ui.scroll("Investment actions")
                assertFalse(ui.nodes().any { ui.matches(it, "Discard changes?") })
                ui.click("Investment actions"); ui.click("Shares", last = true)
                ui.await("Security name"); ui.click("Security name"); ui.click("Synthetic Bank (BOK)")
                repeat(2) { ui.click("Increase shares") }
                quoteFails.set(true)
                scene.onActivity { model.loadMarket(refresh = true) }
                ui.await("Using previously loaded closing prices.")
                val beforeRetry = quoteRequests.get()
                quoteFails.set(false); ui.click("Retry")
                ui.waitUntil("quote retry completed") { quoteRequests.get() > beforeRetry && !model.busy && !model.quoteRefreshFailed }
                ui.await("RWF 146,462.4")
                ui.scroll("Save pending order"); ui.click("Save pending order")
                ui.waitUntil("recoverable save error") { writes.get() == 1 && !model.busy && model.error != null }
                val message = model.error
                Thread.sleep(5_500) // The previous UI removed errors after five seconds.
                assertEquals("Error must remain until dismissal or retry", message, model.error)
                ui.await("Dismiss"); ui.await("RWF 146,462.4")
                ui.scroll("Save pending order")
                val save = ui.await("Save pending order").let { text -> var n = text; while (!n.isClickable && n.parent != null) n = n.parent; n }
                assertTrue(save.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                save.performAction(AccessibilityNodeInfo.ACTION_CLICK) // A stale enabled node must not create a second request.
                ui.waitUntil("one save in flight") { writes.get() == 2 && model.busy }
                ui.await("Saving…"); ui.click("Back")
                ui.await("Saving…"); assertFalse(ui.nodes().any { ui.matches(it, "Discard changes?") })
                assertEquals(2, writes.get())
                saveGate.complete(Unit)
                ui.waitUntil("save completed") { !model.busy && model.shareOrders.size == 1 }
                ui.scroll("Your investments")
                assertEquals(200.0, requireNotNull(saved).getDouble("shares"), 0.0)
                // A widget request must open Calendar even while an unrelated read-only child is displayed.
                ui.click("Settings"); ui.scroll("About Inzira"); ui.click("About Inzira")
                ui.await("A little structure. A clearer view.")
                scene.onActivity { HomeWidgetBridge.openCalendar("2030-02-18") }
                ui.await("Calendar")
                ui.waitUntil("requested calendar month") { ui.nodes().any { it.isVisibleToUser && it.text?.toString()?.let { text -> text.contains("Feb") && text.contains("2030") } == true } }
                assertNull("The widget request has been consumed", HomeWidgetBridge.requestedDate)
                scene.onActivity { host ->
                    host.setContent { CompositionLocalProvider(LocalImportEngine provides engine, LocalNativeActions provides actions) {
                        key("recreated root") { InvestmentApp(model) }
                    } }
                }
                ui.scroll("Investment actions") // Recreating the root must not replay the old Calendar route.

            }
        }
    }
}

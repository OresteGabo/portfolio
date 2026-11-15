package dev.orestegabo.portfolio.investments

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import dev.orestegabo.portfolio.network.PortfolioApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Real root UI, disposable credentials, controlled failure; no identity provider or production account. */
class AuthenticationUiQualityTest {
    @Test fun signInShowsLoadingRetainsInputAfterFailureAndGuestChoiceCanBeCancelled() {
        val ui = UsabilityTestDriver()
        val requests = AtomicInteger()
        val gates = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
        val settings = object : ServerSettings {
            override fun readOrigin() = "https://auth-ui.example.test"
            override fun writeOrigin(origin: String) = Unit
            override fun readLanguage() = "en"
            override fun readScreenCaptureAllowed() = true
        }
        val http = MockEngine { request ->
            check(request.url.encodedPath.endsWith("/auth/login")) { "Unexpected request in signed-out fixture" }
            val attempt = requests.getAndIncrement()
            gates[attempt].await()
            respond("""{"error":"Synthetic sign-in unavailable. Try again."}""", HttpStatusCode.ServiceUnavailable,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = PortfolioApi("https://auth-ui.example.test", http)
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = error("No PDF in this fixture")
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external navigation")
            override fun shareText(text: String) = Unit
        }
        try {
            UsabilityPreferences().use {
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    lateinit var model: PortfolioModel
                    scene.onActivity { host ->
                        model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, apiFactory = { api }, deferAccountStartup = true)
                        host.viewModelStore.put("auth-ui", model)
                        AppLanguage.initialize(settings)
                        host.setContent { CompositionLocalProvider(LocalImportEngine provides engine, LocalNativeActions provides actions) { InvestmentApp(model) } }
                    }
                    ui.scroll("Continue as Guest"); ui.click("Continue as Guest")
                    ui.await("Explore fictional sample"); ui.await("Start personal portfolio")
                    ui.click("Cancel"); ui.await("Email")
                    assertNull(model.guestPath); assertEquals(0, requests.get())
                    ui.enter("Email", "synthetic@example.test")
                    ui.enter("Password", "synthetic-only-password")
                    ui.scroll("Sign in"); ui.click("Sign in")
                    ui.await("Signing in…")
                    ui.waitUntil("one blocked request") { requests.get() == 1 }
                    assertTrue(model.busy)
                    // The loading action must be disabled and must not enqueue another request.
                    var button = ui.await("Signing in…")
                    while (!button.isClickable && button.parent != null) button = button.parent
                    assertFalse(button.isEnabled)
                    gates[0].complete(Unit)
                    ui.waitUntil("recoverable sign-in error") { !model.busy && model.error != null }
                    ui.await(requireNotNull(model.error))
                    ui.scroll("Sign in"); ui.click("Sign in")
                    ui.await("Signing in…")
                    ui.waitUntil("retry without re-entering credentials") { requests.get() == 2 }
                    gates[1].complete(Unit)
                    ui.waitUntil("retry finished") { !model.busy && model.error != null }
                    assertNull(model.user); assertEquals(2, requests.get())
                }
            }
        } finally { gates.forEach { it.complete(Unit) }; http.close() }
    }
}

package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.os.Build
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import dev.orestegabo.portfolio.network.PortfolioApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Synthetic vault/network only. No real biometrics, account data, providers or saved credentials. */
class UnlockUiQualityTest {
    private val origin = "https://unlock-ui.example.test"
    private fun settings(language: String = "en") = object : ServerSettings {
        override fun readOrigin() = origin
        override fun writeOrigin(origin: String) = Unit
        override fun readLanguage() = language
        override fun readOnboardingCompleted() = true
        override fun readScreenCaptureAllowed() = true
    }
    private fun vault(label: String = "Fingerprint", read: suspend () -> String?) = object : SecureSignIn {
        override val available = true
        override val unlockLabel = label
        override fun hasSaved(origin: String) = true
        override suspend fun read(origin: String) = read()
        override suspend fun save(origin: String, value: String) = error("No credentials may be saved in this fixture")
        override fun delete(origin: String) = Unit
        override fun lock() = Unit
    }
    private val engine = object : InvestmentEngine {
        override var onProgress: (String) -> Unit = {}
        override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = error("No document operation")
        override fun cancel() = Unit
        override fun destroy() = Unit
    }
    private val actions = object : NativeActions {
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
        override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
        override fun openUrl(url: String) = error("No external navigation")
        override fun shareText(text: String) = Unit
    }

    @Test fun lockedActionsRemainPrivateAndRecoverFromCancelledUnlockAndSlowVerification() {
        val ui = UsabilityTestDriver()
        val reads = AtomicInteger()
        val requests = AtomicInteger()
        val cancelled = AtomicBoolean(false)
        val vaultGates = listOf(CompletableDeferred<String?>(), CompletableDeferred<String?>())
        val networkGate = CompletableDeferred<Unit>()
        val http = MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("/auth/login"))
            requests.incrementAndGet()
            try { networkGate.await() } finally { cancelled.set(true) }
            respond("{}", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            UsabilityPreferences().use {
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    lateinit var model: PortfolioModel
                    scene.onActivity { host ->
                        val preferences = settings()
                        model = PortfolioModel(preferences, vault = vault { vaultGates[reads.getAndIncrement()].await() },
                            assets = { "[]".encodeToByteArray() }, deferAccountStartup = true,
                            apiFactory = { PortfolioApi(it, http) }, captureMaskRequired = false)
                        host.viewModelStore.put("locked-ui", model)
                        AppLanguage.initialize(preferences)
                        host.setContent { CompositionLocalProvider(LocalImportEngine provides engine, LocalNativeActions provides actions) { InvestmentApp(model) } }
                    }
                    ui.await("Portfolio locked")
                    assertTrue(model.locked); assertNull(model.user)
                    assertEquals(0, requests.get()); assertEquals(0, reads.get())
                    assertFalse(ui.nodes().any { it.text?.toString() == "Your investments" })
                    ui.scroll("Legal & privacy"); ui.click("Legal & privacy")
                    ui.await("Terms of Service"); assertTrue(model.locked)
                    ui.click("Back"); ui.await("Portfolio locked")
                    ui.scroll("Continue as Guest"); ui.click("Continue as Guest")
                    ui.await("Explore fictional sample"); ui.click("Cancel")
                    assertNull(model.guestPath); assertTrue(model.locked)
                    ui.scroll("Unlock with Fingerprint"); ui.click("Unlock with Fingerprint")
                    ui.waitUntil("one native unlock request") { reads.get() == 1 && model.busy }
                    var button = ui.await("Unlocking…")
                    while (!button.isClickable && button.parent != null) button = button.parent
                    assertFalse(button.isEnabled)
                    // A cancelled native prompt leaves the portfolio locked and manual retry available.
                    vaultGates[0].complete(null)
                    ui.waitUntil("cancelled unlock returns to idle") { !model.busy }
                    assertTrue(model.locked); assertEquals(0, requests.get())
                    ui.click("Unlock with Fingerprint")
                    ui.waitUntil("second native unlock") { reads.get() == 2 }
                    vaultGates[1].complete(JsonRecord().put("origin", origin).put("email", "synthetic@example.test")
                        .put("password", "synthetic-only-password").put("session", "").toString())
                    ui.waitUntil("server verification waits") { model.verifyingSession && requests.get() == 1 }
                    ui.await("Device unlocked")
                    assertTrue(model.locked); assertNull(model.user)
                    ui.scroll("Use password instead"); ui.click("Use password instead")
                    ui.await("Email")
                    ui.waitUntil("verification is cancelled before password mode") { cancelled.get() && !model.locked && !model.busy }
                    assertNull(model.user); assertNull(model.guestPath); assertEquals(2, reads.get())
                }
            }
        } finally { vaultGates.forEach { it.complete(null) }; networkGate.complete(Unit); http.close() }
    }

    @Test fun lockedLayoutSupportsBothThemesFrenchAndLargeTextInAShortViewport() {
        val ui = UsabilityTestDriver()
        val prefs = ui.instrumentation.targetContext.getSharedPreferences("connection", 0)
        UsabilityPreferences().use {
            for ((theme, language, fontScale) in listOf(Triple("light", "en", 1f), Triple("dark", "en", 1f), Triple("light", "fr", 1.8f), Triple("dark", "fr", 1.8f))) {
                prefs.edit().putString("theme", theme).putString("language", language).commit()
                val http = MockEngine { error("Locked brand content must not make network requests") }
                try {
                    ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                        scene.onActivity { host ->
                            val preferences = settings(language)
                            val model = PortfolioModel(preferences, vault = vault("Face ID") { null },
                                assets = { "[]".encodeToByteArray() }, apiFactory = { PortfolioApi(it, http) },
                                deferAccountStartup = true, captureMaskRequired = false)
                            host.viewModelStore.put("locked-layout", model)
                            AppLanguage.initialize(preferences)
                            host.setContent {
                                val density = LocalDensity.current
                                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                                    LocalImportEngine provides engine, LocalNativeActions provides actions) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                        Box(if (fontScale > 1f) Modifier.width(360.dp).height(540.dp) else Modifier.fillMaxSize()) { InvestmentApp(model) }
                                    }
                                }
                            }
                        }
                        ui.await(if (language == "fr") "Portefeuille verrouillé" else "Portfolio locked")
                        for (label in if (language == "fr") listOf("Déverrouiller avec Face ID", "Utiliser le mot de passe", "Continuer en mode invité", "Mentions légales et confidentialité")
                            else listOf("Unlock with Face ID", "Use password instead", "Continue as Guest", "Legal & privacy")) {
                            ui.scroll(label)
                            var node = ui.await(label)
                            while (!node.isClickable && node.parent != null) node = node.parent
                            val bounds = Rect().also(node::getBoundsInScreen)
                            var density = 1f
                            scene.onActivity { density = it.resources.displayMetrics.density }
                            assertTrue("Action must retain a 48dp touch target: $label", bounds.height() >= (48 * density).toInt() - 1)
                            assertTrue(node.isEnabled)
                        }
                        ui.scroll(if (language == "fr") "Portefeuille verrouillé" else "Portfolio locked")
                        if (InstrumentationRegistry.getArguments().getString("uiAuditSnapshots") == "true") {
                            Thread.sleep(4_800)
                            val bitmap = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                            bitmap.writeGolden(File(ui.instrumentation.targetContext.cacheDir, "unlock-$theme-$language-$fontScale.png")); bitmap.recycle()
                        }
                    }
                } finally { http.close() }
            }
        }
    }

    @Test fun decorativeTurnRepeatsAndRespectsPauseReducedMotionAndBackgrounding() {
        val ui = UsabilityTestDriver()
        for (reduced in listOf(false, true)) {
            val bounds = AtomicReference<Rect>()
            val window = AtomicReference<Window>()
            ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                scene.onActivity { host ->
                    window.set(host.window)
                    AppLanguage.initialize(settings())
                    host.setContent {
                        MaterialTheme(colorScheme = structCapitalColorScheme(false)) {
                            Surface(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    UnlockBrandArtwork(Modifier.size(240.dp).onGloballyPositioned {
                                        val r = it.boundsInWindow(); bounds.set(Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()))
                                    }, reducedMotion = reduced)
                                    Text("Public brand artwork", Modifier.align(Alignment.BottomCenter))
                                }
                            }
                        }
                    }
                }
                ui.await("Public brand artwork")
                fun committedFrame() {
                    val gate = CountDownLatch(1)
                    scene.onActivity { host ->
                        val root = host.window.decorView
                        if (Build.VERSION.SDK_INT >= 29) { root.viewTreeObserver.registerFrameCommitCallback { gate.countDown() }; root.invalidate() }
                        else root.postOnAnimation { root.postOnAnimation { root.post { gate.countDown() } } }
                    }
                    assertTrue(gate.await(5, TimeUnit.SECONDS))
                }
                fun capture() = captureUiGolden(window.get(), requireNotNull(bounds.get()))
                fun changed(first: android.graphics.Bitmap, second: android.graphics.Bitmap): Int = try { goldenChangedPixels(first, second) }
                    finally { first.recycle(); second.recycle() }
                committedFrame()
                val first = capture(); Thread.sleep(400)
                val change = changed(first, capture())
                if (reduced) assertEquals("Reduced motion must be static", 0, change)
                else {
                    assertTrue("The 3D artwork must visibly turn", change > 200)
                    scene.moveToState(Lifecycle.State.STARTED); committedFrame()
                    val paused = capture(); Thread.sleep(340)
                    assertEquals("Backgrounding must pause the artwork", 0, changed(paused, capture()))
                    scene.moveToState(Lifecycle.State.RESUMED)
                    // Cross both reversal points: the requested motion keeps playing until paused.
                    Thread.sleep(12_800); committedFrame()
                    val repeating = capture(); Thread.sleep(400)
                    assertTrue("Back-and-forth motion must keep playing", changed(repeating, capture()) > 200)
                    ui.click("Pause logo animation"); ui.await("Play logo animation"); committedFrame()
                    val pausedByUser = capture(); Thread.sleep(400)
                    assertEquals("The pause control must stop decorative motion", 0, changed(pausedByUser, capture()))
                    scene.moveToState(Lifecycle.State.STARTED); scene.moveToState(Lifecycle.State.RESUMED); committedFrame()
                    val stillPaused = capture(); Thread.sleep(400)
                    assertEquals("User pause must survive backgrounding", 0, changed(stillPaused, capture()))
                    ui.click("Play logo animation"); ui.await("Pause logo animation")
                    Thread.sleep(700); committedFrame()
                    val resumed = capture(); Thread.sleep(400)
                    assertTrue("Play must resume the turn", changed(resumed, capture()) > 200)
                }
            }
        }
    }
}

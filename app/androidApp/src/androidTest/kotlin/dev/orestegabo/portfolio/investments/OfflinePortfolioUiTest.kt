package dev.orestegabo.portfolio.investments

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import dev.orestegabo.portfolio.network.PortfolioApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic auth boundary and HTTP only; native encrypted files survive a completely new model. */
class OfflinePortfolioUiTest {
    private class Vault(val storage: AndroidGuestSnapshotStorage) : SecureSignIn, ProtectedAccountPortfolio {
        override val available = true
        override val unlockLabel = "Face ID"
        var credentials: String? = null
        var authorized = false
        var cancelled = false
        var reads = 0
        var storedQuotes = false
        override fun hasSaved(origin: String) = credentials != null
        override suspend fun read(origin: String): String? {
            reads++
            if (cancelled) return null
            authorized = credentials != null
            return credentials
        }
        override suspend fun readUnlocked(origin: String) = credentials.takeIf { authorized }
        override suspend fun save(origin: String, value: String): Boolean { credentials = value; authorized = true; return true }
        override fun lock() { authorized = false }
        override fun delete(origin: String) { credentials = null; authorized = false; deleteAccountPortfolio(origin) }
        override suspend fun readAccountPortfolio(origin: String): ByteArray? { check(authorized); return storage.read() }
        override suspend fun writeAccountPortfolio(origin: String, bytes: ByteArray) {
            check(authorized); storage.write(bytes)
            storedQuotes = JsonRecord(bytes.decodeToString()).optJSONObject("quotes") != null
        }
        override fun deleteAccountPortfolio(origin: String) { runBlocking { storage.delete() } }
    }

    @Test fun coldOfflineUnlockShowsSavedValuesAndReconnectsInBothThemesAndLargeText() {
        val ui = UsabilityTestDriver()
        val context = ui.instrumentation.targetContext
        val native = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external navigation")
            override fun shareText(text: String) = error("No sharing")
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = error("No import")
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        val offline = AtomicBoolean(false)
        val closing = AtomicInteger(120)
        val requestCount = AtomicInteger()
        val settings = object : ServerSettings {
            override fun readOrigin() = "https://offline-ui.example.test"
            override fun writeOrigin(origin: String) = Unit
            override fun readLanguage() = "en"
            override fun readScreenCaptureAllowed() = true
        }
        UsabilityPreferences().use {
            for (dark in listOf(false, true)) {
                offline.set(false); closing.set(120)
                context.getSharedPreferences("connection", 0).edit().putString("theme", if (dark) "dark" else "light").commit()
                val name = "test-offline-ui-${System.nanoTime()}"
                val storage = AndroidGuestSnapshotStorage(context, name, directoryName = "offline-ui-fixtures")
                val vault = Vault(storage)
                fun model() = PortfolioModel(settings, assets = { "[]".encodeToByteArray() }, vault = vault,
                    deferAccountStartup = true, apiFactory = { origin -> PortfolioApi(origin, MockEngine { request ->
                        requestCount.incrementAndGet()
                        if (offline.get()) error("Synthetic offline transport")
                        val user = """{"id":"offline-ui","email":"offline@example.test"}"""
                        val body = when (request.url.encodedPath) {
                            "/api/bonds/auth/login" -> """{"user":$user}"""
                            "/api/bonds/auth/session" -> """{"authenticated":true,"user":$user}"""
                            "/api/bonds/purchases" -> """{"purchases":[{"id":"offline-bond","bondName":"Synthetic offline bond","currency":"RWF","status":"active","faceValue":1000000,"purchaseDate":"2025-01-01","maturityDate":"2035-01-01","couponRate":0.10,"couponFrequency":2,"withholdingTaxRate":0.05}]}"""
                            "/api/bonds/equities" -> """{"holdings":[{"id":"offline-share","ticker":"TEST","securityName":"Synthetic offline company","currency":"RWF","status":"active","shares":100,"pricePerShare":100,"netAmountPayable":10000,"tradeDate":"2025-01-01"}]}"""
                            "/api/bonds/share-orders" -> """{"orders":[]}"""
                            "/api/bonds/cash-ledger" -> """{"entries":[]}"""
                            "/api/bonds/rse/quotes" -> """{"source":{"publishedAt":"2000-01-02","stale":true},"quotes":[{"ticker":"TEST","name":"Synthetic offline company","closing":${closing.get()}}]}"""
                            "/api/bonds/documents" -> """{"documents":[]}"""
                            else -> "{}"
                        }
                        val headers = if (request.url.encodedPath.endsWith("auth/login")) headersOf(
                            HttpHeaders.ContentType to listOf("application/json"),
                            HttpHeaders.SetCookie to listOf("__Host-bonds_session=synthetic-offline-token; Path=/; Secure; HttpOnly; Max-Age=43200"))
                        else headersOf(HttpHeaders.ContentType, "application/json")
                        respond(body, headers = headers)
                    }) })
                fun show(scene: ActivityScenario<GuestExperienceHostActivity>, model: PortfolioModel) {
                    scene.onActivity { host -> host.setContent {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalNativeActions provides native, LocalImportEngine provides engine,
                            LocalDensity provides Density(density.density, if (dark) 1.6f else 1f)) { InvestmentApp(model) }
                    } }
                }
                try {
                    ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                        lateinit var first: PortfolioModel
                        scene.onActivity { host -> first = model(); host.viewModelStore.put("offline-first", first) }
                        ui.waitUntil("synthetic startup") { !first.busy }
                        scene.onActivity { first.login("offline@example.test", "synthetic-password-only", savePassword = true) }
                        ui.waitUntil("protected snapshot and quotes committed") { first.portfolioReady && !first.portfolioSyncing && !first.busy && vault.storedQuotes }
                        show(scene, first)
                        ui.await("1,012,000")
                        val ciphertext = File(context.noBackupFilesDir, "offline-ui-fixtures/$name.scg").readBytes()
                        assertFalse(ciphertext.decodeToString().contains("Synthetic offline bond"))
                    }
                    offline.set(true); vault.cancelled = true
                    ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                        lateinit var cached: PortfolioModel
                        scene.onActivity { host -> cached = model(); host.viewModelStore.put("offline-cold", cached) }
                        ui.waitUntil("cold locked startup") { !cached.busy }
                        show(scene, cached)
                        ui.await("Portfolio locked")
                        assertFalse(ui.nodes().any { ui.matches(it, "1,012,000") })
                        val requests = requestCount.get()
                        ui.click("Unlock with Face ID")
                        ui.waitUntil("cancelled biometric stays locked") { cached.locked && !cached.busy }
                        assertEquals(requests, requestCount.get())
                        assertFalse(cached.portfolioReady)
                        vault.cancelled = false
                        ui.click("Unlock with Face ID")
                        ui.await("Saved portfolio"); ui.await("1,012,000")
                        ui.click("Saved portfolio")
                        ui.await("Read-only · May be out of date")
                        ui.click("Close")
                        assertFalse(cached.canEditInvestments)
                        val image = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                        try { image.writeGolden(File(context.cacheDir, "offline-portfolio-$dark.png")) } finally { image.recycle() }
                        scene.onActivity { cached.toggleNumberVisibility() }
                        ui.waitUntil("cached amounts still obey privacy") { ui.nodes().none { ui.matches(it, "1,012,000") } }
                        scene.onActivity { cached.toggleNumberVisibility() }
                        ui.await("1,012,000")
                        val prompts = vault.reads
                        offline.set(false); closing.set(125)
                        ui.scroll("Saved portfolio"); ui.click("Saved portfolio"); ui.click("Retry connection")
                        ui.waitUntil("online authorization and snapshot refresh") { !cached.viewingSavedPortfolio && !cached.portfolioSyncing && !cached.busy }
                        ui.await("1,012,500")
                        assertTrue(cached.canEditInvestments); assertEquals(prompts, vault.reads)

                    }
                } finally { runBlocking { storage.delete() } }
            }
        }
    }
}

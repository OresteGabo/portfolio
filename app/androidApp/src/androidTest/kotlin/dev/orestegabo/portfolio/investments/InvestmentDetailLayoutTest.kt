package dev.orestegabo.portfolio.investments

import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.MainActivity
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

/** Synthetic local layouts only; run on an isolated emulator, never against a real portfolio. */
class InvestmentDetailLayoutTest {
    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun investmentOverviewsRenderInBothThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val original = prefs.getString("origin", null)
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val bond = JsonRecord("""{"id":"layout-bond","bondName":"Rwanda Treasury Bond","isin":"RW-SYNTHETIC-2033","status":"active","currency":"RWF","faceValue":2200000,"purchaseDate":"2026-07-01","maturityDate":"2033-07-01","couponRate":0.115,"withholdingTaxRate":0.05,"couponFrequency":2,"firstCouponDate":"2027-01-01","scheduleConfidence":"Estimated"}""")
        val holdings = """[{"id":"layout-share","ticker":"IMR","securityName":"I&M Bank Rwanda","currency":"RWF","status":"active","tradeDate":"2026-07-01","shares":400,"pricePerShare":90,"grossConsideration":36000,"totalCharges":615.60,"netAmountPayable":36615.60}]"""
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try { server.accept().use { client ->
                val reader = client.getInputStream().bufferedReader()
                val path = reader.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                while (!reader.readLine().isNullOrEmpty()) { }
                val body = when {
                    path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"layout-only","email":"layout@example.test","isOwner":true}}"""
                    path.endsWith("/purchases") -> """{"purchases":[$bond]}"""
                    path.endsWith("/equities") -> """{"holdings":$holdings}"""
                    path.contains("/rse/quotes") -> """{"quotes":[{"ticker":"IMR","closing":100}],"source":{"publishedAt":"$Today"}}"""
                    path.endsWith("/documents") -> """{"documents":[]}"""
                    path.endsWith("/statement-items") -> """{"items":[]}"""
                    else -> "{}"
                }
                val bytes = body.encodeToByteArray()
                client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
            } } catch (_: Exception) { }
        }
        val settings = object : ServerSettings {
            override fun readOrigin() = origin
            override fun writeOrigin(origin: String) = Unit
            override fun readLanguage() = "en"
            override fun readScreenCaptureAllowed() = true
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?) = error("Not part of layout test")
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        val share = mutableStateOf(false)
        val dark = mutableStateOf(false)
        try {
            prefs.edit().putString("origin", origin).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: PortfolioModel
                var window: android.view.Window? = null
                scenario.onActivity { activity ->
                    window = activity.window
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    model = PortfolioModel(settings, assets = { "[]".encodeToByteArray() })
                    activity.viewModelStore.put("investment-layout", model)
                    AppLanguage.initialize(settings)
                    activity.setContent {
                        val actions = rememberNativeActions()
                        CompositionLocalProvider(LocalNativeActions provides actions, LocalNumbersHidden provides false) {
                            val colors = if (dark.value) darkColorScheme(primary = Color(0xFFF2E6A7), secondaryContainer = Color(0xFF514716), background = Color(0xFF11150F), surface = Color(0xFF1D2219), onSurface = Color(0xFFF3F1E8))
                                else lightColorScheme(primary = Color(0xFF6D5E0F), secondaryContainer = Color(0xFFF2E6A7), background = Color(0xFFFAF8F2), surface = Color.White)
                            MaterialTheme(colorScheme = colors) {
                                Scaffold(topBar = { TopAppBar(title = { AppText(if (share.value) "Share details" else "Bond details") }) }) { padding ->
                                    Box(Modifier.fillMaxSize().padding(padding)) {
                                        if (share.value) ShareDetailsScreen(model, JsonRecord().put("ticker", "IMR").put("currency", "RWF"))
                                        else BondDetailsScreen(model, bond, engine, {}, {}, {})
                                    }
                                }
                            }
                        }
                        NativePrivacyCover(false)
                    }
                }
                val deadline = System.currentTimeMillis() + 15000
                var ready = false
                while (!ready && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { ready = model.user != null && model.portfolioReady && !model.busy }
                    Thread.sleep(100)
                }
                assertTrue("Synthetic session loads", ready)
                for (isShare in listOf(false, true)) for (isDark in listOf(false, true)) {
                    scenario.onActivity { share.value = isShare; dark.value = isDark }
                    instrumentation.waitForIdleSync(); Thread.sleep(1200)
                    fun collect(node: android.view.accessibility.AccessibilityNodeInfo): String = buildString {
                        append(node.text).append(' ').append(node.contentDescription).append(' ')
                        for (i in 0 until node.childCount) node.getChild(i)?.let { append(collect(it)) }
                    }
                    val filename = "detail-${if (isShare) "share" else "bond"}-${if (isDark) "dark" else "light"}.png"
                    var text = ""
                    repeat(30) {
                        text = instrumentation.uiAutomation.rootInActiveWindow?.let(::collect).orEmpty()
                        if (!text.contains("Overview")) Thread.sleep(200)
                    }
                    File(instrumentation.targetContext.filesDir, "detail-state.txt").writeText(text)
                    scenario.onActivity { File(instrumentation.targetContext.filesDir, "detail-model.txt").writeText("holdings=" + model.holdings.joinToString() + " error=" + model.error) }
                    readerFixtureScreenshot(checkNotNull(window), File(instrumentation.targetContext.filesDir, filename))
                    assertTrue("Overview tab must be visible ($filename)", text.contains("Overview"))
                    assertTrue("Instrument identity must be visible ($filename)", text.contains(if (isShare) "IMR" else "Rwanda Treasury Bond"))
                    assertTrue(File(instrumentation.targetContext.filesDir, filename).length() > 1000)
                }
            }
        } finally {
            server.close(); worker.shutdownNow()
            prefs.edit().apply { if (original == null) remove("origin") else putString("origin", original) }.commit()
        }
    }
}

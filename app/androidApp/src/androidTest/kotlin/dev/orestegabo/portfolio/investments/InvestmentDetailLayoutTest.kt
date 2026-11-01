package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.MainActivity
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Synthetic localhost account only. Run with an isolated, unlocked emulator. */
class InvestmentDetailLayoutTest {
    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun investmentOverviewsRenderInBothThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val profile = args.getString("profile") ?: "normal"
        val mode = args.getString("quoteMode") ?: "fresh"
        val language = args.getString("language") ?: "en"
        val large = profile == "large"
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val original = prefs.getString("origin", null)
        val oldLanguage = AppLanguage.preference
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val bond = JsonRecord("""{"id":"layout-bond","bondName":"Rwanda Treasury Bond","issuer":"National Bank of Rwanda","isin":"RW-SYNTHETIC-2033","status":"active","currency":"RWF","faceValue":2200000,"purchaseDate":"2025-01-01","maturityDate":"2033-07-01","couponRate":0.115,"withholdingTaxRate":0.05,"couponFrequency":2,"firstCouponDate":"2025-07-01","scheduleConfidence":"estimated","confirmationDocumentId":"layout-pdf"}""")
        if (large) bond.put("faceValue", 2200000000L).put("bondName", "Rwanda Treasury Bond — long instrument name for accessibility verification")
        val holding = JsonRecord("""{"id":"layout-share","ticker":"IMR","securityName":"I&M Bank Rwanda","currency":"RWF","status":"active","exchange":"RSE","tradeDate":"2026-07-01","shares":400,"pricePerShare":90,"grossConsideration":36000,"totalCharges":615.60,"netAmountPayable":36615.60,"contractDocumentId":"layout-pdf"}""")
        if (large) holding.put("shares", 40000000).put("grossConsideration", 3600000000L).put("totalCharges",61560000).put("netAmountPayable",3661560000L)
            .put("securityName","I&M Bank Rwanda — long company name for accessibility verification")
        val receipts = """[{"purchaseId":"layout-bond","currency":"RWF","dueDate":"2026-01-01","received":true,"method":"manual","amount":120175},{"purchaseId":"layout-bond","currency":"RWF","dueDate":"2026-07-01","received":true,"method":"automatic","amount":120175}]"""
        val receiptRecords = JsonRecord("""{"receipts":$receipts}""").rows("receipts").toMutableList()
        val couponWrites = AtomicInteger()
        val pdf = readerFixturePdf(pageCount = 1)
        val downloads = AtomicInteger()
        val pdfRequests = AtomicInteger()
        val deletes = AtomicInteger()
        val edits = AtomicInteger()
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try { server.accept().use { client ->
                val reader = client.getInputStream().bufferedReader()
                val line = reader.readLine().orEmpty().split(' ')
                val path = line.getOrNull(1).orEmpty()
                var length = 0
                while (true) {
                    val header = reader.readLine().orEmpty(); if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                }
                val input = CharArray(length); var offset = 0
                while (offset < length) { val n = reader.read(input, offset, length-offset); if (n < 0) break; offset += n }
                if (line.firstOrNull() == "POST" && path.endsWith("/notifications/coupons")) {
                    val event = JsonRecord(String(input))
                    val row = receiptRecords.first { it.text("dueDate") == event.text("dueDate") && it.text("purchaseId") == event.text("purchaseId") }
                    row.put("received", event.optBoolean("received")).put("method", "manual")
                    couponWrites.incrementAndGet()
                }
                if (line.firstOrNull() == "DELETE") deletes.incrementAndGet()
                val isPdf = path.endsWith("/documents/layout-pdf")
                if (isPdf) pdfRequests.incrementAndGet()
                val body = when {
                    path.endsWith("/auth/session") -> """{"authenticated":true,"user":{"id":"layout-only","email":"layout@example.test","isOwner":true}}"""
                    path.endsWith("/purchases") -> """{"purchases":[$bond]}"""
                    path.endsWith("/equities") -> """{"holdings":[$holding]}"""
                    path.contains("/rse/quotes") -> """{"quotes":[{"ticker":"IMR","closing":${if (mode == "missing") 0 else 100}}],"source":{"publishedAt":"$Today","stale":${mode == "stale"}}}"""
                    path.endsWith("/documents") -> """{"documents":[{"id":"layout-pdf","label":"Original investment document","originalFileName":"synthetic.pdf","documentDate":"2026-07-01"}]}"""
                    path.endsWith("/statement-items") -> """{"items":[]}"""
                    path.endsWith("/notifications/coupons") -> JsonRecord().put("receipts", JsonList(receiptRecords)).toString()
                    path.endsWith("/notifications") -> """{"notifications":[],"serverDate":"$Today"}"""
                    else -> "{}"
                }
                val bytes = if (isPdf) pdf else body.encodeToByteArray()
                val mime = if (isPdf) "application/pdf" else "application/json"
                client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
            } } catch (_: Exception) { }
        }
        val settings = object : ServerSettings {
            override fun readOrigin() = origin
            override fun writeOrigin(origin: String) = Unit
            override fun readLanguage() = language
            override fun readScreenCaptureAllowed() = true
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?) = error("No PDF import in layout test")
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray): Boolean = error("Use share/save")
            override suspend fun sharePdf(fileName: String, bytes: ByteArray): Boolean { assertTrue(bytes.size > 5); downloads.incrementAndGet(); return true }
            override fun openUrl(url: String) = Unit
            override fun shareText(text: String) = Unit
        }
        val share = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val composition = mutableIntStateOf(0)
        fun visible(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.isVisibleToUser && predicate(node)) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it)?.let { found -> return found } }
                return null
            }
            return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        }
        fun find(label: String) = visible { it.text?.toString() == label || it.contentDescription?.toString() == label }
        fun await(label: String): AccessibilityNodeInfo {
            repeat(50) { find(label)?.let { return it }; Thread.sleep(100) }
            throw AssertionError("Missing visible control: $label ($profile/$mode/$language)")
        }
        fun scrollTo(label: String) {
            repeat(20) {
                if (find(label) != null) return
                visible { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                instrumentation.waitForIdleSync(); Thread.sleep(150)
            }
            await(label)
        }
        fun tap(label: String) {
            val bounds = Rect().also(await(label)::getBoundsInScreen)
            val down = android.os.SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0).also {
                instrumentation.sendPointerSync(it); it.recycle()
            }
            instrumentation.waitForIdleSync(); Thread.sleep(200)
        }
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
                        CompositionLocalProvider(LocalNativeActions provides actions, LocalNumbersHidden provides false) {
                            MaterialTheme(colorScheme = structCapitalColorScheme(dark.value)) {
                                Scaffold(topBar = { TopAppBar(title = { AppText(if (share.value) "Share details" else "Bond details") }) }) { padding ->
                                    Box(Modifier.fillMaxSize().padding(padding)) { key(composition.intValue) {
                                        if (share.value) ShareDetailsScreen(model, JsonRecord().put("ticker", "IMR").put("currency", "RWF"))
                                        else BondDetailsScreen(model, bond, engine, {}, {}, { edits.incrementAndGet() })
                                    } }
                                }
                            }
                        }
                        NativePrivacyCover(false)
                    }
                }
                val deadline = System.currentTimeMillis() + 15000
                var ready = false
                while (!ready && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { ready = model.user != null && model.portfolioReady && !model.busy && model.quotes != null }
                    Thread.sleep(100)
                }
                assertTrue("Synthetic portfolio and quote response load", ready)
                fun capture(name: String) {
                    instrumentation.waitForIdleSync(); Thread.sleep(300)
                    readerFixtureScreenshot(checkNotNull(window), File(instrumentation.targetContext.filesDir, "$profile-$mode-$language-$name.png"))
                }
                for (isShare in listOf(false, true)) for (isDark in listOf(false, true)) {
                    scenario.onActivity { share.value = isShare; dark.value = isDark; composition.intValue++ }
                    instrumentation.waitForIdleSync(); Thread.sleep(700)
                    val prefix = if (isShare) "I&M Bank Rwanda" else "Rwanda Treasury Bond"
                    repeat(50) {
                        if (visible { node -> node.text?.toString()?.contains(prefix) == true } == null) Thread.sleep(100)
                    }
                    capture("${if (isShare) "share" else "bond"}-${if (isDark) "dark" else "light"}")
                    if (large) {
                        val numeric = formatNumber(if (isShare) 4000000000.0 else 2200000000.0)
                        scrollTo(numeric)
                        capture("${if (isShare) "share" else "bond"}-${if (isDark) "dark" else "light"}-value")
                    }
                    fun collect(node: AccessibilityNodeInfo): String = buildString {
                        append(node.text).append(' ').append(node.contentDescription).append(' ')
                        for (i in 0 until node.childCount) node.getChild(i)?.let { append(collect(it)) }
                    }
                    File(instrumentation.targetContext.filesDir, "redesign-state.txt").writeText(instrumentation.uiAutomation.rootInActiveWindow?.let(::collect).orEmpty())
                    assertNotNull("Instrument identity visible ($prefix)", visible { it.text?.toString()?.contains(prefix) == true })
                }
                if (profile == "normal" && mode == "fresh" && language == "en") {
                    scenario.onActivity { share.value = false; dark.value = false; composition.intValue++ }
                    instrumentation.waitForIdleSync(); Thread.sleep(500)
                    tap("Documents"); await("Original investment document")
                    tap("Download Original investment document")
                    assertEquals(1, downloads.get())
                    tap("Original investment document")
                    assertEquals("Document preview fetches the original private PDF", 2, pdfRequests.get())
                    tap("Documents")
                    scrollTo("Past"); tap("Past"); scrollTo("Marked automatically")
                    capture("bond-receipt-ledger")
                    tap("Confirm received 1 Jul 2026")
                    repeat(50) { if (couponWrites.get() < 1) Thread.sleep(100) }
                    scenario.onActivity { assertEquals("manual", model.couponReceipt("layout-bond", "2026-07-01")?.text("method")) }
                    scrollTo("Mark not received 1 Jul 2026")
                    tap("Mark not received 1 Jul 2026")
                    repeat(50) { if (couponWrites.get() < 2) Thread.sleep(100) }
                    scenario.onActivity { assertEquals(false, model.couponReceipt("layout-bond", "2026-07-01")?.optBoolean("received")) }
                    scrollTo("Marked not received")
                    scrollTo("Edit purchase"); tap("Edit purchase"); assertEquals(1, edits.get())
                    tap("Delete purchase"); await("Delete purchase?"); tap("Keep purchase"); assertEquals(0, deletes.get())
                    scenario.onActivity { share.value = true; composition.intValue++ }
                    instrumentation.waitForIdleSync(); Thread.sleep(500)
                    scrollTo("Purchase breakdown"); capture("share-purchase-ledger")
                    tap("Purchase breakdown"); scrollTo("Trade date"); assertNotNull(find("Trade date"))
                }
                if (mode == "missing") {
                    // Header clearly uses cost, not a zero-price valuation or invented loss.
                    scenario.onActivity { share.value = true; composition.intValue++ }
                    instrumentation.waitForIdleSync(); Thread.sleep(500)
                    assertNotNull(find(localized("Purchase cost")))
                }
            }
        } finally {
            server.close(); worker.shutdownNow()
            prefs.edit().apply { if (original == null) remove("origin") else putString("origin", original) }.commit()
            AppLanguage.select(oldLanguage)
        }
    }
}

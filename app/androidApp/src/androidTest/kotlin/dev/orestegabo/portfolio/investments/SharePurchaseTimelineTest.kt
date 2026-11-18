package dev.orestegabo.portfolio.investments

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Different dates, prices and documents ensure company-level grouping never mixes purchase evidence. */
class SharePurchaseTimelineTest {
    private val syntheticActions = object : NativeActions {
        override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
        override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
        override suspend fun sharePdf(fileName: String, bytes: ByteArray) = true
        override fun openUrl(url: String) = error("No external URL in this synthetic fixture")
        override fun shareText(text: String) = error("No external text sharing")
    }

    @Test fun pendingPurchasesExposeRequestDatesAndBecomeActualDatedPurchasesAfterRefresh() {
        val ui = UsabilityTestDriver()
        fun holding(id: String, date: String, price: Int, cost: Double) = JsonRecord()
            .put("id", id).put("ticker", "TEST").put("securityName", "Synthetic UI company")
            .put("currency", "RWF").put("status", "active").put("shares", 100).put("pricePerShare", price)
            .put("grossConsideration", 100 * price).put("netAmountPayable", cost).put("totalCharges", cost - 100 * price)
            .put("tradeDate", date).put("settlementDate", date)
        val pending = JsonRecord().put("id", "pending-one").put("ticker", "TEST").put("securityName", "Synthetic UI company")
            .put("currency", "RWF").put("status", "pending").put("shares", 100).put("pricePerShare", 100)
            .put("requestDate", "2000-01-10").put("estimatedTotal", 10171).put("estimatedCommission", 171).put("version", 2)
        val actual = holding("executed", "2000-01-02", 101, 10272.71).put("contractDocumentId", "execution-doc")
        val doc = JsonRecord().put("id", "execution-doc").put("label", "Executed contract").put("originalFileName", "executed.pdf")
        UsabilityPreferences().use {
            UiQualityPortfolioFixture(purchaseRecords = listOf(holding("older", "2000-01-04", 90, 9000.0)),
                documentRecords = listOf(doc), pendingRecords = listOf(pending)).use { fixture ->
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    val managed = AtomicReference<JsonRecord>()
                    val uploaded = AtomicReference<String>()
                    val generation = mutableIntStateOf(0)
                    scene.onActivity { host -> host.viewModelStore.put("pending-history", fixture.model); fixture.start() }
                    fixture.ready(ui) { block -> scene.onActivity { block() } }
                    scene.onActivity {
                        assertEquals(1, fixture.model.portfolio.shareOrders.size)
                        assertEquals(19171.0, fixture.model.portfolio.summary(Today).single().shareCashCost, 0.0)
                    }
                    for ((dark, fontScale) in listOf(false to 1f, true to 1.8f)) {
                        scene.onActivity { host ->
                            generation.intValue++
                            host.setContent { key(generation.intValue) {
                                val density = LocalDensity.current
                                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                                    LocalNumbersHidden provides false, LocalNativeActions provides syntheticActions) {
                                    MaterialTheme(colorScheme = structCapitalColorScheme(dark)) {
                                        Surface(Modifier.fillMaxSize()) {
                                            ShareDetailsScreen(fixture.model, fixture.selected, managePending = { managed.set(it) },
                                                uploadPendingContract = { uploaded.set(it.text("id")) }) {}
                                        }
                                    }
                                }
                            } }
                        }
                        ui.scroll("Request date · 10 Jan 2000"); ui.await("Pending")
                        ui.scroll("Estimated total RWF 10,171")
                        var row = ui.await("100 shares × RWF 100")
                        while (!row.isClickable && row.parent != null) row = row.parent
                        fun text(node: android.view.accessibility.AccessibilityNodeInfo): String = buildString {
                            append(node.text)
                            for (i in 0 until node.childCount) node.getChild(i)?.let { append(text(it)) }
                        }
                        assertFalse("A pending request has no realized purchase return", text(row).contains("since purchase"))
                        ui.click("100 shares × RWF 100"); assertEquals("pending-one", managed.get().text("id"))
                        ui.scroll("Upload contract"); ui.click("Upload contract"); assertEquals("pending-one", uploaded.get())
                        ui.instrumentation.waitForIdleSync()
                        val image = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                        try { image.writeGolden(File(ui.instrumentation.targetContext.cacheDir, "pending-history-$dark-$fontScale.png")) }
                        finally { image.recycle() }
                    }
                    ui.scroll("Add pending purchase"); ui.click("Add pending purchase")
                    assertEquals("", managed.get().text("id")); assertEquals("TEST", managed.get().text("ticker"))
                    assertEquals("RWF", managed.get().text("currency")); assertEquals(100, managed.get().optInt("shares"))
                    scene.onActivity { fixture.publishExecutedOrder("pending-one", actual); fixture.model.refresh() }
                    ui.waitUntil("pending request replaced by the server-confirmed purchase") {
                        !fixture.model.busy && fixture.model.historyComplete && fixture.model.shareOrders.isEmpty() && fixture.model.holdings.size == 2
                    }
                    ui.scroll("2 Jan 2000"); ui.scroll("100 shares × RWF 101")
                    ui.scroll("Total paid RWF 10,272.71"); ui.scroll("Executed contract")
                    assertFalse(ui.nodes().any { ui.matches(it, "Request date · 10 Jan 2000") || ui.matches(it, "Upload contract") })
                    scene.onActivity { assertEquals(19272.71, fixture.model.portfolio.summary(Today).single().shareCashCost, 0.0) }
                }
            }
        }
    }

    @Test fun datedPurchaseTilesKeepTheirOwnPricesReturnsDocumentsAndPrivacy() {
        val ui = UsabilityTestDriver()
        val actions = syntheticActions
        fun lot(id: String, date: String, quantity: Int, price: Int, doc: String? = null) = JsonRecord()
            .put("id", id).put("ticker", "TEST").put("securityName", "Synthetic UI company")
            .put("currency", "RWF").put("status", "active").put("shares", quantity).put("pricePerShare", price)
            .put("grossConsideration", quantity * price).put("netAmountPayable", quantity * price).put("totalCharges", 0)
            .put("tradeDate", date).put("settlementDate", date).apply { doc?.let { put("contractDocumentId", it) } }
        val latest = lot("latest", "2000-01-01", 100, 90, "contract-a")
        val middle = lot("middle", "1999-12-11", 100, 100)
        val oldest = lot("oldest", "1999-12-01", 200, 110, "contract-b")
        val docs = listOf("a", "b").map { JsonRecord().put("id", "contract-$it")
            .put("label", "Contract ${it.uppercase()}").put("originalFileName", "$it.pdf") }
        UsabilityPreferences().use {
            UiQualityPortfolioFixture(purchaseRecords = listOf(oldest, latest, middle), documentRecords = docs).use { fixture ->
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    val opened = AtomicReference<String>()
                    val hidden = mutableStateOf(false)
                    val generation = mutableIntStateOf(0)
                    scene.onActivity { host -> host.viewModelStore.put("timeline", fixture.model); fixture.start() }
                    fixture.ready(ui) { block -> scene.onActivity { block() } }
                    fun capture(name: String) {
                        ui.instrumentation.waitForIdleSync(); Thread.sleep(250)
                        val bitmap = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                        try { bitmap.writeGolden(File(ui.instrumentation.targetContext.cacheDir, "share-timeline-$name.png")) }
                        finally { bitmap.recycle() }
                    }
                    for ((dark, fontScale) in listOf(false to 1f, true to 1f, false to 1.8f, true to 1.8f)) {
                        val name = "${if (dark) "dark" else "light"}-$fontScale"
                        scene.onActivity { host ->
                            generation.intValue++
                            host.setContent {
                                key(generation.intValue) {
                                    val density = LocalDensity.current
                                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                                        LocalNumbersHidden provides hidden.value, LocalNativeActions provides actions) {
                                        MaterialTheme(colorScheme = structCapitalColorScheme(dark)) {
                                            Surface(Modifier.fillMaxSize()) {
                                                ShareDetailsScreen(fixture.model, fixture.selected) { opened.set(it.text("id")) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        ui.await("Synthetic UI company")
                        assertFalse("Overview document toggle removed", ui.nodes().any { ui.matches(it, "Documents") })
                        capture("$name-overview")
                        for (text in listOf("1 Jan 2000", "100 shares × RWF 90", "Total paid RWF 9,000", "+11.11% · since purchase", "Contract A")) ui.scroll(text)
                        if (!dark && fontScale == 1f) {
                            ui.click("100 shares × RWF 90")
                            assertEquals("latest", opened.get())
                            ui.click("Contract A")
                            ui.waitUntil("latest purchase document fetched") { fixture.requestedDocuments.contains("contract-a") && !fixture.model.busy }
                            assertEquals(listOf("contract-a"), fixture.requestedDocuments.toList())
                        }
                        for (text in listOf("11 Dec 1999", "100 shares × RWF 100", "0% · since purchase", "No linked document",
                            "1 Dec 1999", "200 shares × RWF 110", "Total paid RWF 22,000", "−9.09% · since purchase", "Contract B")) ui.scroll(text)
                        capture("$name-history")
                        if (!dark && fontScale == 1f) {
                            ui.click("200 shares × RWF 110")
                            assertEquals("oldest", opened.get())
                            ui.click("Contract B")
                            ui.waitUntil("oldest purchase document fetched") { fixture.requestedDocuments.contains("contract-b") && !fixture.model.busy }
                            assertEquals(listOf("contract-a", "contract-b"), fixture.requestedDocuments.toList())
                        }
                    }
                    scene.onActivity { hidden.value = true }
                    ui.scroll("1 Jan 2000"); ui.await("Financial numbers hidden")
                    assertFalse(ui.nodes().any { it.isVisibleToUser && listOf("11.11%", "9,000", "100 shares").any { value -> it.text?.contains(value) == true } })
                }
            }
        }
    }
}

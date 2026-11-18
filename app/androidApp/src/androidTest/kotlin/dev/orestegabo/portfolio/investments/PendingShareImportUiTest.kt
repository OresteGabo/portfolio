package dev.orestegabo.portfolio.investments

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** General upload requires an explicit choice before completing a matching pending order. */
class PendingShareImportUiTest {
    @Test fun generalUploadAsksBeforeReplacingAPendingPurchase() {
        val ui = UsabilityTestDriver()
        val fields = JsonRecord().put("ticker", "TEST").put("securityName", "Synthetic UI company").put("currency", "RWF")
            .put("shares", 100).put("pricePerShare", 100).put("grossConsideration", 10000).put("totalCharges", 171)
            .put("netAmountPayable", 10171).put("tradeDate", "2000-01-18").put("settlementDate", "2000-01-20")
            .put("broker", "Synthetic broker").put("contractNote", "SYNTHETIC-001")
        val review = JsonRecord().put("kind", "equity").put("fields", fields).put("missingFields", JsonList()).put("warnings", JsonList())
        val pending = JsonRecord(fields.toString()).put("id", "pending-one").put("status", "pending")
            .put("requestDate", "2000-01-10").put("version", 2).put("estimatedTotal", 10171)
        val pdf = readerFixturePdf()
        val actions = object : NativeActions {
            override val pickedPdfName = "synthetic-contract.pdf"
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray { onSelected(); return pdf }
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external navigation")
            override fun shareText(text: String) = error("No external sharing")
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?) = review
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        UsabilityPreferences().use {
            UiQualityPortfolioFixture(lotCount = 0, pendingRecords = listOf(pending)).use { fixture ->
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    scene.onActivity { host -> host.viewModelStore.put("pending-import", fixture.model); fixture.start() }
                    fixture.ready(ui) { block -> scene.onActivity { block() } }
                    scene.onActivity { host -> host.setContent {
                        CompositionLocalProvider(LocalNativeActions provides actions, LocalImportEngine provides engine) {
                            InvestmentApp(fixture.model)
                        }
                    } }
                    ui.scroll("Add purchase"); ui.click("Add purchase")
                    ui.scroll("Is this your pending purchase?")
                    ui.scroll("Save purchase and PDF")
                    fun saveEnabled(label: String): Boolean {
                        var node = ui.await(label)
                        while (!node.isClickable && node.parent != null) node = node.parent
                        return node.isEnabled
                    }
                    assertFalse(saveEnabled("Save purchase and PDF"))
                    assertTrue(fixture.confirmedOrders.isEmpty()); assertEquals(0, fixture.createdSharePurchases.get())
                    ui.scroll("This is a separate purchase"); ui.click("This is a separate purchase")
                    ui.scroll("Save purchase and PDF"); assertTrue(saveEnabled("Save purchase and PDF"))
                    ui.scroll("TEST · 100 shares"); ui.click("TEST · 100 shares")
                    ui.scroll("Save PDF and confirm purchase"); assertTrue(saveEnabled("Save PDF and confirm purchase"))
                    ui.instrumentation.waitForIdleSync()
                    val image = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                    try { image.writeGolden(File(ui.instrumentation.targetContext.cacheDir, "pending-import-match.png")) }
                    finally { image.recycle() }
                    ui.click("Save PDF and confirm purchase")
                    ui.waitUntil("pending purchase replaced, not duplicated") {
                        !fixture.model.busy && fixture.model.shareOrders.isEmpty() && fixture.model.holdings.size == 1
                    }
                    assertEquals(listOf("pending-one"), fixture.confirmedOrders.toList())
                    assertEquals(0, fixture.createdSharePurchases.get())
                    assertEquals("2000-01-18", fixture.model.holdings.single().text("tradeDate"))
                    assertEquals("import-doc", fixture.model.holdings.single().text("contractDocumentId"))
                }
            }
        }
    }
}

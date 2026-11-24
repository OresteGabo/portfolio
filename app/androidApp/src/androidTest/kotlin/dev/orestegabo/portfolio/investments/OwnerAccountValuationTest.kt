package dev.orestegabo.portfolio.investments

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OwnerAccountValuationTest {
    @Test fun ownerAccountsShowSeparateTotalsExpandableBreakdownsAndRespectNumberHiding() {
        val ui = UsabilityTestDriver()
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external URL")
            override fun shareText(text: String) = error("No external sharing")
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = error("No import")
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        val currency = JsonRecord().put("currency", "RWF").put("estimatedNetWorth", 1060000)
            .put("bondValue", 1000000).put("shareValue", 60000).put("unpricedShares", 1)
        val account = JsonRecord().put("id", "synthetic-owner").put("email", "owner@example.test").put("kind", "owner").put("status", "active")
            .put("portfolio", JsonRecord().put("purchaseCount", 1).put("valuation", JsonRecord().put("currencies", JsonList(listOf(currency)))
                .put("priceDate", "2000-01-02").put("stale", true)))
        val legacy = JsonRecord().put("id", "legacy").put("email", "legacy@example.test").put("kind", "database").put("status", "active")
        UsabilityPreferences().use {
            UiQualityPortfolioFixture(owner = true, accountRecords = listOf(account, legacy)).use { fixture ->
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    scene.onActivity { host -> host.viewModelStore.put("owner-values", fixture.model); fixture.start() }
                    fixture.ready(ui) { block -> scene.onActivity { block() } }
                    scene.onActivity { host -> host.setContent {
                        CompositionLocalProvider(LocalNativeActions provides actions, LocalImportEngine provides engine) { InvestmentApp(fixture.model) }
                    } }
                    ui.click("Settings"); ui.scroll("Administration"); ui.click("Administration"); ui.scroll("Accounts"); ui.click("Accounts")
                    ui.await("≈ RWF 1,060,000")
                    ui.await("Unavailable")
                    assertFalse(ui.nodes().any { ui.matches(it, "Bond principal") })
                    ui.click("owner@example.test")
                    ui.await("Bond principal"); ui.await("RWF 1,000,000")
                    ui.await("Shares value"); ui.await("RWF 60,000")
                    ui.await("Unpriced shares use recorded cost.")
                    val image = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                    try { image.writeGolden(File(ui.instrumentation.targetContext.cacheDir, "owner-account-values.png")) }
                    finally { image.recycle() }
                    ui.click("owner@example.test")
                    ui.waitUntil("account collapsed") { ui.nodes().none { ui.matches(it, "Bond principal") } }
                    scene.onActivity { fixture.model.toggleNumberVisibility() }
                    ui.await("Financial numbers hidden")
                    assertFalse(ui.nodes().any { it.isVisibleToUser && it.text?.toString()?.contains("1,060,000") == true })
                }
            }
        }
    }
}

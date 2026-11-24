package dev.orestegabo.portfolio.investments

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class InvestmentGoalPeriodUiTest {
    @Test fun yearlyAndMonthlyChoicesRememberAmountsAndUpdateTheDashboardWithoutChangingHoldings() {
        val ui = UsabilityTestDriver()
        val native = object : NativeActions {
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
        val purchase = JsonRecord().put("id", "goal-synthetic").put("ticker", "TEST").put("currency", "RWF")
            .put("status", "active").put("shares", 6000).put("netAmountPayable", 600000).put("tradeDate", Today.toString())
        UsabilityPreferences().use {
            for (dark in listOf(false, true)) {
                ui.instrumentation.targetContext.getSharedPreferences("connection", 0).edit().putString("theme", if (dark) "dark" else "light").commit()
                UiQualityPortfolioFixture(purchaseRecords = listOf(purchase)).use { f ->
                    ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                        scene.onActivity { host -> host.viewModelStore.put("goal-period", f.model); f.start() }
                        f.ready(ui) { block -> scene.onActivity { block() } }
                        scene.onActivity { host -> host.setContent {
                            CompositionLocalProvider(LocalNativeActions provides native, LocalImportEngine provides engine) { InvestmentApp(f.model) }
                        } }
                        ui.click("Settings"); ui.scroll("Investing"); ui.click("Investing"); ui.scroll("Edit investment goal"); ui.click("Edit investment goal")
                        ui.await("Investment goal"); ui.click("Yearly"); ui.await("January–December")
                        ui.click("3.6m"); ui.click("Save")
                        ui.waitUntil("yearly goal acknowledged") { f.savedGoal.period == InvestmentGoalPeriod.YEARLY && f.savedGoal.yearlyAmount == 3600000L }
                        assertEquals(100000L, f.model.monthlyInvestmentTarget)
                        ui.click("Back"); ui.click("Home"); ui.scroll("Next insight"); ui.click("Next insight")
                        ui.instrumentation.uiAutomation.waitForIdle(500, 5000)
                        ui.click("Next insight")
                        ui.instrumentation.uiAutomation.waitForIdle(500, 5000)
                        ui.await("Yearly goal"); ui.await("RWF 3.60M"); ui.await("RWF 600,000")
                        val image = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                        try { image.writeGolden(File(ui.instrumentation.targetContext.cacheDir, "yearly-goal-$dark.png")) } finally { image.recycle() }
                        ui.click("Settings"); ui.scroll("Investing"); ui.click("Investing"); ui.scroll("Edit investment goal"); ui.click("Edit investment goal")
                        ui.click("Monthly"); ui.await("100000"); ui.click("300k"); ui.click("Save")
                        ui.waitUntil("monthly choice saved") { f.savedGoal.period == InvestmentGoalPeriod.MONTHLY && f.savedGoal.monthlyAmount == 300000L }
                        assertEquals(3600000L, f.model.investmentGoal.yearlyAmount)
                        ui.click("Edit investment goal"); ui.click("Yearly"); ui.await("3600000"); ui.click("Cancel")
                        assertEquals(InvestmentGoalPeriod.MONTHLY, f.model.investmentGoal.period)
                        assertEquals(600000.0, f.model.portfolio.holdings.single().optDouble("netAmountPayable"), 0.0)
                    }
                }
            }
        }
    }
}

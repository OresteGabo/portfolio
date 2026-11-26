package dev.orestegabo.portfolio.investments

import androidx.activity.compose.setContent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real navigation with synthetic account data; never changes a live account. */
class SettingsNavigationUiTest {
    @Test fun searchAndNestedPagesPreservePrivacyEditorsAndBackNavigationInBothThemes() {
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
        fun screenshot(name: String) {
            val image = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
            try { image.writeGolden(File(ui.instrumentation.targetContext.cacheDir, name)) } finally { image.recycle() }
        }
        UsabilityPreferences().use {
            for (dark in listOf(false, true)) {
                ui.instrumentation.targetContext.getSharedPreferences("connection", 0).edit()
                    .putString("theme", if (dark) "dark" else "light").commit()
                UiQualityPortfolioFixture().use { f ->
                    ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                        scene.onActivity { host -> host.viewModelStore.put("settings-navigation", f.model); f.start() }
                        f.ready(ui) { block -> scene.onActivity { block() } }
                        scene.onActivity { host -> host.setContent {
                            val density = LocalDensity.current
                            CompositionLocalProvider(LocalNativeActions provides native, LocalImportEngine provides engine,
                                LocalDensity provides Density(density.density, if (dark) 1.6f else 1f)) { InvestmentApp(f.model) }
                        } }
                        ui.click("Settings"); ui.await("Search settings")
                        assertFalse("Search must stay collapsed until its toolbar icon is tapped", ui.nodes().any { it.isVisibleToUser && it.isEditable })
                        fun keyboardVisible(): Boolean {
                            var visible = false
                            scene.onActivity { host -> visible = ViewCompat.getRootWindowInsets(host.window.decorView)
                                ?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                            return visible
                        }
                        assertFalse("Controls belong on their nested pages", ui.nodes().any { ui.matches(it, "Hide numbers") })
                        screenshot("settings-directory-$dark.png")
                        ui.scroll("Privacy"); ui.click("Privacy"); ui.await("Hide numbers")
                        val concealed = f.model.numbersConcealed
                        ui.click("Hide numbers")
                        ui.waitUntil("privacy control still updates model") { f.model.numbersConcealed != concealed }
                        screenshot("settings-privacy-$dark.png")
                        // Exercise system Back as well as the app toolbar; neither leaves Settings.
                        scene.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                        ui.await("Search settings")
                        ui.click("Search settings")
                        ui.waitUntil("search field receives focus automatically") { ui.nodes().any { it.isVisibleToUser && it.isEditable && it.isFocused } }
                        ui.waitUntil("search opens the software keyboard") { keyboardVisible() }
                        ui.enter("Search settings", "yearly")
                        ui.await("Investment goal")
                        assertFalse("Search shows matching options only", ui.nodes().any { ui.matches(it, "Hide numbers") })
                        screenshot("settings-search-$dark.png")
                        ui.click("Investment goal"); ui.await("Edit investment goal")
                        ui.click("Edit investment goal"); ui.await("Save")
                        ui.await("Your target is hidden."); ui.click("Show numbers to edit")
                        ui.waitUntil("numbers explicitly revealed for editing") { !f.model.numbersConcealed }
                        ui.click("Yearly"); ui.click("Cancel")
                        assertEquals("Cancel must not change the selected goal", InvestmentGoalPeriod.MONTHLY, f.model.investmentGoal.period)
                        ui.click("Back"); ui.await("Search settings"); ui.await("yearly"); ui.await("Investment goal")
                        ui.enter("Search settings", "Accounts"); ui.await("No settings found")
                        ui.enter("Search settings", "unknown-setting"); ui.await("No settings found")
                        ui.click("Clear search"); ui.await("Privacy")
                        ui.click("Close search")
                        ui.waitUntil("closing search removes the field and keyboard") { ui.nodes().none { it.isVisibleToUser && it.isEditable } && !keyboardVisible() }
                        ui.click("Search settings")
                        ui.waitUntil("reopened search is focused") { ui.nodes().any { it.isVisibleToUser && it.isEditable && it.isFocused } }
                        scene.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                        ui.waitUntil("system Back collapses search") { ui.nodes().none { it.isVisibleToUser && it.isEditable } && !keyboardVisible() }
                        // Existing child screens return to their originating Settings page.
                        ui.scroll("Investing"); ui.click("Investing"); ui.click("Cash & transactions")
                        ui.await("Cash & transactions"); ui.click("Back"); ui.await("Edit investment goal")
                        ui.click("Back"); ui.await("Search settings")
                        ui.click("Home"); ui.scroll("Your investments")
                    }
                }
            }
        }
    }
}

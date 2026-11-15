package dev.orestegabo.portfolio.investments

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test

class FinancialTransparencyUiTest {
    @Test fun incompleteProjectionExplainsUnknownValuesAndKeepsActualZeroDistinct() {
        val ui = UsabilityTestDriver()
        val settings = object : ServerSettings {
            override fun readOrigin(): String? = null
            override fun writeOrigin(origin: String) = Unit
            override fun readLanguage() = "en"
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord {
                assertEquals("projection", command)
                return JsonRecord("""{"summary":{"finalCashBalance":0},"rows":[{"calendarYear":2030,"calendarMonth":1,"totalAccountValue":120.25},{"calendarYear":2030,"calendarMonth":2}]}""")
            }
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external navigation")
            override fun shareText(text: String) = Unit
        }
        UsabilityPreferences().use {
            ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                scene.onActivity { host ->
                    val model = PortfolioModel(settings, deferAccountStartup = true, apiFactory = { error("No network in projection presentation test") })
                    host.viewModelStore.put("financial-presentation", model)
                    AppLanguage.initialize(settings)
                    host.setContent { CompositionLocalProvider(LocalNativeActions provides actions) {
                        MaterialTheme { SimulatorScreen(model, engine, {}) }
                    } }
                }
                ui.scroll("Calculate projection"); ui.click("Calculate projection")
                ui.scroll("Projected account value")
                ui.await("Illustrative projection, not a guaranteed return. Review your assumptions.")
                ui.await("Unavailable")
                ui.scroll("Cash balance"); ui.await("RWF 0")
                assertEquals("Only the explicitly returned zero is displayed; absent summary fields are unknown", 1,
                    ui.nodes().count { it.isVisibleToUser && it.text?.toString() == "RWF 0" })
                ui.scroll("Projection chart unavailable: some values are missing.")
            }
        }
    }
}

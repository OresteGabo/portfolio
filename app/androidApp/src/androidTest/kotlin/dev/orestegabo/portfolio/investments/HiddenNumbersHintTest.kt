package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Synthetic account only; hints explain privacy without revealing values or changing the setting. */
class HiddenNumbersHintTest {
    @Test fun hiddenValuesAndWidgetEntryExplainHowToShowNumbersWithoutRevealingThem() {
        val ui = UsabilityTestDriver()
        val hint = "To show numbers, turn off Hide numbers in Settings."
        val actions = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external navigation")
            override fun shareText(text: String) = error("No external sharing")
        }
        val engine = object : InvestmentEngine {
            override var onProgress: (String) -> Unit = {}
            override suspend fun run(command: String, payload: JsonRecord, pdf: ByteArray?): JsonRecord = error("No import")
            override fun cancel() = Unit
            override fun destroy() = Unit
        }
        UsabilityPreferences().use {
            UiQualityPortfolioFixture().use { fixture ->
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    scene.onActivity { host ->
                        host.viewModelStore.put("hidden-number-hint", fixture.model)
                        fixture.start()
                    }
                    fixture.ready(ui) { block -> scene.onActivity { block() } }
                    scene.onActivity { host ->
                        fixture.model.toggleNumberVisibility()
                        host.setContent {
                            CompositionLocalProvider(LocalNativeActions provides actions, LocalImportEngine provides engine) {
                                InvestmentApp(fixture.model)
                            }
                        }
                    }
                    val hidden = ui.await("Financial numbers hidden")
                    val bounds = Rect().also(hidden::getBoundsInScreen)
                    val down = SystemClock.uptimeMillis()
                    for ((action, time) in listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + 50)) {
                        val event = MotionEvent.obtain(down, time, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                        try { assertTrue(ui.instrumentation.uiAutomation.injectInputEvent(event, true)) }
                        finally { event.recycle() }
                    }
                    ui.await(hint)
                    val screenshot = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                    try { screenshot.writeGolden(File(ui.instrumentation.targetContext.cacheDir, "hidden-numbers-hint.png")) }
                    finally { screenshot.recycle() }
                    assertTrue(fixture.model.numbersHidden)
                    assertFalse(ui.nodes().any { it.isVisibleToUser && it.text?.toString()?.contains("10,000") == true })
                    ui.click("Got it")
                    // Screen-reader users get the same explanation.
                    ui.click("Financial numbers hidden")
                    ui.await(hint)
                    ui.click("Got it")
                    ui.click("Valuation basis details")
                    ui.await("About this estimate")
                    ui.click("Financial numbers hidden")
                    ui.await(hint)
                    ui.click("Got it")
                    ui.click("Done")
                    ui.scroll("Financial chart hidden")
                    ui.click("Financial chart hidden")
                    ui.await(hint)
                    ui.click("Got it")
                    // Native iOS and Android widgets enter through this same post-unlock route.
                    scene.onActivity { HomeWidgetBridge.openCalendar("2030-01-18") }
                    ui.await(hint)
                    assertNull(HomeWidgetBridge.requestedDate)
                    assertTrue(fixture.model.numbersHidden)
                    ui.click("Got it")
                    ui.await("Jan 2030")
                    scene.onActivity {
                        fixture.model.toggleNumberVisibility()
                        HomeWidgetBridge.openCalendar("2030-02-18")
                    }
                    ui.await("Feb 2030")
                    assertFalse(fixture.model.numbersHidden)
                    assertFalse(ui.nodes().any { ui.matches(it, hint) })
                }
            }
        }
    }
}

package dev.orestegabo.portfolio.investments

import android.app.UiAutomation
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Synthetic values only: actual Android accessibility actions, hardware keys, and hidden-value tree. */
class ChartAccessibilityTest {
    @Test fun chartsExposeAllObservationsWithoutTouchAndRemoveFinancialSemanticsWhenHidden() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Keep an installed screen reader active when this test is run with one enabled.
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val language = AppLanguage.preference
        val priorTouchMode = AtomicReference<Boolean>()
        val snapshots = InstrumentationRegistry.getArguments().getString("a11ySnapshots") == "true"
        val withTalkBack = InstrumentationRegistry.getArguments().getString("withTalkBack") == "true"
        fun descendants(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList()
            else listOf(node) + (0 until node.childCount).flatMap { descendants(node.getChild(it)) }
        fun nodes(): List<AccessibilityNodeInfo> { automation.clearCache(); return descendants(automation.rootInActiveWindow) }
        fun state(node: AccessibilityNodeInfo) = androidx.core.view.accessibility.AccessibilityNodeInfoCompat.wrap(node).stateDescription?.toString()
        fun await(description: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline) {
                nodes().firstOrNull(predicate)?.let { return it }
                Thread.sleep(50)
            }
            error("Missing synthetic accessibility state: $description")
        }
        fun chart() = await("chart summary") { it.contentDescription?.toString()?.contains("3 observations") == true }
        fun observation(number: Int) = await("observation $number") {
            state(it)?.startsWith("Observation $number ") == true
        }
        fun action(label: String) {
            val node = chart()
            val action = checkNotNull(node.actionList.firstOrNull { it.label?.toString() == localized(label) }) { "Missing chart action: $label" }
            assertTrue(node.performAction(action.id))
            instrumentation.waitForIdleSync()
        }
        try {
            for (style in listOf("area", "line", "bars")) for (dark in listOf(false, true)) for (scale in listOf(1f, 2f)) {
                AppLanguage.initialize(object : ServerSettings {
                    override fun readOrigin(): String? = null
                    override fun writeOrigin(origin: String) = Unit
                    override fun readLanguage() = if (dark) "fr" else "en"
                })
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        priorTouchMode.compareAndSet(null, activity.window.decorView.isInTouchMode)
                        activity.setContent {
                        var hidden by remember { mutableStateOf(false) }
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
                            LocalChartStyle provides style, LocalNumbersHidden provides hidden) {
                            MaterialTheme(colorScheme = structCapitalColorScheme(dark)) {
                                Surface(Modifier.fillMaxSize()) {
                                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                                        AppText("Synthetic chart", style = MaterialTheme.typography.headlineSmall)
                                        InvestmentChart(listOf(Double.NaN, -1.0, 720.0, 680.0, 760.0),
                                            listOf("Invalid", "Invalid", "First", "Middle", "Latest"))
                                        Button(onClick = { hidden = !hidden }) { AppText("Hide numbers") }
                                    }
                                }
                            }
                        }
                    } }
                    val summary = chart().contentDescription.toString()
                    assertTrue(summary.contains("720")); assertTrue(summary.contains("680")); assertTrue(summary.contains("760"))
                    assertFalse(summary.contains("NaN")); assertFalse(summary.contains("Invalid"))
                    observation(3)
                    if (withTalkBack) {
                        val target = chart()
                        if (!target.isAccessibilityFocused) assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                        await("chart accessibility focus") { it.isAccessibilityFocused && it.contentDescription?.toString()?.contains("3 observations") == true }
                    }
                    action("Previous observation"); observation(2)
                    action("Next observation"); observation(3)
                    action("Previous observation"); observation(2)
                    action("Latest observation"); observation(3)
                    val focused = chart()
                    assertTrue("Chart must accept keyboard focus", focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
                    observation(2)
                    if (snapshots && style == "area" && scale == 2f) {
                        val window = AtomicReference<android.view.Window>()
                        scenario.onActivity { window.set(it.window) }
                        readerFixtureScreenshot(window.get(), File(instrumentation.targetContext.cacheDir, "a11y-chart-$dark.png"))
                    }
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                    observation(3)
                    // Tab must leave the chart; Enter activates the next normal Material button.
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
                    await("next keyboard control") { it.isFocused && it.isClickable }
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
                    await("privacy placeholder") { it.contentDescription?.toString() == localized("Financial chart hidden") }
                    val spoken = nodes().joinToString { "${it.text} ${it.contentDescription} ${state(it)}" }
                    listOf("720", "680", "760", "3 observations", "RWF").forEach {
                        assertFalse("Hidden chart must not retain its values or summary", spoken.contains(it))
                    }
                }
            }
        } finally {
            // Hardware keys leave the display outside touch mode; do not change later gesture tests.
            priorTouchMode.get()?.let { instrumentation.setInTouchMode(it) }
            AppLanguage.initialize(object : ServerSettings {
                override fun readOrigin(): String? = null
                override fun writeOrigin(origin: String) = Unit
                override fun readLanguage() = language
            })
        }
    }
}

package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.design.InziraDimensions
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Actual Compose layout/accessibility checks; all visible content is synthetic. No account/native capture host. */
class UiDesignConsistencyTest {
    @Test fun progressAndFormContentWrapAtLargeTextAndKeepTheirActionsReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val snapshot = args.getString("uiAuditSnapshots") == "true"
        val previousLanguage = AppLanguage.preference
        fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
            if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
        try {
            for ((index, variant) in listOf(false to 1f, true to 1f, false to 1.8f, true to 1.8f).withIndex()) {
                val (dark, scale) = variant
                AppLanguage.initialize(object : ServerSettings {
                    override fun readOrigin(): String? = null
                    override fun writeOrigin(origin: String) = Unit
                    override fun readLanguage() = if (scale > 1f) "fr" else "en"
                })
                val label = if (scale > 1f) "Vérification des informations de votre document" else "Checking your document information"
                val detail = if (scale > 1f) "Les informations sont en cours de vérification avant votre confirmation." else "Review the extracted information before saving."
                val form = AtomicReference<ComposeRect>()
                val viewport = AtomicReference<ComposeRect>()
                val density = AtomicReference<Float>()
                val taps = AtomicInteger()
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scenario ->
                    scenario.onActivity { activity -> activity.setContent {
                        val currentDensity = LocalDensity.current
                        density.set(currentDensity.density)
                        CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, scale)) {
                            MaterialTheme(colorScheme = structCapitalColorScheme(dark)) {
                                Surface(Modifier.fillMaxSize().onGloballyPositioned { viewport.set(it.boundsInRoot()) }) {
                                    FormContent { formModifier ->
                                        Column(formModifier.onGloballyPositioned { form.set(it.boundsInRoot()) }
                                            .verticalScroll(rememberScrollState()).padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                            AppText("Inzira", style = MaterialTheme.typography.headlineMedium)
                                            ProgressContent(label, detail = detail)
                                            OutlinedTextField("", {}, label = { AppText("Document name") }, modifier = Modifier.fillMaxWidth())
                                            EmptyState("Nothing saved yet", artwork = EmptyArtwork.DOCUMENTS, compact = true,
                                                description = "Your information stays under your control.")
                                            Button(onClick = { taps.incrementAndGet() }, modifier = Modifier.fillMaxWidth()
                                                .heightIn(min = InziraDimensions.primaryActionMinHeight)) { AppText("Continue") }
                                        }
                                    }
                                }
                            }
                        }
                    } }
                    val deadline = System.currentTimeMillis() + 15_000
                    while ((form.get() == null || nodes(instrumentation.uiAutomation.rootInActiveWindow).none {
                            it.text?.toString()?.contains(label) == true }) && System.currentTimeMillis() < deadline) Thread.sleep(80)
                    assertNotNull("Form must be laid out", form.get())
                    assertTrue("Progress label must remain accessible", nodes(instrumentation.uiAutomation.rootInActiveWindow).any { it.text?.toString()?.contains(label) == true })
                    val actual = form.get(); val window = viewport.get()
                    val progressBounds = Rect()
                    nodes(instrumentation.uiAutomation.rootInActiveWindow).first { it.text?.toString()?.contains(label) == true }
                        .getBoundsInScreen(progressBounds)
                    assertTrue("Progress text must not overflow the form", progressBounds.width() <= actual.width + 2)
                    assertTrue("Form exceeds window", actual.left >= window.left - 1 && actual.right <= window.right + 1)
                    assertTrue("Wide forms must retain a readable width", actual.width <= InziraDimensions.formMaxWidth.value * density.get() + 2)
                    assertTrue("Form must remain centered", abs(actual.center.x - window.center.x) < 2)
                    if (snapshot) {
                        val target = File(instrumentation.targetContext.cacheDir, "ui-audit-$index.png")
                        scenario.onActivity { activity ->
                            // PixelCopy's callback needs main, so perform the copy off that thread below.
                            activity.window.decorView.postInvalidate()
                        }
                        instrumentation.waitForIdleSync()
                        val windowRef = AtomicReference<android.view.Window>()
                        scenario.onActivity { windowRef.set(it.window) }
                        readerFixtureScreenshot(windowRef.get(), target)
                    }
                    val ui = UsabilityTestDriver()
                    ui.scroll(localized("Continue"))
                    ui.click(localized("Continue"))
                    assertEquals("Primary action must retain its callback", 1, taps.get())
                }
            }
        } finally {
            AppLanguage.initialize(object : ServerSettings {
                override fun readOrigin(): String? = null
                override fun writeOrigin(origin: String) = Unit
                override fun readLanguage() = previousLanguage
            })
        }
    }
}

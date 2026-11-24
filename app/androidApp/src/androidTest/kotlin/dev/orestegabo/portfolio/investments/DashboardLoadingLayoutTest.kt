package dev.orestegabo.portfolio.investments

import android.graphics.Rect
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** An unresolved synthetic snapshot exercises the real loading branch, not a preview-only screen. */
class DashboardLoadingLayoutTest {
    @Test fun loadingMatchesTheDashboardHeaderAndRevealsValuesOnlyAfterTheSnapshotArrives() {
        val ui = UsabilityTestDriver()
        val native = object : NativeActions {
            override suspend fun pickPdf(onSelected: () -> Unit): ByteArray? = null
            override suspend fun savePdf(fileName: String, bytes: ByteArray) = false
            override fun openUrl(url: String) = error("No external navigation")
            override fun shareText(text: String) = error("No external sharing")
        }
        UsabilityPreferences().use {
            for ((dark, fontScale) in listOf(false to 1f, true to 1f, false to 1.8f, true to 1.8f)) {
                val snapshot = CompletableDeferred<Unit>()
                try {
                    UiQualityPortfolioFixture(initialSnapshotGate = snapshot).use { fixture ->
                        ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                            val notifications = AtomicInteger()
                            scene.onActivity { host ->
                                host.viewModelStore.put("dashboard-loading", fixture.model)
                                host.setContent {
                                    val density = LocalDensity.current
                                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale),
                                        LocalNumbersHidden provides false,
                                        LocalNativeActions provides native) {
                                        MaterialTheme(colorScheme = structCapitalColorScheme(dark)) {
                                            Surface(Modifier.fillMaxSize()) {
                                                PortfolioScreen(fixture.model, {}, {}, {}, {}, {}, {},
                                                    openNotifications = { notifications.incrementAndGet() },
                                                    unreadNotifications = 3, openShare = {})
                                            }
                                        }
                                    }
                                }
                                fixture.start()
                            }
                            ui.await("Loading your saved investments…")
                            assertFalse(fixture.model.portfolioReady)
                            assertFalse("Unknown balances must not be shown as zero or as fixture values", ui.nodes().any {
                                it.isVisibleToUser && (it.text?.toString()?.contains("10,000") == true || it.text?.toString() == "RWF 0")
                            })
                            fun logoBounds(): Rect {
                                val logos = ui.nodes().filter { ui.matches(it, "Inzira") }
                                    .map { Rect().also(it::getBoundsInScreen) }
                                return requireNotNull(logos.minByOrNull { it.width() })
                            }
                            ui.await("Your way forward")
                            val loadingLogo = logoBounds()
                            ui.click("Notifications"); assertEquals(1, notifications.get())
                            fun capture(stage: String) {
                                ui.instrumentation.waitForIdleSync(); Thread.sleep(300)
                                val bitmap = requireNotNull(ui.instrumentation.uiAutomation.takeScreenshot())
                                try { bitmap.writeGolden(File(ui.instrumentation.targetContext.cacheDir,
                                    "dashboard-$stage-$dark-$fontScale.png")) }
                                finally { bitmap.recycle() }
                            }
                            capture("loading")
                            snapshot.complete(Unit)
                            fixture.ready(ui) { block -> scene.onActivity { block() } }
                            ui.await("10,000")
                            ui.await("Your way forward")
                            assertFalse(ui.nodes().any { ui.matches(it, "Loading your saved investments…") })
                            assertEquals("Loading and loaded headers must occupy the same position", loadingLogo, logoBounds())
                            capture("loaded")
                            fun metricBounds() = Rect().also(ui.await("Bond principal")::getBoundsInScreen)
                            val cachedPosition = metricBounds()
                            fixture.quoteStale = false
                            scene.onActivity { fixture.model.loadMarket(refresh = true) }
                            ui.waitUntil("fresh quote board") { !fixture.model.busy && !fixture.model.quoteRefreshFailed }
                            ui.instrumentation.waitForIdleSync(); Thread.sleep(300)
                            assertEquals("Fresh prices must not pull the metric cards upward", cachedPosition, metricBounds())
                            val actionGate = CompletableDeferred<Unit>()
                            scene.onActivity { fixture.model.action { actionGate.await() } }
                            ui.waitUntil("held dashboard action") { fixture.model.busy }
                            ui.instrumentation.waitForIdleSync(); Thread.sleep(300)
                            val busyPosition = metricBounds()
                            actionGate.complete(Unit)
                            ui.waitUntil("dashboard action settled") { !fixture.model.busy }
                            ui.instrumentation.waitForIdleSync(); Thread.sleep(300)
                            assertEquals("Finishing startup work must not collapse content above the cards", busyPosition, metricBounds())
                            assertEquals("Status changes must keep the brand header fixed", loadingLogo, logoBounds())
                            capture("settled")
                        }
                    }
                } finally { snapshot.complete(Unit) }
            }
        }
    }
}

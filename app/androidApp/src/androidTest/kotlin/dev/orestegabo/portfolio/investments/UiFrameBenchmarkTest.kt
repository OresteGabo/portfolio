package dev.orestegabo.portfolio.investments

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Collections

/** Debug/emulator diagnostic, not a release benchmark or a universal frame-time threshold. */
class UiFrameBenchmarkTest {
    @Test fun syntheticPurchaseHistoryFrameBaseline() {
        assumeTrue("Opt in with -e uiFrameBenchmark true on the disposable reference emulator",
            InstrumentationRegistry.getArguments().getString("uiFrameBenchmark") == "true")
        val ui = UsabilityTestDriver()
        val samples = Collections.synchronizedList(mutableListOf<Long>())
        val worker = HandlerThread("ui-frame-evidence").apply { start() }
        var droppedReports = 0
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            samples.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)); droppedReports += dropped
        }
        try {
            UsabilityPreferences().use {
                UiQualityPortfolioFixture(200).use { fixture ->
                    ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                        scene.onActivity { host -> host.viewModelStore.put("frames", fixture.model); fixture.start() }
                        fixture.ready(ui) { block -> scene.onActivity { block() } }
                        scene.onActivity { host ->
                            host.setContent { MaterialTheme(colorScheme = structCapitalColorScheme(false)) {
                                Surface { ShareDetailsScreen(fixture.model, fixture.selected) }
                            } }
                        }
                        ui.await("Synthetic UI company")
                        ui.scroll("Show all 200 purchases"); ui.click("Show all 200 purchases")
                        var refreshRate = 0f
                        scene.onActivity { host ->
                            refreshRate = host.display?.refreshRate ?: 0f
                            host.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
                        }
                        try {
                            repeat(24) {
                                ui.nodes().firstOrNull { it.isScrollable && !it.isEditable }
                                    ?.performAction(if (it < 12) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                                ui.instrumentation.waitForIdleSync(); Thread.sleep(100)
                            }
                        } finally { scene.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) } }
                        val drained = java.util.concurrent.CountDownLatch(1)
                        Handler(worker.looper).post { drained.countDown() }
                        check(drained.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        val sorted = synchronized(samples) { samples.toList().sorted() }
                        check(sorted.isNotEmpty()) { "No frame metrics received" }
                        fun percentile(p: Double) = sorted[((sorted.size - 1) * p).toInt()] / 1_000_000.0
                        val result = JsonRecord().put("fixtureLots", 200).put("frames", sorted.size)
                            .put("refreshRateHz", refreshRate.toDouble()).put("p50TotalMs", percentile(.5))
                            .put("p95TotalMs", percentile(.95)).put("maxTotalMs", sorted.last() / 1_000_000.0)
                            .put("droppedMetricReports", droppedReports).put("sdk", android.os.Build.VERSION.SDK_INT)
                            .put("hardware", android.os.Build.HARDWARE)
                            .put("scope", "debug emulator, ShareDetailsScreen accessibility scroll; excludes startup/biometric/PDF/platform capture")
                        File(ui.instrumentation.targetContext.filesDir, "ui-frame-baseline.json").writeText(result.toString())
                        android.util.Log.i("UiFrameBaseline", result.toString())
                    }
                }
            }
        } finally { worker.quitSafely(); worker.join(5_000) }
    }
}

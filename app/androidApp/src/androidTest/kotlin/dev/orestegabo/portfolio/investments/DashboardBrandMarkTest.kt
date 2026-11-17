package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real rendered frames, synthetic status only; no account, credentials or network. */
class DashboardBrandMarkTest {
    @Test fun initialTurnsWithinFixedFrameAndRespectsMotionAndLifecycle() {
        val ui = UsabilityTestDriver()
        val saveFrames = InstrumentationRegistry.getArguments().getString("uiAuditSnapshots") == "true"
        for (dark in listOf(false, true)) {
            val reduced = mutableStateOf(false)
            val bounds = AtomicReference<Rect>()
            val window = AtomicReference<Window>()
            ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                scene.onActivity { host ->
                    window.set(host.window)
                    host.setContent {
                        MaterialTheme(colorScheme = structCapitalColorScheme(dark)) {
                            Surface(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    DashboardBrandMark(Modifier.size(144.dp).onGloballyPositioned {
                                        val r = it.boundsInWindow()
                                        bounds.set(Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()))
                                    }, reducedMotion = reduced.value)
                                    Text(if (reduced.value) "Motion reduced" else "Motion active",
                                        Modifier.align(Alignment.BottomCenter))
                                }
                            }
                        }
                    }
                }
                ui.waitUntil("brand mark has bounds") { bounds.get() != null }
                ui.await("Inzira")
                fun capture() = captureUiGolden(window.get(), bounds.get())
                fun awaitRenderedFrame() {
                    val committed = CountDownLatch(1)
                    scene.onActivity { host ->
                        val root = host.window.decorView
                        if (Build.VERSION.SDK_INT >= 29) {
                            root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                            root.invalidate()
                        } else {
                            root.postOnAnimation { root.postOnAnimation { root.post { committed.countDown() } } }
                        }
                    }
                    assertTrue("The updated scene must render before checking pixel stability",
                        committed.await(5, TimeUnit.SECONDS))
                }
                fun changed(first: Bitmap, second: Bitmap): Int = try { goldenChangedPixels(first, second) }
                    finally { first.recycle(); second.recycle() }
                val first = capture()
                Thread.sleep(340)
                val next = capture()
                if (saveFrames) next.writeGolden(File(ui.instrumentation.targetContext.cacheDir,
                    "dashboard-mark-${if (dark) "dark" else "light"}.png"))
                // The straight left side of the hexagon is clear of the rotating initial.
                var frameChanges = 0
                val frameColors = mutableSetOf<Int>()
                for (y in (first.height * .4f).toInt() until (first.height * .6f).toInt()) {
                    for (x in (first.width * .10f).toInt() until (first.width * .18f).toInt()) {
                        frameColors += first.getPixel(x, y)
                        if (first.getPixel(x, y) != next.getPixel(x, y)) frameChanges++
                    }
                }
                assertTrue("The tested band must contain the visible hexagon", frameColors.size > 1)
                assertEquals("Hexagon must remain fixed while the initial turns", 0, frameChanges)
                assertTrue("The inner initial must visibly turn", changed(first, next) > 200)

                scene.onActivity { reduced.value = true }
                ui.await("Motion reduced")
                ui.instrumentation.waitForIdleSync()
                // Semantics/idle do not fence the Surface buffer used by PixelCopy.
                // Wait for a committed frame, then retain the strict no-motion assertion.
                awaitRenderedFrame()
                val accessible = capture()
                Thread.sleep(340)
                assertEquals("Reduce Motion must retain the complete stationary mark", 0, changed(accessible, capture()))

                scene.onActivity { reduced.value = false }
                ui.instrumentation.waitForIdleSync()
                scene.moveToState(Lifecycle.State.STARTED)
                val background = capture()
                Thread.sleep(340)
                assertEquals("Backgrounding must stop the animation", 0, changed(background, capture()))
                scene.moveToState(Lifecycle.State.RESUMED)
                ui.await("Inzira")
                val resumed = capture()
                Thread.sleep(340)
                assertTrue("Animation must resume with its screen", changed(resumed, capture()) > 200)
            }
        }
    }
}

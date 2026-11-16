package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/** Real rendered frames, synthetic status only; no account, credentials or network. */
class InziraLoadingMarkTest {
    @Test fun loaderMovesInBothThemesAndStopsForIdleReducedMotionAndBackground() {
        val ui = UsabilityTestDriver()
        val saveFrames = InstrumentationRegistry.getArguments().getString("uiAuditSnapshots") == "true"
        for (dark in listOf(false, true)) {
            val active = mutableStateOf(true)
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
                                    InziraLoadingMark(active.value, Modifier.size(144.dp).onGloballyPositioned {
                                        val r = it.boundsInWindow()
                                        bounds.set(Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()))
                                    }, label = if (active.value) "Checking sign-in" else "Inzira",
                                        reducedMotion = reduced.value)
                                }
                            }
                        }
                    }
                }
                ui.waitUntil("loader has bounds") { bounds.get() != null }
                assertEquals("android.widget.ProgressBar", ui.await("Checking sign-in").className.toString())
                fun capture() = captureUiGolden(window.get(), bounds.get())
                fun changed(first: Bitmap, second: Bitmap): Int = try { goldenChangedPixels(first, second) }
                    finally { first.recycle(); second.recycle() }
                val first = capture()
                Thread.sleep(340)
                val next = capture()
                if (saveFrames) next.writeGolden(File(ui.instrumentation.targetContext.cacheDir,
                    "auth-loader-${if (dark) "dark" else "light"}.png"))
                assertTrue("The combined mark must visibly animate", changed(first, next) > 200)

                scene.onActivity { active.value = false }
                ui.await("Inzira")
                ui.instrumentation.waitForIdleSync()
                val idle = capture()
                Thread.sleep(340)
                assertEquals("Idle logo must be still", 0, changed(idle, capture()))

                scene.onActivity { active.value = true; reduced.value = true }
                ui.await("Checking sign-in")
                ui.instrumentation.waitForIdleSync()
                val accessible = capture()
                Thread.sleep(340)
                assertEquals("Reduce Motion must retain a still, accessible loading mark", 0, changed(accessible, capture()))

                scene.onActivity { reduced.value = false }
                ui.instrumentation.waitForIdleSync()
                scene.moveToState(Lifecycle.State.STARTED)
                val background = capture()
                Thread.sleep(340)
                assertEquals("Backgrounding must stop the animation", 0, changed(background, capture()))
                scene.moveToState(Lifecycle.State.RESUMED)
                ui.await("Checking sign-in")
                val resumed = capture()
                Thread.sleep(340)
                assertTrue("Animation must resume with its screen", changed(resumed, capture()) > 200)
            }
        }
    }
}

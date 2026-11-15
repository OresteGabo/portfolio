package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.design.structCapitalColorScheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** Reference pixels cover the named Compose viewports, not physical tablet/keyboard/system-bar behavior. */
class UiGoldenTest {
    @SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
    @Test fun syntheticShareDetailsMatchReviewedLightDarkAndLargeTextReferences() {
        val ui = UsabilityTestDriver()
        val context = ui.instrumentation.context
        val manifest = JsonRecord(context.assets.open("ui-goldens/manifest.json").bufferedReader().use { it.readText() })
        val record = InstrumentationRegistry.getArguments().getString("uiGoldenMode") == "record"
        if (record) check(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Record only on a disposable emulator" }
        val bounds = AtomicReference<Rect>()
        val generation = mutableIntStateOf(0)
        UsabilityPreferences().use {
            UiQualityPortfolioFixture().use { fixture ->
                ActivityScenario.launch(GuestExperienceHostActivity::class.java).use { scene ->
                    scene.onActivity { host -> host.viewModelStore.put("golden", fixture.model); fixture.start() }
                    fixture.ready(ui) { block -> scene.onActivity { block() } }
                    for (profile in manifest.rows("profiles")) for (dark in listOf(false, true)) {
                        val width = profile.getInt("width"); val height = profile.getInt("height")
                        val name = "${profile.text("id")}-${if (dark) "dark" else "light"}.png"
                        bounds.set(null)
                        lateinit var window: android.view.Window
                        scene.onActivity { host ->
                            window = host.window; generation.intValue++
                            host.setContent {
                                key(generation.intValue) {
                                    CompositionLocalProvider(LocalDensity provides Density(1f, profile.getDouble("fontScale").toFloat()),
                                        LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        MaterialTheme(colorScheme = structCapitalColorScheme(dark)) {
                                            Surface(Modifier.size(width.dp, height.dp).onGloballyPositioned {
                                                val r = it.boundsInWindow()
                                                bounds.set(Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt()))
                                            }) { ShareDetailsScreen(fixture.model, fixture.selected) }
                                        }
                                    }
                                }
                            }
                        }
                        ui.waitUntil("$name viewport") { bounds.get()?.let { it.width() == width && it.height() == height } == true }
                        ui.await("Synthetic UI company")
                        ui.instrumentation.waitForIdleSync(); Thread.sleep(350)
                        val actual = captureUiGolden(window, bounds.get())
                        try {
                            val output = File(ui.instrumentation.targetContext.filesDir, "ui-quality-goldens/$name")
                            actual.writeGolden(output)
                            if (!record) {
                                val expected = context.assets.open("ui-goldens/$name").use(BitmapFactory::decodeStream)
                                    ?: error("Missing reviewed baseline: $name")
                                try { assertEquals("Visual change in $name; inspect actual image before recording", 0, goldenChangedPixels(expected, actual)) }
                                finally { expected.recycle() }
                            }
                        } finally { actual.recycle() }
                        ui.scroll("Purchase history"); ui.await("1 Jan 2000")
                    }
                    // Native modal behavior is asserted separately; it is not in the activity-window pixels.
                    ui.scroll("About share values"); ui.click("About share values"); ui.await("Unrealized gain / loss"); ui.click("Got it")
                    ui.await("Synthetic UI company")
                }
            }
        }
    }

    @Test fun comparatorRejectsChangedFinancialDigitsAndDimensions() {
        fun image(text: String) = Bitmap.createBitmap(240, 70, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply { drawColor(Color.WHITE); drawText(text, 5f, 45f, Paint().apply { color = Color.BLACK; textSize = 32f }) }
        }
        val a = image("RWF 8,000"); val same = image("RWF 8,000"); val changed = image("RWF 8,001")
        val other = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        try {
            assertEquals(0, goldenChangedPixels(a, same))
            assertTrue(goldenChangedPixels(a, changed) > 0)
            assertThrows(IllegalArgumentException::class.java) { goldenChangedPixels(a, other) }
        } finally { listOf(a, same, changed, other).forEach(Bitmap::recycle) }
    }
}

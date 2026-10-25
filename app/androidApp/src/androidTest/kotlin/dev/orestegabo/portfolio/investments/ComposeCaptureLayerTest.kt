package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import dev.orestegabo.portfolio.MainActivity
import java.net.ServerSocket
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
class ComposeCaptureLayerTest {
    @Test fun visibleFinancialTextIsMaskedOnlyInSystemCapturesAndOverrideRestoresNormalRendering() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("connection", 0)
        val oldOrigin = prefs.getString("origin", null)
        val server = ServerSocket(0)
        val worker = Executors.newSingleThreadExecutor()
        worker.submit {
            while (!server.isClosed) try {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val body = "{\"authenticated\":false}"
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
                }
            } catch (_: Exception) { }
        }
        fun capture() = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        fun difference(a: Bitmap, b: Bitmap): Int {
            var different = 0
            for (y in a.height / 2 - 60 until a.height / 2 + 60)
                for (x in a.width / 2 - 180 until a.width / 2 + 180)
                    if (a.getPixel(x, y) != b.getPixel(x, y)) different++
            return different
        }
        fun awaitCapture(description: String, predicate: (Bitmap) -> Boolean): Bitmap {
            val deadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < deadline) {
                val bitmap = capture()
                if (predicate(bitmap)) return bitmap
                bitmap.recycle()
                Thread.sleep(50)
            }
            throw AssertionError(description)
        }
        try {
            prefs.edit().putString("origin", "http://127.0.0.1:${server.localPort}").commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val manualMask = mutableStateOf(false)
                val protect = mutableStateOf(true)
                val amount = mutableStateOf("12345678")
                var taps = 0
                scenario.onActivity { activity ->
                    activity.setContent {
                        AndroidCaptureBackdropHost(protect.value) {
                            CompositionLocalProvider(LocalNumbersHidden provides (manualMask.value ||
                                (protect.value && captureProtectionRequiresMaskedNumbers))) {
                                MaterialTheme {
                                    Box(Modifier.fillMaxSize().background(Color.Blue).clickable { taps++ }, contentAlignment = Alignment.Center) {
                                        FinancialText(amount.value, Modifier.width(280.dp), color = Color.White,
                                            fontSize = 32.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                        NativePrivacyCover(protect.value)
                    }
                }
                val deadline = System.currentTimeMillis() + 7000
                var ready = false
                while (!ready && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { ready = ExperimentalCaptureBackdrop.ready }
                    if (!ready) Thread.sleep(50)
                }
                assertTrue("Excluded live renderer must become ready", ready)
                Thread.sleep(500)
                val maskedCapture = capture()
                val latch = CountDownLatch(1)
                var live: Bitmap? = null
                var copyResult = -1
                scenario.onActivity { activity ->
                    val root = activity.findViewById<FrameLayout>(android.R.id.content)
                    val surface = (0 until root.childCount).map(root::getChildAt).filterIsInstance<SurfaceView>().single()
                    live = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
                    PixelCopy.request(surface, live!!, { result -> copyResult = result; latch.countDown() }, Handler(Looper.getMainLooper()))
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS))
                assertEquals(PixelCopy.SUCCESS, copyResult)
                assertEquals(maskedCapture.width, live!!.width)
                assertEquals(maskedCapture.height, live!!.height)
                File(instrumentation.targetContext.cacheDir, "capture-experiment-masked.png").outputStream().use {
                    maskedCapture.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                File(instrumentation.targetContext.cacheDir, "capture-experiment-live.png").outputStream().use {
                    live!!.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                assertTrue("Visible digits must differ from the masked capture", difference(maskedCapture, live!!) > 100)
                scenario.onActivity { activity ->
                    val time = android.os.SystemClock.uptimeMillis()
                    val root = activity.findViewById<FrameLayout>(android.R.id.content)
                    listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                        MotionEvent.obtain(time, time, action, 100f, 200f, 0).also {
                            root.dispatchTouchEvent(it); it.recycle()
                        }
                    }
                }
                assertEquals("Excluded surface must not intercept touch input", 1, taps)
                // Synthetic evidence only: verify a real recording through the OS capture path too.
                instrumentation.uiAutomation.executeShellCommand(
                    "screenrecord --time-limit 2 /data/local/tmp/portfolio-capture-experiment.mp4"
                ).use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
                scenario.onActivity { manualMask.value = true }
                Thread.sleep(500)
                val explicitMaskCapture = capture()
                assertTrue("Capture must match explicitly masked UI", difference(maskedCapture, explicitMaskCapture) < 20)
                scenario.onActivity { manualMask.value = false; amount.value = "98765432" }
                Thread.sleep(500)
                val updatedCapture = capture()
                assertTrue("Changing the amount must still capture only the mask", difference(maskedCapture, updatedCapture) < 20)
                scenario.onActivity { manualMask.value = false; protect.value = false }
                Thread.sleep(500)
                val allowedCapture = capture()
                assertTrue("Opting out must capture visible digits", difference(maskedCapture, allowedCapture) > 100)
                scenario.onActivity { protect.value = true }
                val protectedAgain = awaitCapture("Re-enabling protection did not restore the masked capture") {
                    difference(maskedCapture, it) < 20
                }
                File(instrumentation.targetContext.cacheDir, "capture-experiment-reenabled.png").outputStream().use {
                    protectedAgain.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                assertTrue("Re-enabling protection must restore the masked capture; pixel difference=${difference(maskedCapture, protectedAgain)}",
                    difference(maskedCapture, protectedAgain) < 20)
                maskedCapture.recycle(); explicitMaskCapture.recycle(); updatedCapture.recycle()
                allowedCapture.recycle(); protectedAgain.recycle(); live!!.recycle()
            }
        } finally {
            prefs.edit().apply { if (oldOrigin == null) remove("origin") else putString("origin", oldOrigin) }.commit()
            server.close(); worker.shutdownNow()
        }
    }
}

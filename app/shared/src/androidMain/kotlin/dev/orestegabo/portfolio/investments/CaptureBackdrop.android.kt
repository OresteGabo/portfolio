package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.*

/** The optional debug host accepts live pixels only after excluding its own surface from capture. */
object ExperimentalCaptureBackdrop {
    private var renderer by mutableStateOf<((Bitmap?) -> Boolean)?>(null)
    var ready by mutableStateOf(false)
        private set
    val available get() = renderer != null
    fun attach(draw: (Bitmap?) -> Boolean) { ready = false; renderer = draw }
    fun detach() { renderer?.invoke(null); renderer = null; ready = false }
    internal fun publish(bitmap: Bitmap): Boolean {
        val rendered = renderer?.invoke(bitmap) == true
        ready = rendered
        return rendered
    }
    internal fun clear() { renderer?.invoke(null); ready = false }
}

/** Android API 28+ uses a synchronous Picture snapshot in the pinned Compose implementation. */
internal fun immediateAndroidCaptureBitmap(layer: GraphicsLayer): ImageBitmap? {
    var accepting = true
    var bitmap: ImageBitmap? = null
    suspend { layer.toImageBitmap() }.startCoroutine(object : Continuation<ImageBitmap> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<ImageBitmap>) { if (accepting) bitmap = result.getOrNull() }
    })
    accepting = false
    return bitmap
}

@Composable
internal actual fun CaptureBackdropHost(enabled: Boolean, content: @Composable () -> Unit) =
    AndroidCaptureBackdropHost(enabled, content)

@Composable
fun AndroidCaptureBackdropHost(enabled: Boolean, content: @Composable () -> Unit) {
    val registry = remember { CaptureBackdropRegistry() }
    val scene = rememberGraphicsLayer()
    var frameRevision by remember { mutableIntStateOf(0) }
    val experimental = enabled && ExperimentalCaptureBackdrop.available && Build.VERSION.SDK_INT >= 28
    SideEffect { if (!enabled) ExperimentalCaptureBackdrop.clear() }
    DisposableEffect(Unit) { onDispose { ExperimentalCaptureBackdrop.clear() } }
    LaunchedEffect(experimental) {
        if (experimental) while (isActive) {
            delay(50)
            if (registry.revision != registry.capturedRevision) frameRevision++
        }
    }
    CompositionLocalProvider(LocalCaptureBackdropRegistry provides if (experimental) registry else null) {
        Box(Modifier.fillMaxSize().drawWithContent {
            if (!experimental) { drawContent(); return@drawWithContent }
            frameRevision
            scene.record { this@drawWithContent.drawContent() }
            // Snapshots are immutable: restore the child layers only after the masked pixels exist.
            val live = immediateAndroidCaptureBitmap(scene)
            val masked = registry.redacted { immediateAndroidCaptureBitmap(scene) }
            if (masked != null) {
                // The ordinary window ALWAYS receives masked pixels. Never draw the live scene here.
                drawImage(masked)
                if (live != null) ExperimentalCaptureBackdrop.publish(live.asAndroidBitmap())
                else ExperimentalCaptureBackdrop.clear()
            } else {
                // No safe capture image: keep the underlying window empty and return to masking.
                ExperimentalCaptureBackdrop.clear()
            }
            registry.captured()
        }) { content() }
    }
}

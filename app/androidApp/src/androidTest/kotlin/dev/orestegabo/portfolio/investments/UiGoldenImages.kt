package dev.orestegabo.portfolio.investments

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal fun captureUiGolden(window: Window, bounds: Rect): Bitmap {
    val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
    val done = CountDownLatch(1)
    var result = -1
    PixelCopy.request(window, bounds, bitmap, { result = it; done.countDown() }, Handler(Looper.getMainLooper()))
    if (!done.await(5, TimeUnit.SECONDS) || result != PixelCopy.SUCCESS) {
        bitmap.recycle(); error("Golden capture failed: $result")
    }
    return bitmap
}

/** Every pixel is checked. Two channel levels allow only tiny rasterization noise, not area loss. */
internal fun goldenChangedPixels(expected: Bitmap, actual: Bitmap): Int {
    require(expected.width == actual.width && expected.height == actual.height) { "Golden dimensions changed" }
    val a = IntArray(expected.width * expected.height); val b = IntArray(a.size)
    expected.getPixels(a, 0, expected.width, 0, 0, expected.width, expected.height)
    actual.getPixels(b, 0, actual.width, 0, 0, actual.width, actual.height)
    val shifts = intArrayOf(0, 8, 16, 24)
    return a.indices.count { i -> shifts.any { shift ->
        abs(((a[i] ushr shift) and 255) - ((b[i] ushr shift) and 255)) > 2
    } }
}

internal fun Bitmap.writeGolden(file: File) {
    file.parentFile?.mkdirs()
    file.outputStream().use { check(compress(Bitmap.CompressFormat.PNG, 100, it)) }
}

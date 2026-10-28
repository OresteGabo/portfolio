package dev.orestegabo.portfolio.investments

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/** Real two-page PDF, deliberately unrelated to any user document or account. */
internal fun readerFixturePdf(): ByteArray {
    val document = PdfDocument()
    try {
        for (number in 1..2) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(400, 560, number).create())
            page.canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(50, 90, 65); textSize = 22f }
            page.canvas.drawText("Struct Capital PDF fixture", 28f, 56f, paint)
            paint.textSize = 16f
            page.canvas.drawText("Original page $number", 28f, 96f, paint)
            page.canvas.drawRect(28f, 130f, 340f, 190f, paint)
            paint.color = Color.WHITE
            page.canvas.drawRect(120f, 240f, 280f, 360f, paint)
            paint.color = Color.BLACK
            for (x in 120 until 280 step 2) page.canvas.drawRect(x.toFloat(), 240f, x + 1f, 360f, paint)
            document.finishPage(page)
        }
        return ByteArrayOutputStream().also(document::writeTo).toByteArray()
    } finally { document.close() }
}

/** Copy only the synthetic Activity's window; excluded experimental surfaces need not be captured. */
internal fun readerFixtureScreenshot(window: android.view.Window, file: java.io.File) {
    if (android.os.Build.VERSION.SDK_INT < 26) {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            try { file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        return
    }
    val bitmap = android.graphics.Bitmap.createBitmap(window.decorView.width, window.decorView.height, android.graphics.Bitmap.Config.ARGB_8888)
    val ready = java.util.concurrent.CountDownLatch(1)
    var status = -1
    android.view.PixelCopy.request(window, bitmap, { result -> status = result; ready.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
    try {
        check(ready.await(5, java.util.concurrent.TimeUnit.SECONDS) && status == android.view.PixelCopy.SUCCESS) { "Synthetic reader screenshot failed: $status" }
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    } finally { bitmap.recycle() }
}

package dev.orestegabo.portfolio.investments

import android.app.Activity
import android.content.Context
import android.view.WindowManager

actual val captureProtectionRequiresMaskedNumbers = false
actual val captureProtectionDescription = "Blocks screenshots and screen recordings"
internal const val SCREEN_CAPTURE_ALLOWED_KEY = "screenCaptureAllowed"

/** Apply before Compose draws any financial content, including on a cold start. */
fun applyInitialCaptureProtection(activity: Activity) {
    val allowed = activity.getSharedPreferences("connection", Context.MODE_PRIVATE)
        .getBoolean(SCREEN_CAPTURE_ALLOWED_KEY, false)
    if (!allowed) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

package dev.orestegabo.portfolio.investments

import android.app.Activity
import android.view.WindowManager

actual val captureProtectionRequiresMaskedNumbers: Boolean
    get() = !ExperimentalCaptureBackdrop.ready
actual val captureProtectionDescription: String
    get() = if (ExperimentalCaptureBackdrop.ready) "Masks financial numbers only in captures (experimental)"
        else "Masks numbers on screen and in screenshots and recordings"
internal const val SCREEN_CAPTURE_ALLOWED_KEY = "screenCaptureAllowed"

/** Apply before Compose draws any financial content, including on a cold start. */
fun applyInitialCaptureProtection(activity: Activity) {
    // Guard startup until Compose has applied the persisted masking preference.
    // NativePrivacyCover clears this for the foreground after composition.
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

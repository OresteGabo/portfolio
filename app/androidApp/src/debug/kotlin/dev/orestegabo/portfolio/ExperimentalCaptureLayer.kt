package dev.orestegabo.portfolio

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Build
import android.util.Log
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.orestegabo.portfolio.investments.ExperimentalCaptureBackdrop
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Debug-only private API experiment; release APKs contain neither this code nor the bypass library. */
fun installExperimentalCaptureLayer(activity: MainActivity) {
    if (Build.VERSION.SDK_INT < 33) return
    val root = activity.findViewById<FrameLayout>(android.R.id.content)
    val surface = SurfaceView(activity).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }
    var excluded = false
    fun render(bitmap: Bitmap?): Boolean {
        if (!excluded || !surface.holder.surface.isValid) return false
        return try {
            val canvas = surface.holder.lockHardwareCanvas()
            try {
                canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                if (bitmap != null) canvas.drawBitmap(bitmap, 0f, 0f, null)
            } finally { surface.holder.unlockCanvasAndPost(canvas) }
            true
        } catch (error: Exception) {
            Log.w("CaptureLayer", "Experimental surface unavailable", error)
            false
        }
    }
    surface.holder.addCallback(object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            try {
                SurfaceControl.Transaction().use { transaction ->
                    HiddenApiBypass.invoke(SurfaceControl.Transaction::class.java, transaction,
                        "setSkipScreenshot", surface.surfaceControl, true)
                    transaction.addTransactionCommittedListener(activity.mainExecutor) {
                        if (surface.holder.surface.isValid) {
                            excluded = true
                            ExperimentalCaptureBackdrop.attach(::render)
                        }
                    }
                    transaction.apply()
                }
            } catch (error: Throwable) {
                Log.w("CaptureLayer", "Private capture exclusion unavailable; using on-screen masks", error)
            }
        }
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            ExperimentalCaptureBackdrop.detach()
            excluded = false
        }
    })
    root.addView(surface, FrameLayout.LayoutParams(-1, -1))
    activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            ExperimentalCaptureBackdrop.detach()
            root.removeView(surface)
            owner.lifecycle.removeObserver(this)
        }
    })
}

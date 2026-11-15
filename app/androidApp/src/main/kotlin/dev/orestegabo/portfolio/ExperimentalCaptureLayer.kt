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
import androidx.lifecycle.Lifecycle
import dev.orestegabo.portfolio.investments.ExperimentalCaptureBackdrop
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Capture-only live surface, installed automatically when the platform supports exclusion. */
fun installExperimentalCaptureLayer(activity: MainActivity) {
    // Older devices and rejected private APIs use a secure window, never forced number hiding.
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
    var disposed = false
    var generation = 0
    var releaseAttachment: (() -> Unit)? = null
    fun retireSurface() {
        generation++
        // Stop hardware-canvas access before destroying the surface or invoking its release callback.
        excluded = false
        releaseAttachment?.invoke()
        releaseAttachment = null
    }
    fun render(bitmap: Bitmap?): Boolean {
        if (!excluded || disposed || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            !surface.holder.surface.isValid) return false
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
            retireSurface()
            val createdGeneration = generation
            try {
                SurfaceControl.Transaction().use { transaction ->
                    HiddenApiBypass.invoke(SurfaceControl.Transaction::class.java, transaction,
                        "setSkipScreenshot", surface.surfaceControl, true)
                    transaction.addTransactionCommittedListener(activity.mainExecutor) {
                        // A transaction may commit after destruction or after another surface was created.
                        if (!disposed && generation == createdGeneration && surface.holder.surface.isValid) {
                            excluded = true
                            releaseAttachment = ExperimentalCaptureBackdrop.attach(::render)
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
            retireSurface()
        }
    })
    root.addView(surface, FrameLayout.LayoutParams(-1, -1))
    activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            disposed = true
            retireSurface()
            root.removeView(surface)
            owner.lifecycle.removeObserver(this)
        }
    })
}

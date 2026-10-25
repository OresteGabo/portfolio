package dev.orestegabo.portfolio.investments

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun PlatformShakeListener(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val callback by rememberUpdatedState(onShake)
    DisposableEffect(context, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val detector = ShakeDetector()
        var listening = enabled
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (!listening) return
                val g = SensorManager.GRAVITY_EARTH.toDouble()
                if (detector.sample(event.timestamp / 1_000_000, event.values[0] / g,
                        event.values[1] / g, event.values[2] / g)) callback()
            }
        }
        if (enabled && sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { listening = false; manager?.unregisterListener(listener) }
    }
}

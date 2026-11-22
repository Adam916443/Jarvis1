package app.shilex.android.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

private val Flat = mutableStateOf(Offset.Zero)

/** How the phone is tilted, each side -1..1, 0 when held as usual. Read it in draw or graphicsLayer blocks only. */
val LocalTilt = staticCompositionLocalOf<State<Offset>> { Flat }

/**
 * Feeds [LocalTilt] from the accelerometer while the app is on screen, so the HUD floats like a
 * hologram: panels turn a little and layers shift as the phone moves. It slowly re-centres on
 * the way the phone is usually held.
 */
@Composable
fun ProvideTilt(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    LifecycleResumeEffect(Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var sx = 0f
        var sy = 0f
        var bx = 0f
        var by = 0f
        var first = true
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                if (first) {
                    first = false
                    sx = x; sy = y; bx = x; by = y
                }
                sx += (x - sx) * 0.08f
                sy += (y - sy) * 0.08f
                bx += (sx - bx) * 0.004f
                by += (sy - by) * 0.004f
                tilt.value = Offset(((sx - bx) / 4f).coerceIn(-1f, 1f), ((sy - by) / 4f).coerceIn(-1f, 1f))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose { manager?.unregisterListener(listener) }
    }
    CompositionLocalProvider(LocalTilt provides tilt, content = content)
}

/** Turns a panel a few degrees toward the tilt, in real perspective. */
@Composable
fun Modifier.holoTilt(degrees: Float = 5f): Modifier {
    val tilt = LocalTilt.current
    return graphicsLayer {
        val t = tilt.value
        rotationX = -t.y * degrees
        rotationY = t.x * degrees
        cameraDistance = 18f * density
    }
}

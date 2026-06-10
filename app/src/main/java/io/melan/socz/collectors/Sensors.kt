package io.melan.socz.collectors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SensorDesc(
    val name: String,
    val vendor: String,
    val typeName: String,
    val typeId: Int,
    val version: Int,
    val powerMa: Float,
    val resolution: Float,
    val maxRange: Float,
    val minDelayUs: Int,
    val maxDelayUs: Int,
    val isWakeUp: Boolean,
    val isDynamic: Boolean,
) {
    /** Stable key matching [SensorCollector.liveValues] map entries. */
    val key: String get() = "$typeId/$name"
}

object SensorCollector {
    fun read(ctx: Context): List<SensorDesc> {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getSensorList(Sensor.TYPE_ALL).map { s ->
            SensorDesc(
                name = s.name, vendor = s.vendor,
                typeName = s.stringType, typeId = s.type,
                version = s.version, powerMa = s.power,
                resolution = s.resolution, maxRange = s.maximumRange,
                minDelayUs = s.minDelay, maxDelayUs = s.maxDelay,
                isWakeUp = s.isWakeUpSensor, isDynamic = s.isDynamicSensor,
            )
        }
    }

    /**
     * Latest raw values of every continuous/on-change sensor, keyed by
     * [SensorDesc.key] and snapshotted every [intervalMs]. Listeners are
     * registered while the flow is collected and unregistered on cancel,
     * so leaving the screen stops all hardware polling.
     */
    fun liveValues(ctx: Context, intervalMs: Long = 500L): Flow<Map<String, FloatArray>> =
        callbackFlow {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val latest = ConcurrentHashMap<String, FloatArray>()
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    latest["${e.sensor.type}/${e.sensor.name}"] = e.values.clone()
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
            }
            // One-shot (trigger) sensors fire once via requestTriggerSensor and
            // can't be subscribed with a regular listener — skip them.
            sm.getSensorList(Sensor.TYPE_ALL)
                .filter { it.reportingMode != Sensor.REPORTING_MODE_ONE_SHOT }
                .forEach { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            val ticker = launch {
                while (isActive) {
                    trySend(latest.toMap())
                    delay(intervalMs)
                }
            }
            awaitClose {
                ticker.cancel()
                sm.unregisterListener(listener)
            }
        }
}

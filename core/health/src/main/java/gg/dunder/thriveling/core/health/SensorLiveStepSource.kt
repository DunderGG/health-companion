// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import gg.dunder.thriveling.core.domain.sensor.LiveStepSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * [LiveStepSource] backed by the platform step sensors (`SensorManager`), for instant on-screen reactions.
 *
 * Health Services passive data is batched and can arrive minutes late, and `MeasureClient` does not
 * offer steps, so the live signal comes from the hardware step sensors directly (DD-37):
 * - [Sensor.TYPE_STEP_DETECTOR] (preferred): one event per step, with low latency.
 * - [Sensor.TYPE_STEP_COUNTER] (fallback): a cumulative count; each increase is spread evenly
 *   between the previous and the current event ([StepCounterSpreader]).
 *
 * The listener is registered only while the flow is collected and removed in `awaitClose`, so the
 * sensor is held only while the pet screen is visible. Without `ACTIVITY_RECOGNITION` or without
 * either sensor, the flow completes immediately and the pet simply never walks.
 *
 * ### Kotlin vs C++ Note:
 * - **`callbackFlow { ... awaitClose { } }`**: Adapts a callback/listener API into a stream. The
 *   `awaitClose` block runs when the collector goes away, like a RAII destructor unregistering a callback.
 *
 * @param context Any context; only the application context is retained.
 */
class SensorLiveStepSource(context: Context) : LiveStepSource {

    private val appContext = context.applicationContext

    override fun steps(): Flow<Long> {
        if (!HealthPermissions.hasCorePermission(appContext)) return emptyFlow()

        val sensorManager = appContext.getSystemService(SensorManager::class.java) ?: return emptyFlow()
        val detector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val sensor = detector ?: counter ?: run {
            Log.i(TAG, "No step detector or step counter on this device; live reactions disabled.")
            return emptyFlow()
        }

        return callbackFlow {
            val spreader = StepCounterSpreader()
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val eventMillis = toEpochMillis(event.timestamp)
                    if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                        trySend(eventMillis)
                    } else {
                        spreader.onCount(event.values[0].toLong(), eventMillis).forEach { trySend(it) }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }

            // maxReportLatencyUs = 0: deliver immediately instead of batching in the sensor hub.
            val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0)
            if (!registered) {
                Log.w(TAG, "Could not register ${sensor.name}; live reactions disabled.")
                close()
            } else {
                Log.d(TAG, "Live steps from ${sensor.name}")
            }

            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }

    /**
     * Sensor timestamps are nanoseconds since boot (`elapsedRealtimeNanos`); anchor them to wall-clock time.
     */
    private fun toEpochMillis(eventNanos: Long): Long {
        val ageMillis = (SystemClock.elapsedRealtimeNanos() - eventNanos) / 1_000_000L
        return System.currentTimeMillis() - ageMillis.coerceAtLeast(0L)
    }

    companion object {
        private const val TAG = "SensorLiveStepSource"
    }
}

/**
 * Converts cumulative `TYPE_STEP_COUNTER` readings into individual step timestamps.
 *
 * The first reading after registration only sets the baseline (the counter counts since boot).
 * Each later increase of `n` steps is spread evenly over the interval since the previous reading.
 * At most the [MAX_STEPS_PER_EVENT] most recent of them are produced, at the same average spacing;
 * older steps would fall outside the cadence window anyway.
 */
class StepCounterSpreader {

    private var lastCount: Long? = null
    private var lastMillis: Long = 0L

    /**
     * @param count Cumulative step count since boot.
     * @param eventMillis Epoch-millisecond time of the reading.
     * @return Timestamps of the new steps, oldest first; empty for the baseline, no change, or a counter reset.
     */
    fun onCount(count: Long, eventMillis: Long): List<Long> {
        val previous = lastCount
        val previousMillis = lastMillis
        lastCount = count
        lastMillis = eventMillis

        if (previous == null || count <= previous) return emptyList()

        // Keep the real average spacing, so capping drops the oldest steps but not the cadence.
        val total = count - previous
        val spacing = (eventMillis - previousMillis).coerceAtLeast(0L) / total
        val kept = total.coerceAtMost(MAX_STEPS_PER_EVENT.toLong()).toInt()
        return List(kept) { i -> eventMillis - spacing * (kept - 1 - i) }
    }

    companion object {
        const val MAX_STEPS_PER_EVENT = 20
    }
}

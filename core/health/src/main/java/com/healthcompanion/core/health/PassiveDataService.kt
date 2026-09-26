// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import android.os.SystemClock
import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.IntervalDataPoint
import com.healthcompanion.core.domain.usecase.DailyTotalReading
import com.healthcompanion.core.domain.usecase.IngestPassiveDataUseCase
import com.healthcompanion.core.domain.usecase.PassiveDataBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Wear OS system-managed passive listener service.
 *
 * Receives batched health data from Health Services, converts it into a platform-independent
 * [PassiveDataBatch], and hands it to [IngestPassiveDataUseCase], which applies only the *new*
 * activity to the pet in a single atomic write.
 *
 * Consumed data types:
 * - [DataType.STEPS_DAILY] → cumulative daily total, converted to step deltas
 * - [DataType.FLOORS_DAILY] → cumulative daily total, converted to floor deltas (step-equivalent bonus)
 * - [DataType.HEART_RATE_BPM] → latest sample (SampleDataType), rate-limited fitness signal
 *
 * ### Kotlin vs C++ Note:
 * - **`CoroutineScope(SupervisorJob() + Dispatchers.IO)`**:
 *   Combines context elements via the overloaded `+` operator.
 *   - `SupervisorJob()`: Failure of one child coroutine does not cancel other children (unlike a standard `Job`).
 *   - `Dispatchers.IO`: Thread pool dispatcher backed by an elastic thread pool optimized for blocking IO/DB calls.
 * - **`serviceScope.launch { ... }`**: Spawns a concurrent "fire-and-forget" coroutine, analogous to
 *   dispatching a task to a thread pool via `std::async(std::launch::async, ...)`.
 */
class PassiveDataService : PassiveListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Callback invoked by Wear OS when batched passive sensor readings arrive.
     * Launches a background coroutine to persist habit data without blocking the main/binder thread.
     *
     * @param dataPoints Container holding all received [androidx.health.services.client.data.DataPoint] lists.
     */
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        serviceScope.launch {
            try {
                val ingest = (application as PassiveDataDependencies).ingestPassiveDataUseCase
                val batch = toPassiveDataBatch(dataPoints)
                Log.d(TAG, "Passive batch received: $batch")
                ingest.execute(batch)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ingest passive data batch", e)
            }
        }
    }

    /**
     * Extracts the latest reading per data type from a Health Services payload.
     */
    private fun toPassiveDataBatch(dataPoints: DataPointContainer): PassiveDataBatch {
        // Health Services timestamps are relative to device boot; anchor them to wall-clock time.
        val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())

        val latestHeartRate = dataPoints.getData(DataType.HEART_RATE_BPM)
            .maxByOrNull { it.getTimeInstant(bootInstant) }
            ?.value

        return PassiveDataBatch(
            steps = latestDailyTotal(dataPoints.getData(DataType.STEPS_DAILY), bootInstant),
            floors = latestDailyTotal(dataPoints.getData(DataType.FLOORS_DAILY), bootInstant),
            latestHeartRateBpm = latestHeartRate
        )
    }

    /**
     * Returns the most recent cumulative daily total, tagged with the local day it belongs to.
     */
    private fun <T : Number> latestDailyTotal(
        points: List<IntervalDataPoint<T>>,
        bootInstant: Instant
    ): DailyTotalReading? {
        val latest = points.maxByOrNull { it.getEndInstant(bootInstant) } ?: return null

        // An interval ending exactly at midnight still belongs to the previous day,
        // so attribute the reading to the last instant inside its interval.
        val epochDay = latest.getEndInstant(bootInstant)
            .minusMillis(1)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()

        return DailyTotalReading(epochDay = epochDay, total = latest.value.toDouble())
    }

    companion object {
        private const val TAG = "PassiveDataService"
    }
}

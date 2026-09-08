// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.repository.PetRepositoryImpl
import com.healthcompanion.core.model.HabitType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wear OS system-managed passive listener service.
 *
 * Receives batched health data from Health Services and dispatches each
 * data type to the appropriate [HabitType] for the pet game engine.
 *
 * Supported data types:
 * - [DataType.STEPS_DAILY] → [HabitType.Steps]
 * - [DataType.HEART_RATE_BPM] → [HabitType.HeartRate] (SampleDataType)
 * - [DataType.CALORIES_DAILY] → [HabitType.Workout] (passive calorie burn)
 * - [DataType.DISTANCE_DAILY] → [HabitType.Steps] (converted to step equivalent)
 * - [DataType.FLOORS_DAILY] → [HabitType.Steps] (converted to step equivalent)
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
            val db = CompanionDatabase.getInstance(applicationContext)
            val repository = PetRepositoryImpl(db.petDao())

            processSteps(dataPoints, repository)
            processHeartRate(dataPoints, repository)
            processCalories(dataPoints, repository)
            processDistance(dataPoints, repository)
            processFloors(dataPoints, repository)
        }
    }

    // ── Steps (IntervalDataType) ─────────────────────────────────────

    /**
     * Extracts cumulative daily steps from [dataPoints] and logs [HabitType.Steps].
     *
     * @param dataPoints Health Services data payload.
     * @param repository Companion repository to record the habit.
     */
    private suspend fun processSteps(
        dataPoints: DataPointContainer,
        repository: PetRepositoryImpl
    ) {
        val stepDataPoints = dataPoints.getData(DataType.STEPS_DAILY)
        if (stepDataPoints.isNotEmpty()) {
            val latestTotalSteps = stepDataPoints.last().value
            Log.d(TAG, "Steps received: $latestTotalSteps")
            repository.recordHabit(HabitType.Steps(latestTotalSteps.toInt()))
        }
    }

    // ── Heart Rate (SampleDataType) ──────────────────────────────────

    /**
     * Extracts the latest heart rate sample in BPM and logs [HabitType.HeartRate].
     *
     * @param dataPoints Health Services data payload.
     * @param repository Companion repository to record the habit.
     */
    private suspend fun processHeartRate(
        dataPoints: DataPointContainer,
        repository: PetRepositoryImpl
    ) {
        val hrDataPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
        if (hrDataPoints.isNotEmpty()) {
            // Use the most recent sample for the fitness signal
            val latestBpm = hrDataPoints.last().value
            Log.d(TAG, "Heart rate received: $latestBpm bpm")
            repository.recordHabit(HabitType.HeartRate(latestBpm.toFloat()))
        }
    }

    // ── Calories (IntervalDataType) ──────────────────────────────────

    /**
     * Extracts passive calorie burn and logs it as a [HabitType.Workout] with zero active duration.
     *
     * @param dataPoints Health Services data payload.
     * @param repository Companion repository to record the habit.
     */
    private suspend fun processCalories(
        dataPoints: DataPointContainer,
        repository: PetRepositoryImpl
    ) {
        val calDataPoints = dataPoints.getData(DataType.CALORIES_DAILY)
        if (calDataPoints.isNotEmpty()) {
            val totalCalories = calDataPoints.last().value
            Log.d(TAG, "Calories received: $totalCalories kcal")
            // Map passive calorie burn to a zero-duration workout with calorie data
            repository.recordHabit(HabitType.Workout(durationMinutes = 0, calories = totalCalories.toInt()))
        }
    }

    // ── Distance (IntervalDataType, meters) ──────────────────────────

    /**
     * Extracts total daily distance in meters and converts it to equivalent step counts (avg stride ~0.75m).
     *
     * @param dataPoints Health Services data payload.
     * @param repository Companion repository to record the habit.
     */
    private suspend fun processDistance(
        dataPoints: DataPointContainer,
        repository: PetRepositoryImpl
    ) {
        val distDataPoints = dataPoints.getData(DataType.DISTANCE_DAILY)
        if (distDataPoints.isNotEmpty()) {
            val totalMeters = distDataPoints.last().value
            // Convert meters to approximate step equivalent (avg stride ~0.75m)
            val stepEquivalent = (totalMeters / 0.75).toInt()
            Log.d(TAG, "Distance received: $totalMeters m → ~$stepEquivalent steps")
            repository.recordHabit(HabitType.Steps(stepEquivalent))
        }
    }

    // ── Floors (IntervalDataType) ────────────────────────────────────

    /**
     * Extracts climbed floors and converts each floor into ~20 equivalent steps.
     *
     * @param dataPoints Health Services data payload.
     * @param repository Companion repository to record the habit.
     */
    private suspend fun processFloors(
        dataPoints: DataPointContainer,
        repository: PetRepositoryImpl
    ) {
        val floorDataPoints = dataPoints.getData(DataType.FLOORS_DAILY)
        if (floorDataPoints.isNotEmpty()) {
            val totalFloors = floorDataPoints.last().value
            // Each floor ≈ ~20 step equivalent for fitness boost
            val stepEquivalent = (totalFloors * 20).toInt()
            Log.d(TAG, "Floors received: $totalFloors → ~$stepEquivalent step equivalent")
            repository.recordHabit(HabitType.Steps(stepEquivalent))
        }
    }

    companion object {
        private const val TAG = "PassiveDataService"
    }
}

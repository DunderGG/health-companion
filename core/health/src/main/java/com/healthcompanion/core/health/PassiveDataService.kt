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
 */
class PassiveDataService : PassiveListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

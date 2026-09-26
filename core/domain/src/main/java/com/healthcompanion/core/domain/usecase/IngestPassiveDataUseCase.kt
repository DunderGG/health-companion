// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.repository.PassiveSyncRepository
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet

/**
 * Latest cumulative "since local midnight" reading for a daily data type.
 *
 * @property epochDay Local calendar day ([java.time.LocalDate.toEpochDay]) the reading belongs to.
 * @property total Cumulative total for that day.
 */
data class DailyTotalReading(
    val epochDay: Long,
    val total: Double
)

/**
 * Platform-independent summary of one batch of passive sensor data.
 *
 * @property steps Latest `STEPS_DAILY` reading in the batch, if any.
 * @property floors Latest `FLOORS_DAILY` reading in the batch, if any.
 * @property latestHeartRateBpm Most recent heart-rate sample in the batch, if any.
 */
data class PassiveDataBatch(
    val steps: DailyTotalReading? = null,
    val floors: DailyTotalReading? = null,
    val latestHeartRateBpm: Double? = null
)

/**
 * Turns a batch of passive sensor data into pet habits and applies them in one atomic write.
 *
 * Cumulative daily totals are converted to deltas via [PassiveSyncRepository.consumeDailyTotal],
 * so re-delivered totals are never applied twice. Deltas are consumed *before* the pet is updated:
 * if the process dies in between, activity is under-counted rather than double counted.
 *
 * @property petRepository Repository applying the resulting habits.
 * @property syncRepository Bookkeeping of what has already been consumed.
 */
class IngestPassiveDataUseCase(
    private val petRepository: PetRepository,
    private val syncRepository: PassiveSyncRepository
) {

    /**
     * @param batch The sensor batch to ingest.
     * @param currentTimeMillis Current epoch time, used for heart-rate rate limiting.
     * @return The updated [Pet], or `null` if the batch contained nothing new to apply.
     */
    suspend fun execute(batch: PassiveDataBatch, currentTimeMillis: Long = System.currentTimeMillis()): Pet? {
        val habits = buildList {
            batch.steps?.let { reading ->
                val steps = syncRepository.consumeDailyTotal(
                    STEPS_KEY, reading.epochDay, reading.total, STEP_GRANULARITY
                )
                if (steps > 0) add(HabitType.Steps(steps.toInt()))
            }

            batch.floors?.let { reading ->
                val floors = syncRepository.consumeDailyTotal(
                    FLOORS_KEY, reading.epochDay, reading.total, FLOOR_GRANULARITY
                )
                if (floors > 0) add(HabitType.Steps((floors * STEPS_PER_FLOOR).toInt()))
            }

            batch.latestHeartRateBpm?.let { bpm ->
                if (syncRepository.tryClaimHeartRateAward(currentTimeMillis, HEART_RATE_AWARD_INTERVAL_MS)) {
                    add(HabitType.HeartRate(bpm.toFloat()))
                }
            }
        }

        if (habits.isEmpty()) return null
        return petRepository.recordHabits(habits)
    }

    companion object {
        const val STEPS_KEY = "steps_daily"
        const val FLOORS_KEY = "floors_daily"

        /** Steps are consumed in chunks matching the engine's XP unit (1 XP per 200 steps). */
        const val STEP_GRANULARITY = 200.0

        /** Floors are consumed in chunks of 10 (= 200 step equivalents, one XP unit). */
        const val FLOOR_GRANULARITY = 10.0

        /** Climbing effort bonus: each floor counts as ~20 extra steps. */
        const val STEPS_PER_FLOOR = 20.0

        /** Heart-rate habits are awarded at most once per 30 minutes. */
        const val HEART_RATE_AWARD_INTERVAL_MS = 30 * 60 * 1000L
    }
}

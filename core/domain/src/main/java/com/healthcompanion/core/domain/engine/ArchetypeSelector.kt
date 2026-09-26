// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.HabitEvent
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.PetArchetype
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Chooses the pet's archetype from habit *consistency* over the last [WINDOW_DAYS] local days.
 *
 * Each day in the window can count towards several focus areas:
 * - **Cardio** ([PetArchetype.CARDIO_RUNNER]): at least [CARDIO_STEPS_PER_DAY] steps (including floor bonus steps).
 * - **Strength** ([PetArchetype.IRON_BEAST]): a logged workout, or a heart-rate reading of at least
 *   [ACTIVE_HEART_RATE_BPM] (active exercise).
 * - **Mindful nourishment** ([PetArchetype.ZEN_SAGE]): at least [ZEN_HYDRATION_ML_PER_DAY] ml of water
 *   and [ZEN_HEALTHY_MEALS_PER_DAY] healthy meals.
 *
 * The focus area with the most qualifying days wins if it reached at least [MIN_CONSISTENT_DAYS] days and
 * strictly beats the others. Otherwise, including ties and sparse data, the pet stays [PetArchetype.BALANCED].
 */
object ArchetypeSelector {

    const val WINDOW_DAYS = 7
    const val MIN_CONSISTENT_DAYS = 4
    const val CARDIO_STEPS_PER_DAY = 6_000
    const val ACTIVE_HEART_RATE_BPM = 100f
    const val ZEN_HYDRATION_ML_PER_DAY = 1_500
    const val ZEN_HEALTHY_MEALS_PER_DAY = 2

    /**
     * @param events Habit events; anything outside the [WINDOW_DAYS] days ending on [nowMillis]'s local day is ignored.
     * @param nowMillis The moment of selection (usually the evolution to TEEN).
     * @param zone Time zone defining local days.
     */
    fun select(events: List<HabitEvent>, nowMillis: Long, zone: ZoneId): PetArchetype {
        val today = localDay(nowMillis, zone)
        val firstDay = today.minusDays((WINDOW_DAYS - 1).toLong())

        val days = events
            .filter { it.timestampMillis <= nowMillis }
            .groupBy { localDay(it.timestampMillis, zone) }
            .filterKeys { it in firstDay..today }
            .values

        val scores = mapOf(
            PetArchetype.CARDIO_RUNNER to days.count { it.isCardioDay() },
            PetArchetype.IRON_BEAST to days.count { it.isStrengthDay() },
            PetArchetype.ZEN_SAGE to days.count { it.isZenDay() }
        )

        val best = scores.maxBy { it.value }
        val isUniqueBest = scores.count { it.value == best.value } == 1
        return if (best.value >= MIN_CONSISTENT_DAYS && isUniqueBest) best.key else PetArchetype.BALANCED
    }

    private fun List<HabitEvent>.isCardioDay(): Boolean =
        sumOf { (it.habit as? HabitType.Steps)?.stepCount ?: 0 } >= CARDIO_STEPS_PER_DAY

    private fun List<HabitEvent>.isStrengthDay(): Boolean = any { event ->
        val habit = event.habit
        habit is HabitType.Workout || (habit is HabitType.HeartRate && habit.bpm >= ACTIVE_HEART_RATE_BPM)
    }

    private fun List<HabitEvent>.isZenDay(): Boolean {
        val water = sumOf { (it.habit as? HabitType.Hydration)?.milliliters ?: 0 }
        val healthyMeals = count { (it.habit as? HabitType.Meal)?.isHealthy == true }
        return water >= ZEN_HYDRATION_ML_PER_DAY && healthyMeals >= ZEN_HEALTHY_MEALS_PER_DAY
    }

    private fun localDay(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}

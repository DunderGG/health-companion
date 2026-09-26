// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.domain.settings.DailyGoals
import com.healthcompanion.core.model.HabitEvent
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.PetArchetype

/**
 * One day's progress towards the daily focus goals (DD-47, DD-48, DD-49): the totals the goals page shows,
 * and which goals they reach. The goals page, the goal vibration and archetype selection all use this one
 * calculation, so they always agree.
 *
 * @property goals The targets measured against.
 * @property steps Steps, floor bonus steps included.
 * @property waterMl Water logged, in ml.
 * @property healthyMeals Healthy meals logged.
 * @property strengthReached Whether a workout was logged, or a heart rate of at least
 *   [ArchetypeSelector.ACTIVE_HEART_RATE_BPM] was recorded.
 */
data class DailyProgress(
    val goals: DailyGoals,
    val steps: Int = 0,
    val waterMl: Int = 0,
    val healthyMeals: Int = 0,
    val strengthReached: Boolean = false
) {
    /** Cardio: at least the step goal. */
    val cardioReached: Boolean get() = steps >= goals.steps

    /** Nourishment: both the water and the healthy-meal goal. */
    val nourishmentReached: Boolean get() = waterMl >= goals.waterMl && healthyMeals >= goals.healthyMeals

    /** The reached goals, as the archetypes they count towards. */
    val reached: Set<PetArchetype>
        get() = buildSet {
            if (cardioReached) add(PetArchetype.CARDIO_RUNNER)
            if (strengthReached) add(PetArchetype.IRON_BEAST)
            if (nourishmentReached) add(PetArchetype.ZEN_SAGE)
        }

    companion object {
        /**
         * @param dayEvents The events of a single local day.
         * @param goals The targets to measure against.
         */
        fun of(dayEvents: List<HabitEvent>, goals: DailyGoals): DailyProgress = DailyProgress(
            goals = goals,
            steps = dayEvents.sumOf { (it.habit as? HabitType.Steps)?.stepCount ?: 0 },
            waterMl = dayEvents.sumOf { (it.habit as? HabitType.Hydration)?.milliliters ?: 0 },
            healthyMeals = dayEvents.count { (it.habit as? HabitType.Meal)?.isHealthy == true },
            strengthReached = dayEvents.any { event ->
                val habit = event.habit
                habit is HabitType.Workout ||
                    (habit is HabitType.HeartRate && habit.bpm >= ArchetypeSelector.ACTIVE_HEART_RATE_BPM)
            }
        )
    }
}

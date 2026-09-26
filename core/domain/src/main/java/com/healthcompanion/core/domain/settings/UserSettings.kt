// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.settings

import com.healthcompanion.core.domain.engine.ArchetypeSelector
import com.healthcompanion.core.domain.engine.NightWindow

/**
 * The user's own daily focus goals (DD-48). They decide when the goal-reached vibration plays, but never
 * the pet's archetype, which keeps the fixed [ArchetypeSelector] thresholds.
 *
 * The strength goal (a workout, or a heart rate of at least [ArchetypeSelector.ACTIVE_HEART_RATE_BPM]) is
 * not a personal target and stays fixed.
 *
 * @property steps Steps per day for the cardio goal, floor bonus steps included.
 * @property waterMl Water per day, in ml, for the nourishment goal.
 * @property healthyMeals Healthy meals per day for the nourishment goal.
 */
data class DailyGoals(
    val steps: Int = ArchetypeSelector.CARDIO_STEPS_PER_DAY,
    val waterMl: Int = ArchetypeSelector.ZEN_HYDRATION_ML_PER_DAY,
    val healthyMeals: Int = ArchetypeSelector.ZEN_HEALTHY_MEALS_PER_DAY
) {
    init {
        require(steps in STEPS.first..STEPS.last) { "steps out of range: $steps" }
        require(waterMl in WATER_ML.first..WATER_ML.last) { "waterMl out of range: $waterMl" }
        require(healthyMeals in HEALTHY_MEALS.first..HEALTHY_MEALS.last) { "healthyMeals out of range: $healthyMeals" }
    }

    companion object {
        /** Selectable step goals: 1,000 to 20,000 in steps of 500. */
        val STEPS: IntProgression = 1_000..20_000 step 500

        /** Selectable water goals: 500 to 4,000 ml in steps of 250 ml, one tile tap each. */
        val WATER_ML: IntProgression = 500..4_000 step 250

        /** Selectable healthy-meal goals: 1 to 5. */
        val HEALTHY_MEALS: IntProgression = 1..5

        /** The archetype thresholds (DD-36), the goals before the user changes anything. */
        val DEFAULT = DailyGoals()
    }
}

/**
 * Everything the user can change on the watch's settings screen (DD-48).
 *
 * @property dailyGoals Targets for the daily goal vibration.
 * @property bedtime When the pet sleeps: sleeping mood, energy recovery and quiet hours for alerts.
 * @property hapticsEnabled Whether the pet's vibration patterns (purr, goal, evolution) play at all.
 */
data class UserSettings(
    val dailyGoals: DailyGoals = DailyGoals.DEFAULT,
    val bedtime: NightWindow = NightWindow.DEFAULT,
    val hapticsEnabled: Boolean = true
) {
    companion object {
        /** Selectable hours the night can start: 18:00 to 03:00, in wrapping order. */
        val BEDTIME_HOURS: List<Int> = (18..23) + (0..3)

        /** Selectable hours the night can end: 04:00 to 12:00. */
        val WAKE_UP_HOURS: List<Int> = (4..12).toList()

        /** Factory defaults. */
        val DEFAULT = UserSettings()
    }
}

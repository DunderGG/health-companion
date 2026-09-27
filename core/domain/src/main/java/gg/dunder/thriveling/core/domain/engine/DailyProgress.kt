// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.engine

import gg.dunder.thriveling.core.domain.settings.DailyGoals
import gg.dunder.thriveling.core.model.HabitEvent
import gg.dunder.thriveling.core.model.HabitType
import gg.dunder.thriveling.core.model.PetArchetype

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

    /**
     * The daily goals met today, one per row of the goals page: the page's count and the goal vibration
     * (DD-54). Water and healthy meals count separately here, unlike in [reached].
     */
    val goalsMet: Set<DailyGoal>
        get() = buildSet {
            if (steps >= goals.steps) add(DailyGoal.STEPS)
            if (waterMl >= goals.waterMl) add(DailyGoal.WATER)
            if (healthyMeals >= goals.healthyMeals) add(DailyGoal.HEALTHY_MEALS)
            if (strengthReached) add(DailyGoal.WORKOUT)
        }

    /** The reached focus goals, as the archetypes they count towards; water and meals together are one. */
    val reached: Set<PetArchetype>
        get() = buildSet {
            if (cardioReached) add(PetArchetype.CARDIO_RUNNER)
            if (strengthReached) add(PetArchetype.IRON_BEAST)
            if (nourishmentReached) add(PetArchetype.ZEN_SAGE)
        }

    /**
     * How close the goal counting towards [area] is to being reached, `0..1` (DD-53). Nourishment is the
     * average of its water and meal parts, each capped at 1, so it is 1 only once both are reached; strength
     * is 0 or 1.
     *
     * @param area [PetArchetype.CARDIO_RUNNER], [PetArchetype.IRON_BEAST] or [PetArchetype.ZEN_SAGE];
     *   [PetArchetype.BALANCED] has no goal and gives 0.
     */
    fun fraction(area: PetArchetype): Float = when (area) {
        PetArchetype.CARDIO_RUNNER -> share(steps, goals.steps)
        PetArchetype.IRON_BEAST -> if (strengthReached) 1f else 0f
        PetArchetype.ZEN_SAGE -> (share(waterMl, goals.waterMl) + share(healthyMeals, goals.healthyMeals)) / 2
        PetArchetype.BALANCED -> 0f
    }

    /** [current] as a share of [target], capped at 1; the goals are never 0 ([DailyGoals]). */
    private fun share(current: Int, target: Int): Float = (current.toFloat() / target).coerceIn(0f, 1f)

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

/** The four daily goals, one per row of the goals page, in its order (DD-49, DD-54). */
enum class DailyGoal {
    /** The step goal; also the cardio focus goal. */
    STEPS,

    /** The water goal; half of the nourishment focus goal. */
    WATER,

    /** The healthy-meal goal; the other half of the nourishment focus goal. */
    HEALTHY_MEALS,

    /** A workout, or a heart rate of at least [ArchetypeSelector.ACTIVE_HEART_RATE_BPM]; the strength focus goal. */
    WORKOUT
}

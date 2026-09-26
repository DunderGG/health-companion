// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.domain.settings.DailyGoals
import com.healthcompanion.core.model.HabitEvent
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

    /** The fixed per-day thresholds for archetype selection, whatever the user's own goals (DD-48). */
    private val ARCHETYPE_THRESHOLDS = DailyGoals(
        steps = CARDIO_STEPS_PER_DAY,
        waterMl = ZEN_HYDRATION_ML_PER_DAY,
        healthyMeals = ZEN_HEALTHY_MEALS_PER_DAY
    )

    private val FOCUS_AREAS = listOf(PetArchetype.CARDIO_RUNNER, PetArchetype.IRON_BEAST, PetArchetype.ZEN_SAGE)

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

        val reachedPerDay = days.map { DailyProgress.of(it, ARCHETYPE_THRESHOLDS).reached }
        val scores = FOCUS_AREAS.associateWith { area -> reachedPerDay.count { area in it } }

        val best = scores.maxBy { it.value }
        val isUniqueBest = scores.count { it.value == best.value } == 1
        return if (best.value >= MIN_CONSISTENT_DAYS && isUniqueBest) best.key else PetArchetype.BALANCED
    }

    /**
     * The focus areas one day's events qualify for: the daily focus goals (DD-47). Uses the same rules
     * as [select], but with the user's own step, water and meal targets (DD-48). With [DailyGoals.DEFAULT],
     * a goal reached today is exactly a day that counts towards that archetype.
     *
     * @param dayEvents The events of a single local day.
     * @param goals The targets to measure against.
     * @return A subset of [PetArchetype.CARDIO_RUNNER], [PetArchetype.IRON_BEAST] and [PetArchetype.ZEN_SAGE].
     */
    fun focusAreasReached(dayEvents: List<HabitEvent>, goals: DailyGoals = DailyGoals.DEFAULT): Set<PetArchetype> =
        DailyProgress.of(dayEvents, goals).reached

    private fun localDay(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}

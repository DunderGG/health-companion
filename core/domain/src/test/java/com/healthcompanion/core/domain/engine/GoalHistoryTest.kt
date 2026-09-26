// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.domain.settings.DailyGoals
import com.healthcompanion.core.model.HabitEvent
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.PetArchetype
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class GoalHistoryTest {

    private val utc = ZoneOffset.UTC
    private val now = Instant.parse("2026-01-20T18:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L

    private fun history(events: List<HabitEvent>, bornDaysAgo: Int, goals: DailyGoals = DailyGoals.DEFAULT) =
        GoalHistory.of(events, goals, bornMillis = now - bornDaysAgo * day, nowMillis = now, zone = utc)

    private fun on(daysAgo: Int, vararg habits: HabitType): List<HabitEvent> =
        habits.map { HabitEvent(it, now - daysAgo * day) }

    @Test
    fun `days are counted per goal, in the last week and since birth`() {
        val events = on(0, HabitType.Steps(7_000)) +
            on(3, HabitType.Steps(6_000), HabitType.Workout(durationMinutes = 20)) +
            on(10, HabitType.Steps(9_000)) +
            on(12, HabitType.Hydration(1_500), HabitType.Meal(isHealthy = true), HabitType.Meal(isHealthy = true))

        val result = history(events, bornDaysAgo = 14)

        assertEquals(7, result.lastWeek.days)
        assertEquals(2, result.lastWeek.daysReached(PetArchetype.CARDIO_RUNNER))
        assertEquals(1, result.lastWeek.daysReached(PetArchetype.IRON_BEAST))
        assertEquals(0, result.lastWeek.daysReached(PetArchetype.ZEN_SAGE))

        assertEquals(15, result.sinceBirth.days)
        assertEquals(3, result.sinceBirth.daysReached(PetArchetype.CARDIO_RUNNER))
        assertEquals(1, result.sinceBirth.daysReached(PetArchetype.IRON_BEAST))
        assertEquals(1, result.sinceBirth.daysReached(PetArchetype.ZEN_SAGE))
    }

    @Test
    fun `a pet born today has one day in both stretches`() {
        val result = history(on(0, HabitType.Steps(6_000)), bornDaysAgo = 0)

        assertEquals(GoalTally(days = 1, reachedDays = mapOf(PetArchetype.CARDIO_RUNNER to 1)), result.lastWeek)
        assertEquals(result.lastWeek, result.sinceBirth)
    }

    @Test
    fun `today's progress covers only today's events`() {
        val events = on(0, HabitType.Steps(2_000)) + on(1, HabitType.Steps(5_000))

        assertEquals(2_000, history(events, bornDaysAgo = 3).today.steps)
        assertEquals(0, history(emptyList(), bornDaysAgo = 3).today.steps)
    }

    @Test
    fun `a pet younger than a week counts only its own days`() {
        assertEquals(3, history(emptyList(), bornDaysAgo = 2).lastWeek.days)
    }

    @Test
    fun `events before birth or after now are ignored`() {
        val events = on(5, HabitType.Steps(8_000)) + on(-1, HabitType.Steps(8_000))

        val result = history(events, bornDaysAgo = 3)

        assertEquals(0, result.sinceBirth.daysReached(PetArchetype.CARDIO_RUNNER))
    }

    @Test
    fun `each day is measured against the given goals`() {
        val events = on(1, HabitType.Steps(7_000))

        val result = history(events, bornDaysAgo = 3, goals = DailyGoals(steps = 8_000))

        assertEquals(0, result.lastWeek.daysReached(PetArchetype.CARDIO_RUNNER))
    }
}

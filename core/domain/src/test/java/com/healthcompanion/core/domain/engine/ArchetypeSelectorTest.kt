// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.EvolutionStage
import com.healthcompanion.core.model.HabitEvent
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.PetArchetype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ArchetypeSelectorTest {

    private val utc = ZoneOffset.UTC
    private val now = Instant.parse("2026-01-10T18:00:00Z").toEpochMilli()
    private val day = 24 * 60 * 60 * 1000L

    /** Events on each of [days] days back from today (0 = today). */
    private fun daily(days: Int, vararg habits: HabitType): List<HabitEvent> =
        (0 until days).flatMap { back -> habits.map { HabitEvent(it, now - back * day) } }

    @Test
    fun `consistent step days make a cardio runner`() {
        val events = daily(5, HabitType.Steps(7_000))

        assertEquals(PetArchetype.CARDIO_RUNNER, ArchetypeSelector.select(events, now, utc))
    }

    @Test
    fun `workouts or active heart rate make an iron beast`() {
        val events = daily(2, HabitType.Workout(durationMinutes = 30)) +
            daily(5, HabitType.HeartRate(bpm = 130f)).drop(2)

        assertEquals(PetArchetype.IRON_BEAST, ArchetypeSelector.select(events, now, utc))
    }

    @Test
    fun `hydration and healthy meals make a zen sage`() {
        val events = daily(
            4,
            HabitType.Hydration(500), HabitType.Hydration(500), HabitType.Hydration(500),
            HabitType.Meal(isHealthy = true), HabitType.Meal(isHealthy = true)
        )

        assertEquals(PetArchetype.ZEN_SAGE, ArchetypeSelector.select(events, now, utc))
    }

    @Test
    fun `too few consistent days stays balanced`() {
        val events = daily(3, HabitType.Steps(20_000))

        assertEquals(PetArchetype.BALANCED, ArchetypeSelector.select(events, now, utc))
    }

    @Test
    fun `a tie between focus areas stays balanced`() {
        val events = daily(4, HabitType.Steps(7_000), HabitType.Workout(durationMinutes = 20))

        assertEquals(PetArchetype.BALANCED, ArchetypeSelector.select(events, now, utc))
    }

    @Test
    fun `events older than the seven day window are ignored`() {
        val old = (7 until 14).map { back -> HabitEvent(HabitType.Steps(7_000), now - back * day) }

        assertEquals(PetArchetype.BALANCED, ArchetypeSelector.select(old, now, utc))
    }

    @Test
    fun `specialization happens only on the first step into teen`() {
        val child = Pet(stage = EvolutionStage.CHILD, experiencePoints = 740)
        val teen = EvolutionEngine.checkEvolution(child, additionalXp = 15)

        assertEquals(EvolutionStage.TEEN, teen.stage)
        assertEquals(PetArchetype.BALANCED, teen.archetype) // checkEvolution never picks an archetype
        assertTrue(EvolutionEngine.reachesSpecialization(before = child, after = teen))
        assertFalse(EvolutionEngine.reachesSpecialization(before = teen, after = EvolutionEngine.checkEvolution(teen, 15)))
    }
}

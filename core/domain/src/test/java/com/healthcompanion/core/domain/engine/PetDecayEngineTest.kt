// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Vitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetDecayEngineTest {

    @Test
    fun `calculateDecay reduces hydration and hunger proportionally to elapsed time`() {
        val initialTime = 1_000_000_000L
        val vitals = Vitals(
            hydration = 100f,
            hunger = 100f,
            energy = 100f,
            fitness = 100f,
            happiness = 100f,
            lastUpdatedTimestamp = initialTime
        )

        // Advance 2 hours (2 * 3600 * 1000 millis)
        val twoHoursLater = initialTime + (2 * 3600 * 1000L)
        val decayed = PetDecayEngine.calculateDecay(vitals, twoHoursLater)

        // Hydration drops 3.0/hr -> 6.0
        assertEquals(94.0f, decayed.hydration, 0.01f)
        // Hunger drops 2.5/hr -> 5.0
        assertEquals(95.0f, decayed.hunger, 0.01f)
        // Energy drops 2.0/hr -> 4.0
        assertEquals(96.0f, decayed.energy, 0.01f)
        // Fitness drops 1.5/hr -> 3.0
        assertEquals(97.0f, decayed.fitness, 0.01f)
        assertEquals(twoHoursLater, decayed.lastUpdatedTimestamp)
    }

    @Test
    fun `calculateDecay clamps vitals at zero and prevents negative values`() {
        val initialTime = 1_000_000_000L
        val vitals = Vitals(
            hydration = 10f,
            hunger = 5f,
            energy = 5f,
            fitness = 5f,
            happiness = 5f,
            lastUpdatedTimestamp = initialTime
        )

        // Advance 100 hours
        val longTimeLater = initialTime + (100 * 3600 * 1000L)
        val decayed = PetDecayEngine.calculateDecay(vitals, longTimeLater)

        assertEquals(0f, decayed.hydration, 0.001f)
        assertEquals(0f, decayed.hunger, 0.001f)
        assertEquals(0f, decayed.energy, 0.001f)
        assertEquals(0f, decayed.fitness, 0.001f)
        assertEquals(0f, decayed.happiness, 0.001f)
    }

    @Test
    fun `applyHabit water restores hydration and boosts happiness`() {
        val baseTime = 1_000_000_000L
        val vitals = Vitals(
            hydration = 50f,
            happiness = 50f,
            lastUpdatedTimestamp = baseTime
        )

        val (updated, xp) = PetDecayEngine.applyHabit(
            vitals = vitals,
            habit = HabitType.Hydration(milliliters = 250),
            currentTimeMillis = baseTime
        )

        assertEquals(70f, updated.hydration, 0.01f)
        assertEquals(55f, updated.happiness, 0.01f)
        assertTrue(xp > 0)
    }

    @Test
    fun `applyHabit steps increases fitness and happiness`() {
        val baseTime = 1_000_000_000L
        val vitals = Vitals(
            fitness = 40f,
            happiness = 50f,
            lastUpdatedTimestamp = baseTime
        )

        val (updated, xp) = PetDecayEngine.applyHabit(
            vitals = vitals,
            habit = HabitType.Steps(stepCount = 2000),
            currentTimeMillis = baseTime
        )

        assertEquals(60f, updated.fitness, 0.01f)
        assertEquals(60f, updated.happiness, 0.01f)
        assertEquals(10, xp)
    }
}


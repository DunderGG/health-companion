// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.engine

import gg.dunder.thriveling.core.model.HabitType
import gg.dunder.thriveling.core.model.Vitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class PetDecayEngineTest {

    private val utc = ZoneOffset.UTC

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
        val decayed = PetDecayEngine.calculateDecay(vitals, twoHoursLater, utc)

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
        val decayed = PetDecayEngine.calculateDecay(vitals, longTimeLater, utc)

        assertEquals(0f, decayed.hydration, 0.001f)
        assertEquals(0f, decayed.hunger, 0.001f)
        assertEquals(0f, decayed.fitness, 0.001f)
        assertEquals(0f, decayed.happiness, 0.001f)
        // Energy is not asserted here: 100 hours span several nights, during which it recovers.
    }

    @Test
    fun `energy recovers during the night window instead of decaying`() {
        val bedtime = utcMillis("2026-01-10T22:00:00Z")
        val vitals = Vitals(energy = 20f, lastUpdatedTimestamp = bedtime)

        val morning = PetDecayEngine.calculateDecay(vitals, utcMillis("2026-01-11T07:00:00Z"), utc)

        // 9 hours asleep at +8/h.
        assertEquals(92f, morning.energy, 0.01f)
    }

    @Test
    fun `energy is clamped per segment when a day drains it before the night recovers it`() {
        val morning = utcMillis("2026-01-10T07:00:00Z")
        val vitals = Vitals(energy = 10f, lastUpdatedTimestamp = morning)

        val nextMorning = PetDecayEngine.calculateDecay(vitals, utcMillis("2026-01-11T07:00:00Z"), utc)

        // Day (15 h at -2/h) drains 10 -> 0 (clamped), then night (9 h at +8/h) recovers to 72.
        // A single net sum would wrongly give 10 - 30 + 72 = 52.
        assertEquals(72f, nextMorning.energy, 0.01f)
    }

    private fun utcMillis(iso: String): Long = java.time.Instant.parse(iso).toEpochMilli()

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
            currentTimeMillis = baseTime,
            zone = utc
        )

        assertEquals(70f, updated.hydration, 0.01f)
        assertEquals(55f, updated.happiness, 0.01f)
        assertTrue(xp > 0)
    }

    @Test
    fun `a healthy meal fills more and gives energy, a snack fills less and gives happiness`() {
        val baseTime = 1_000_000_000L
        val vitals = Vitals.starting(baseTime)

        val (meal, mealXp) = PetDecayEngine.applyHabit(vitals, HabitType.Meal(isHealthy = true), baseTime, utc)
        val (snack, snackXp) = PetDecayEngine.applyHabit(vitals, HabitType.Meal(isHealthy = false), baseTime, utc)

        assertEquals(vitals.copy(hunger = 80f, energy = 55f), meal)
        assertEquals(25, mealXp)
        assertEquals(vitals.copy(hunger = 70f, happiness = 60f), snack)
        assertEquals(5, snackXp)
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
            currentTimeMillis = baseTime,
            zone = utc
        )

        assertEquals(60f, updated.fitness, 0.01f)
        assertEquals(60f, updated.happiness, 0.01f)
        assertEquals(10, xp)
    }

    @Test
    fun `applyHabit clamps negative and oversized amounts instead of violating Vitals invariants`() {
        val baseTime = 1_000_000_000L
        val vitals = Vitals(hydration = 30f, fitness = 30f, energy = 30f, lastUpdatedTimestamp = baseTime)

        val (drained, _) = PetDecayEngine.applyHabit(vitals, HabitType.Hydration(milliliters = -10_000), baseTime, utc)
        val (sapped, _) = PetDecayEngine.applyHabit(vitals, HabitType.Steps(stepCount = -50_000), baseTime, utc)
        val (overslept, _) = PetDecayEngine.applyHabit(vitals, HabitType.Sleep(durationMinutes = 10_000, qualityScore = 5f), baseTime, utc)

        assertEquals(0f, drained.hydration, 0f)
        assertEquals(0f, sapped.fitness, 0f)
        assertEquals(100f, overslept.energy, 0f)
    }
}


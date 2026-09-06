// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.Mood
import com.healthcompanion.core.model.Vitals
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodCalculatorTest {

    @Test
    fun `returns ECSTATIC when all vitals are high`() {
        val vitals = Vitals(
            energy = 95f,
            hunger = 90f,
            hydration = 90f,
            fitness = 90f,
            happiness = 95f
        )
        val mood = MoodCalculator.calculateMood(vitals)
        assertEquals(Mood.ECSTATIC, mood)
    }

    @Test
    fun `returns THIRSTY when hydration is low`() {
        val vitals = Vitals(
            energy = 80f,
            hunger = 80f,
            hydration = 15f,
            fitness = 80f,
            happiness = 80f
        )
        val mood = MoodCalculator.calculateMood(vitals)
        assertEquals(Mood.THIRSTY, mood)
    }

    @Test
    fun `returns HUNGRY when hunger is low`() {
        val vitals = Vitals(
            energy = 80f,
            hunger = 15f,
            hydration = 80f,
            fitness = 80f,
            happiness = 80f
        )
        val mood = MoodCalculator.calculateMood(vitals)
        assertEquals(Mood.HUNGRY, mood)
    }

    @Test
    fun `returns SLEEPING when nighttime and low energy`() {
        val vitals = Vitals(
            energy = 30f,
            hunger = 80f,
            hydration = 80f,
            fitness = 80f,
            happiness = 80f
        )
        val mood = MoodCalculator.calculateMood(vitals, isNightTime = true)
        assertEquals(Mood.SLEEPING, mood)
    }
}


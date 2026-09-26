// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.domain.settings.DailyGoals
import com.healthcompanion.core.model.PetArchetype
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyProgressTest {

    private val goals = DailyGoals(steps = 6_000, waterMl = 1_500, healthyMeals = 2)

    @Test
    fun `cardio is the share of the step goal, capped at 1`() {
        assertEquals(0.5f, DailyProgress(goals, steps = 3_000).fraction(PetArchetype.CARDIO_RUNNER))
        assertEquals(1f, DailyProgress(goals, steps = 9_000).fraction(PetArchetype.CARDIO_RUNNER))
    }

    @Test
    fun `strength is all or nothing`() {
        assertEquals(0f, DailyProgress(goals).fraction(PetArchetype.IRON_BEAST))
        assertEquals(1f, DailyProgress(goals, strengthReached = true).fraction(PetArchetype.IRON_BEAST))
    }

    @Test
    fun `nourishment averages water and meals, each capped, so it is full only once both are reached`() {
        assertEquals(0.5f, DailyProgress(goals, waterMl = 750, healthyMeals = 1).fraction(PetArchetype.ZEN_SAGE))
        assertEquals(0.5f, DailyProgress(goals, waterMl = 3_000).fraction(PetArchetype.ZEN_SAGE))
        assertEquals(1f, DailyProgress(goals, waterMl = 1_500, healthyMeals = 2).fraction(PetArchetype.ZEN_SAGE))
    }
}

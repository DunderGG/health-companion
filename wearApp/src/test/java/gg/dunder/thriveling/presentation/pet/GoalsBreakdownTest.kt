// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.presentation.pet

import gg.dunder.thriveling.core.domain.engine.DailyGoal
import gg.dunder.thriveling.core.domain.engine.DailyProgress
import gg.dunder.thriveling.core.domain.settings.DailyGoals
import gg.dunder.thriveling.core.model.PetArchetype
import gg.dunder.thriveling.R
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalsBreakdownTest {

    private val goals = DailyGoals(steps = 8_000, waterMl = 2_000, healthyMeals = 3)

    @Test
    fun `rows show today's totals against the user's goals, in a fixed order`() {
        val lines = goalsBreakdown(DailyProgress(goals, steps = 4_000, waterMl = 500, healthyMeals = 3))

        assertEquals(
            listOf(R.string.goal_steps, R.string.goal_water, R.string.goal_healthy_meals, R.string.goal_strength),
            lines.map { it.labelRes }
        )
        assertEquals(listOf(4_000 to 8_000, 500 to 2_000, 3 to 3, 0 to 1), lines.map { it.current to it.target })
        assertEquals(listOf(0.5f, 0.25f, 1f, 0f), lines.map { it.fraction })
    }

    @Test
    fun `progress past a goal shows a full bar`() {
        val steps = goalsBreakdown(DailyProgress(goals, steps = 12_000)).first()

        assertEquals(1f, steps.fraction)
        assertEquals(true, steps.isReached)
    }

    @Test
    fun `full rows are the met goals, so the page's count agrees with the goal vibration`() {
        val progress = DailyProgress(goals, steps = 8_000, waterMl = 2_000, healthyMeals = 2, strengthReached = true)
        val lines = goalsBreakdown(progress)

        assertEquals(DailyGoal.entries.size, lines.size)
        assertEquals(
            listOf(R.string.goal_steps, R.string.goal_water, R.string.goal_strength),
            lines.filter { it.isReached }.map { it.labelRes }
        )
        assertEquals(setOf(DailyGoal.STEPS, DailyGoal.WATER, DailyGoal.WORKOUT), progress.goalsMet)
        // For the archetype, nourishment needs both water and meals, so it isn't reached yet.
        assertEquals(setOf(PetArchetype.CARDIO_RUNNER, PetArchetype.IRON_BEAST), progress.reached)
    }
}

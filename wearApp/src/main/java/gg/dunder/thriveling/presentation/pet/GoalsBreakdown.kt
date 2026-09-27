// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.presentation.pet

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import gg.dunder.thriveling.core.domain.engine.DailyProgress
import gg.dunder.thriveling.core.ui.theme.BrightAqua
import gg.dunder.thriveling.core.ui.theme.ElectricPurple
import gg.dunder.thriveling.core.ui.theme.HealthyGreen
import gg.dunder.thriveling.core.ui.theme.SunsetOrange
import gg.dunder.thriveling.R

/** How a goal row's numbers are written. */
enum class GoalUnit {
    /** "4,200 / 6,000". */
    COUNT,

    /** "750 / 1,500 ml". */
    MILLILITERS,

    /** Done or not: "Done" / "Not yet". */
    DONE
}

/**
 * One row of the goals page (DD-49).
 *
 * @property labelRes Goal name.
 * @property current Today's total; for [GoalUnit.DONE], 1 when done and 0 otherwise.
 * @property target The goal.
 * @property unit How the numbers are written.
 * @property color Accent of the vital the goal feeds, as on the ring.
 */
data class GoalLine(
    @param:StringRes val labelRes: Int,
    val current: Int,
    val target: Int,
    val unit: GoalUnit,
    val color: Color
) {
    /** Whether this row's own target is reached. */
    val isReached: Boolean get() = current >= target

    /** Filled share of the bar, `0..1`; progress past the goal shows a full bar. */
    val fraction: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
}

/**
 * Today's goals in display order: steps (cardio), water and healthy meals (together the nourishment goal),
 * and strength. The rows are [DailyProgress]'s totals, so a full row always matches the goal vibration.
 */
fun goalsBreakdown(progress: DailyProgress): List<GoalLine> = listOf(
    GoalLine(R.string.goal_steps, progress.steps, progress.goals.steps, GoalUnit.COUNT, HealthyGreen),
    GoalLine(R.string.goal_water, progress.waterMl, progress.goals.waterMl, GoalUnit.MILLILITERS, BrightAqua),
    GoalLine(R.string.goal_healthy_meals, progress.healthyMeals, progress.goals.healthyMeals, GoalUnit.COUNT, SunsetOrange),
    GoalLine(R.string.goal_strength, if (progress.strengthReached) 1 else 0, 1, GoalUnit.DONE, ElectricPurple)
)

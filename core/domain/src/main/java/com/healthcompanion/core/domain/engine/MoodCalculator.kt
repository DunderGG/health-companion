// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.Mood
import com.healthcompanion.core.model.Vitals

/**
 * Calculates the current expressive companion mood from vitals and situational inputs.
 *
 * ### Kotlin vs C++ Note:
 * - **`when` without an argument**: In Kotlin, `when { ... }` acts as a clean, idiomatic replacement
 *   for an `if-else if-else` chain in C++, evaluating branches sequentially from top to bottom
 *   and returning the first branch whose condition evaluates to `true`.
 */
object MoodCalculator {

    /**
     * Determines the companion's current emotional state by evaluating vitals against priority thresholds.
     *
     * ### Priority Order:
     * 1. **[Mood.SLEEPING]**: [isNightTime] is true and energy < 40%.
     * 2. **[Mood.TIRED]**: Energy is critically low (< 20%).
     * 3. **[Mood.THIRSTY]**: Hydration is critically low (< 25%).
     * 4. **[Mood.HUNGRY]**: Hunger is critically low (< 25%).
     * 5. **[Mood.GRUMPY]**: Happiness is critically low (< 30%).
     * 6. **[Mood.ECSTATIC]**: Overall health >= 80%.
     * 7. **[Mood.HAPPY]**: Overall health >= 55%.
     * 8. **[Mood.CONTENT]**: Default baseline mood for all other conditions.
     *
     * @param vitals The current vitals of the companion.
     * @param isNightTime Flag indicating if the current local time falls during nocturnal hours (e.g. 22:00 - 07:00).
     *                    Defaults to `false`.
     * @return The resulting [Mood] driving character sprite expressions, animations, and particle effects.
     */
    fun calculateMood(vitals: Vitals, isNightTime: Boolean = false): Mood {
        return when {
            isNightTime && vitals.energy < 40f -> Mood.SLEEPING
            vitals.energy < 20f -> Mood.TIRED
            vitals.hydration < 25f -> Mood.THIRSTY
            vitals.hunger < 25f -> Mood.HUNGRY
            vitals.happiness < 30f -> Mood.GRUMPY
            vitals.overallHealth >= 80f -> Mood.ECSTATIC
            vitals.overallHealth >= 55f -> Mood.HAPPY
            else -> Mood.CONTENT
        }
    }
}


// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.Mood
import com.healthcompanion.core.model.Vitals

/**
 * Calculates current expressive companion mood from vitals and situational inputs.
 */
object MoodCalculator {

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


// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.model

/**
 * Health inputs performed by the user that feed into the companion engine.
 */
sealed interface HabitType {
    data class Steps(val stepCount: Int) : HabitType
    data class Hydration(val milliliters: Int = 250) : HabitType
    data class Meal(val isHealthy: Boolean, val description: String = "") : HabitType
    data class Workout(val durationMinutes: Int, val calories: Int = 0) : HabitType
    data class Sleep(val durationMinutes: Int, val qualityScore: Float = 0.8f) : HabitType
    data class PettingInteraction(val intensity: Float = 1.0f) : HabitType
}


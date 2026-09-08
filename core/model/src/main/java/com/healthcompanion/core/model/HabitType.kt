// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.model

/**
 * Health events and user interactions that feed into the companion simulation engine.
 *
 * ### Kotlin vs C++ Note:
 * A `sealed interface` (or `sealed class`) defines a **closed type hierarchy**.
 * It is the Kotlin equivalent of an Algebraic Data Type (ADT) or C++17's `std::variant<Steps, Hydration, ...>`:
 * - All possible implementations must be defined within the same package/module.
 * - In a `when` expression (analogous to `switch` or `std::visit`), the compiler enforces **exhaustive**
 *   pattern matching without requiring an `else` (default) clause. If a new habit variant is added,
 *   compilation fails everywhere until that variant is handled.
 */
sealed interface HabitType {

    /**
     * Daily accumulated or delta step count from passive activity tracking.
     *
     * @property stepCount Total or interval steps recorded.
     */
    data class Steps(val stepCount: Int) : HabitType

    /**
     * Water consumption logged either manually via quick-action button or health sync.
     *
     * @property milliliters Volume consumed in mL (default: 250 mL quick-tap).
     */
    data class Hydration(val milliliters: Int = 250) : HabitType

    /**
     * Meal or nutrition logged by the user.
     *
     * @property isHealthy Indicates if the food was wholesome/healthy vs junk/indulgent.
     *                     Healthy meals increase hunger and happiness; unhealthy meals increase hunger but slightly reduce energy.
     * @property description Optional text label describing the meal.
     */
    data class Meal(val isHealthy: Boolean, val description: String = "") : HabitType

    /**
     * Active exercise workout session.
     *
     * @property durationMinutes Length of workout in minutes.
     * @property calories Optional active energy burned in kilocalories.
     */
    data class Workout(val durationMinutes: Int, val calories: Int = 0) : HabitType

    /**
     * Sleep tracking session.
     *
     * @property durationMinutes Total sleep time in minutes.
     * @property qualityScore Normalized sleep quality multiplier in range `[0.0, 1.0]` (default: 0.8f).
     */
    data class Sleep(val durationMinutes: Int, val qualityScore: Float = 0.8f) : HabitType

    /**
     * Direct touch interaction where the user taps/pets the companion sprite on screen.
     *
     * @property intensity Intensity multiplier (default: 1.0f).
     */
    data class PettingInteraction(val intensity: Float = 1.0f) : HabitType

    /**
     * Passive heart rate reading from PPG sensor.
     *
     * @property bpm Beats per minute.
     */
    data class HeartRate(val bpm: Float) : HabitType
}


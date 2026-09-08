// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Vitals
import kotlin.math.max
import kotlin.math.min

/**
 * Battery-efficient time-delta game engine.
 * Calculates vitals degradation based on timestamps rather than waking up the CPU constantly.
 *
 * ### Kotlin vs C++ Note:
 * - **`object` singleton**: In Kotlin, an `object` declaration defines a singleton class and its single
 *   instance simultaneously (equivalent to a C++ Meyer's Singleton `static Class& getInstance()` or a
 *   namespace with free functions).
 * - **Immutable Value Types**: Functions here are **pure functions**; they do not mutate their arguments
 *   in place (unlike passing `Vitals*` or `Vitals&`). Instead, they return a new copy via `.copy(...)`
 *   with clamped values (analogous to `std::clamp` in C++17 via Kotlin's `.coerceIn(...)` and `.coerceAtMost(...)`).
 */
object PetDecayEngine {

    /** Hourly loss of hydration stat (percent per hour). */
    const val HYDRATION_DECAY_PER_HOUR = 3.0f

    /** Hourly loss of hunger/nutrition stat (percent per hour). */
    const val HUNGER_DECAY_PER_HOUR = 2.5f

    /** Hourly loss of energy stat (percent per hour). */
    const val ENERGY_DECAY_PER_HOUR = 2.0f

    /** Hourly loss of cardiovascular fitness stat (percent per hour). */
    const val FITNESS_DECAY_PER_HOUR = 1.5f

    /** Base hourly loss of happiness stat (percent per hour). */
    const val HAPPINESS_DECAY_PER_HOUR = 2.0f

    /**
     * Calculates updated vitals by applying linear time decay for the elapsed time since last update.
     *
     * ### Algorithm & Battery Design:
     * Smartwatch processors consume substantial battery if woken frequently. Instead of running a continuous
     * timer loop (1-second tick), this function uses delta time: `Δt = currentTimeMillis - lastUpdatedTimestamp`.
     * Decay is only computed on demand when the screen turns on, when a habit is logged, or during periodic
     * 2-hour WorkManager maintenance windows.
     *
     * In addition, a neglect penalty of 1.5x is applied to happiness if hydration or hunger drop below 20%.
     *
     * @param vitals The base companion vitals before applying time decay.
     * @param currentTimeMillis The current epoch timestamp in milliseconds (defaults to `System.currentTimeMillis()`).
     * @return A new [Vitals] instance with degraded stats clamped to `[0.0, 100.0]` and updated timestamp.
     *         Returns the unchanged [vitals] if [currentTimeMillis] <= [vitals.lastUpdatedTimestamp].
     */
    fun calculateDecay(vitals: Vitals, currentTimeMillis: Long = System.currentTimeMillis()): Vitals {
        if (currentTimeMillis <= vitals.lastUpdatedTimestamp) {
            return vitals
        }

        val elapsedMillis = currentTimeMillis - vitals.lastUpdatedTimestamp
        val elapsedHours = elapsedMillis / (1000f * 60f * 60f)

        val newHydration = (vitals.hydration - (elapsedHours * HYDRATION_DECAY_PER_HOUR)).coerceIn(0f, 100f)
        val newHunger = (vitals.hunger - (elapsedHours * HUNGER_DECAY_PER_HOUR)).coerceIn(0f, 100f)
        val newEnergy = (vitals.energy - (elapsedHours * ENERGY_DECAY_PER_HOUR)).coerceIn(0f, 100f)
        val newFitness = (vitals.fitness - (elapsedHours * FITNESS_DECAY_PER_HOUR)).coerceIn(0f, 100f)

        // Happiness also takes a penalty if core biological vitals are critically low (< 20)
        val neglectPenalty = if (newHydration < 20f || newHunger < 20f) 1.5f else 1.0f
        val newHappiness = (vitals.happiness - (elapsedHours * HAPPINESS_DECAY_PER_HOUR * neglectPenalty)).coerceIn(0f, 100f)

        return vitals.copy(
            energy = newEnergy,
            hunger = newHunger,
            hydration = newHydration,
            fitness = newFitness,
            happiness = newHappiness,
            lastUpdatedTimestamp = currentTimeMillis
        )
    }

    /**
     * Applies a health habit or interaction to the companion's vitals.
     *
     * ### Execution Flow:
     * 1. Evaluates elapsed decay up to [currentTimeMillis] first to ensure stats are up-to-date.
     * 2. Evaluates the habit type via smart-casting in `when (habit)` (like `std::visit` on `std::variant`).
     * 3. Computes the corresponding stat bonus and awarded experience points (XP).
     * 4. Clamps all values to valid ranges (`[0, 100]`) and stamps `lastUpdatedTimestamp`.
     *
     * @param vitals Current base vitals before applying habit.
     * @param habit The health event being recorded ([HabitType]).
     * @param currentTimeMillis Current epoch timestamp in milliseconds (defaults to `System.currentTimeMillis()`).
     * @return A [Pair] containing the updated [Vitals] (first) and the experience points awarded (second),
     *         analogous to `std::pair<Vitals, int>` in C++.
     */
    fun applyHabit(
        vitals: Vitals,
        habit: HabitType,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Pair<Vitals, Int> {
        val decayed = calculateDecay(vitals, currentTimeMillis)
        var xpEarned = 10

        val updated = when (habit) {
            is HabitType.Hydration -> {
                val boost = (habit.milliliters / 250f) * 20f
                xpEarned = 15
                decayed.copy(
                    hydration = (decayed.hydration + boost).coerceAtMost(100f),
                    happiness = (decayed.happiness + 5f).coerceAtMost(100f)
                )
            }
            is HabitType.Meal -> {
                if (habit.isHealthy) {
                    xpEarned = 25
                    decayed.copy(
                        hunger = (decayed.hunger + 30f).coerceAtMost(100f),
                        happiness = (decayed.happiness + 10f).coerceAtMost(100f)
                    )
                } else {
                    xpEarned = 5
                    decayed.copy(
                        hunger = (decayed.hunger + 20f).coerceAtMost(100f),
                        energy = (decayed.energy - 5f).coerceAtLeast(0f)
                    )
                }
            }
            is HabitType.Steps -> {
                val fitnessBoost = (habit.stepCount / 1000f) * 10f
                xpEarned = min(50, (habit.stepCount / 200))
                decayed.copy(
                    fitness = (decayed.fitness + fitnessBoost).coerceAtMost(100f),
                    happiness = (decayed.happiness + (fitnessBoost * 0.5f)).coerceAtMost(100f)
                )
            }
            is HabitType.Workout -> {
                val calorieBonus = if (habit.calories > 0) (habit.calories / 100f) * 5f else 0f
                xpEarned = habit.durationMinutes * 2 + (habit.calories / 50)
                decayed.copy(
                    fitness = (decayed.fitness + 25f + calorieBonus).coerceAtMost(100f),
                    energy = (decayed.energy - 10f).coerceAtLeast(0f),
                    happiness = (decayed.happiness + 15f).coerceAtMost(100f)
                )
            }
            is HabitType.Sleep -> {
                val energyBoost = (habit.durationMinutes / 480f) * 100f * habit.qualityScore
                xpEarned = 30
                decayed.copy(
                    energy = (decayed.energy + energyBoost).coerceAtMost(100f),
                    happiness = (decayed.happiness + 10f).coerceAtMost(100f)
                )
            }
            is HabitType.PettingInteraction -> {
                xpEarned = 5
                decayed.copy(
                    happiness = (decayed.happiness + (5f * habit.intensity)).coerceAtMost(100f)
                )
            }
            is HabitType.HeartRate -> {
                // Passive HR readings provide a small fitness signal.
                // Resting HR (< 70 bpm) indicates good cardiovascular fitness.
                // Elevated HR (> 100 bpm) indicates active exercise.
                val fitnessBoost = when {
                    habit.bpm < 60f -> 3f    // Excellent resting HR
                    habit.bpm < 75f -> 2f    // Good resting HR
                    habit.bpm > 100f -> 5f   // Active exercise detected
                    else -> 1f               // Normal range
                }
                xpEarned = 5
                decayed.copy(
                    fitness = (decayed.fitness + fitnessBoost).coerceAtMost(100f)
                )
            }
        }

        return Pair(updated.copy(lastUpdatedTimestamp = currentTimeMillis), xpEarned)
    }
}


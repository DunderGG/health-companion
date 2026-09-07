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
 */
object PetDecayEngine {

    // Hourly decay percentages
    const val HYDRATION_DECAY_PER_HOUR = 3.0f
    const val HUNGER_DECAY_PER_HOUR = 2.5f
    const val ENERGY_DECAY_PER_HOUR = 2.0f
    const val FITNESS_DECAY_PER_HOUR = 1.5f
    const val HAPPINESS_DECAY_PER_HOUR = 2.0f

    /**
     * Calculates updated vitals by applying decay for the elapsed time since last update.
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
     * Applies a user health habit to the current vitals.
     * Always computes decay up to the current timestamp first.
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


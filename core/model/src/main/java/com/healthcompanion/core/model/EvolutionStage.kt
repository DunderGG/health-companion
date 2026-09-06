// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.model

/**
 * Life cycle evolution stages for the companion.
 */
enum class EvolutionStage(val level: Int, val requiredXp: Int) {
    EGG(level = 0, requiredXp = 0),
    HATCHLING(level = 1, requiredXp = 100),
    CHILD(level = 2, requiredXp = 300),
    TEEN(level = 3, requiredXp = 750),
    ADULT(level = 4, requiredXp = 1500),
    ANCIENT_SAGE(level = 5, requiredXp = 3000);

    companion object {
        fun fromXp(xp: Int): EvolutionStage {
            return entries.lastOrNull { xp >= it.requiredXp } ?: EGG
        }
    }
}

/**
 * Archetypes unlocked based on user's primary health habits.
 */
enum class PetArchetype(val title: String, val description: String) {
    BALANCED("Balanced Soul", "Maintains harmony across all health vitals."),
    CARDIO_RUNNER("Swift Strider", "Energized by daily step goals and running."),
    ZEN_SAGE("Zen Ascetic", "Flourishes with deep sleep, meditation, and hydration."),
    IRON_BEAST("Mighty Titan", "Empowered by strenuous workouts and strength.")
}


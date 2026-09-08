// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.model

/**
 * Life cycle evolution stages for the companion.
 *
 * ### Kotlin vs C++ Note:
 * Kotlin `enum class` instances are full-fledged objects with constructor properties (`level`, `requiredXp`),
 * similar to a C++ class with `const` member variables and a private constructor instantiated as a fixed set
 * of `static const` instances.
 *
 * @property level Numerical tier index from 0 (EGG) up to 5 (ANCIENT_SAGE).
 * @property requiredXp Minimum cumulative experience points needed to unlock this evolutionary stage.
 */
enum class EvolutionStage(val level: Int, val requiredXp: Int) {
    EGG(level = 0, requiredXp = 0),
    HATCHLING(level = 1, requiredXp = 100),
    CHILD(level = 2, requiredXp = 300),
    TEEN(level = 3, requiredXp = 750),
    ADULT(level = 4, requiredXp = 1500),
    ANCIENT_SAGE(level = 5, requiredXp = 3000);

    companion object {
        /**
         * Resolves the highest evolution stage unlocked for a given total XP value.
         *
         * ### Kotlin Idiom Note:
         * - `entries`: In Kotlin, `entries` provides an immutable list of all enum constants.
         * - `lastOrNull { predicate }`: High-order function iterating backwards to find the last match,
         *   returning `null` if no element satisfies the predicate (similar to `std::find_if` on reverse iterators).
         * - `?: EGG`: The Elvis operator `?:` provides a fallback default if the left operand is `null`
         *   (analogous to `std::optional::value_or(...)` in C++).
         *
         * @param xp Total cumulative experience points (e.g. 0 to 3000+).
         * @return The highest [EvolutionStage] whose [requiredXp] is <= [xp]. Defaults to [EGG] if xp < 100.
         */
        fun fromXp(xp: Int): EvolutionStage {
            return entries.lastOrNull { xp >= it.requiredXp } ?: EGG
        }
    }
}

/**
 * Branching character archetypes unlocked when the companion reaches [EvolutionStage.TEEN].
 * The archetype reflects the user's primary lifestyle habits (e.g. cardio, mindfulness, strength).
 *
 * @property title User-facing title for the archetype.
 * @property description Explanatory text summarizing the archetype's lifestyle alignment.
 */
enum class PetArchetype(val title: String, val description: String) {
    BALANCED("Balanced Soul", "Maintains harmony across all health vitals."),
    CARDIO_RUNNER("Swift Strider", "Energized by daily step goals and running."),
    ZEN_SAGE("Zen Ascetic", "Flourishes with deep sleep, meditation, and hydration."),
    IRON_BEAST("Mighty Titan", "Empowered by strenuous workouts and strength.")
}


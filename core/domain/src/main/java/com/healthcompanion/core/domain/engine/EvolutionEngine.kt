// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.EvolutionStage
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.PetArchetype

/**
 * Manages companion evolution milestones and personality archetype branching.
 *
 * Tracks XP thresholds and permanently unlocks specialized archetypes once the companion
 * matures into adolescence ([EvolutionStage.TEEN]).
 */
object EvolutionEngine {

    /**
     * Evaluates whether awarded experience points trigger a stage evolution or archetype specialization.
     *
     * ### Archetype Branching Logic:
     * When reaching [EvolutionStage.TEEN] (level 3, 750+ XP), if the companion's archetype is still
     * [PetArchetype.BALANCED], its lifestyle habit vitals are inspected to lock in a persona:
     * - `fitness > 80f` -> [PetArchetype.CARDIO_RUNNER] (focused on running/steps)
     * - `hydration > 80f && energy > 80f` -> [PetArchetype.ZEN_SAGE] (focused on recovery/hydration)
     * - Otherwise -> Remains [PetArchetype.BALANCED]
     *
     * @param pet The current [Pet] state prior to XP addition.
     * @param additionalXp Non-negative experience points gained from the recent habit or interaction.
     * @return A new [Pet] instance with updated [Pet.stage], [Pet.archetype], and cumulative [Pet.experiencePoints].
     */
    fun checkEvolution(pet: Pet, additionalXp: Int): Pet {
        val totalXp = pet.experiencePoints + additionalXp
        val newStage = EvolutionStage.fromXp(totalXp)

        // Archetype assignment once reaching TEEN stage
        val updatedArchetype = if (newStage.level >= EvolutionStage.TEEN.level && pet.archetype == PetArchetype.BALANCED) {
            when {
                pet.vitals.fitness > 80f -> PetArchetype.CARDIO_RUNNER
                pet.vitals.hydration > 80f && pet.vitals.energy > 80f -> PetArchetype.ZEN_SAGE
                else -> PetArchetype.BALANCED
            }
        } else {
            pet.archetype
        }

        return pet.copy(
            stage = newStage,
            archetype = updatedArchetype,
            experiencePoints = totalXp
        )
    }
}


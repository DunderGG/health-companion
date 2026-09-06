// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.EvolutionStage
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.PetArchetype

/**
 * Manages evolution milestones and archetype branching.
 */
object EvolutionEngine {

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


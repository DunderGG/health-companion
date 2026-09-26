// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.EvolutionStage
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.PetArchetype

/**
 * Manages companion evolution milestones and the moment of archetype specialization.
 *
 * Stage progression is purely XP-based. The archetype is chosen exactly once, when the companion
 * first matures into adolescence ([EvolutionStage.TEEN]), from habit consistency via [ArchetypeSelector].
 */
object EvolutionEngine {

    /**
     * Adds [additionalXp] and updates the stage accordingly. Never changes the archetype;
     * see [reachesSpecialization] and [ArchetypeSelector].
     *
     * @param pet The current [Pet] state prior to XP addition.
     * @param additionalXp Non-negative experience points gained from the recent habit or interaction.
     * @return A new [Pet] instance with updated [Pet.stage] and cumulative [Pet.experiencePoints].
     */
    fun checkEvolution(pet: Pet, additionalXp: Int): Pet {
        val totalXp = pet.experiencePoints + additionalXp
        return pet.copy(
            stage = EvolutionStage.fromXp(totalXp),
            experiencePoints = totalXp
        )
    }

    /**
     * Whether going from [before] to [after] is the pet's first step into [EvolutionStage.TEEN] or beyond
     * while still [PetArchetype.BALANCED], i.e. the moment its archetype should be locked in.
     */
    fun reachesSpecialization(before: Pet, after: Pet): Boolean {
        return before.stage.level < EvolutionStage.TEEN.level &&
            after.stage.level >= EvolutionStage.TEEN.level &&
            after.archetype == PetArchetype.BALANCED
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.MoodCalculator
import com.healthcompanion.core.domain.engine.PetDecayEngine
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.model.Mood
import com.healthcompanion.core.model.Pet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PetWithMood(
    val pet: Pet,
    val mood: Mood
)

class GetPetStateUseCase(
    private val repository: PetRepository
) {
    fun execute(): Flow<PetWithMood> {
        return repository.getPetFlow().map { pet ->
            val decayedVitals = PetDecayEngine.calculateDecay(pet.vitals)
            val updatedPet = pet.copy(vitals = decayedVitals)
            val mood = MoodCalculator.calculateMood(decayedVitals)
            PetWithMood(updatedPet, mood)
        }
    }
}


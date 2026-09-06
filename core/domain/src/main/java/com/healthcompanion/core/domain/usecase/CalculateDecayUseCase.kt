// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.PetDecayEngine
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.model.Pet

class CalculateDecayUseCase(
    private val repository: PetRepository
) {
    suspend fun execute(currentTimeMillis: Long = System.currentTimeMillis()): Pet {
        val currentPet = repository.getPet()
        val decayedVitals = PetDecayEngine.calculateDecay(currentPet.vitals, currentTimeMillis)
        val updatedPet = currentPet.copy(vitals = decayedVitals)
        repository.updatePet(updatedPet)
        return updatedPet
    }
}


// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.PetDecayEngine
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.model.Pet

/**
 * Use case that explicitly applies time-delta decay and persists the resulting vitals to the database.
 *
 * Typically invoked by background workers (e.g. [com.healthcompanion.core.data.workers.PetDecayWorker])
 * or when preparing for long idle periods.
 *
 * @property repository The [PetRepository] for reading and persisting companion state.
 */
class CalculateDecayUseCase(
    private val repository: PetRepository
) {

    /**
     * Executes a read-modify-write operation: fetches the pet, applies decay up to [currentTimeMillis],
     * writes the decayed pet back to the database, and returns the updated pet.
     *
     * @param currentTimeMillis The epoch timestamp to compute decay up to (defaults to `System.currentTimeMillis()`).
     * @return The updated [Pet] instance with persisted decayed vitals.
     */
    suspend fun execute(currentTimeMillis: Long = System.currentTimeMillis()): Pet {
        val currentPet = repository.getPet()
        val decayedVitals = PetDecayEngine.calculateDecay(currentPet.vitals, currentTimeMillis)
        val updatedPet = currentPet.copy(vitals = decayedVitals)
        repository.updatePet(updatedPet)
        return updatedPet
    }
}


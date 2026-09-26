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
     * Executes an atomic read-modify-write operation: applies decay up to [currentTimeMillis]
     * to the stored pet, persists it, and returns the updated pet.
     *
     * @param currentTimeMillis The epoch timestamp to compute decay up to (defaults to `System.currentTimeMillis()`).
     * @return The updated [Pet] instance with persisted decayed vitals.
     */
    suspend fun execute(currentTimeMillis: Long = System.currentTimeMillis()): Pet {
        return repository.updatePet { pet ->
            pet.copy(vitals = PetDecayEngine.calculateDecay(pet.vitals, currentTimeMillis))
        }
    }
}


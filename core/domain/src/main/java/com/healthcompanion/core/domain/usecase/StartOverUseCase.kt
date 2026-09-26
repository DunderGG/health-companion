// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.model.Pet

/**
 * Starts over with a new pet (DD-52): the current pet, its evolution and archetype, and the whole habit
 * history are deleted, and a fresh default pet takes their place.
 *
 * Kept: the user's settings, and the sensor bookkeeping, so activity from before starting over (e.g.
 * today's steps so far) isn't credited to the new pet.
 *
 * @property repository Where the pet and its history live.
 */
class StartOverUseCase(
    private val repository: PetRepository
) {

    /** @return The new pet. */
    suspend fun execute(): Pet = repository.startOver()
}

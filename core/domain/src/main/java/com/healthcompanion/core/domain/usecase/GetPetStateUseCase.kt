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

/**
 * Composite domain state pairing a [Pet] instance with its dynamically calculated [Mood].
 *
 * @property pet Companion entity with up-to-date vitals.
 * @property mood Current emotional expression determined from the companion's vitals.
 */
data class PetWithMood(
    val pet: Pet,
    val mood: Mood
)

/**
 * Use case that observes the companion state and transforms it into a ready-to-render [PetWithMood] stream.
 *
 * ### Decay-on-Read Pattern:
 * The database stores the pet vitals as they were at the last write time. When this use case streams
 * data, it lazily evaluates elapsed time decay via [PetDecayEngine.calculateDecay] so the UI always reflects
 * current vitals without requiring background battery-draining timer writes.
 *
 * @property repository The [PetRepository] providing access to companion persistence.
 */
class GetPetStateUseCase(
    private val repository: PetRepository
) {

    /**
     * Executes the reactive stream query.
     *
     * ### Kotlin Flow Mechanics:
     * - `repository.getPetFlow()`: Upstream cold stream.
     * - `.map { ... }`: Intermediate operator transforming each emitted [Pet] into a [PetWithMood].
     *
     * @return Cold [Flow] emitting [PetWithMood] whenever the pet record changes.
     */
    fun execute(): Flow<PetWithMood> {
        return repository.getPetFlow().map { pet ->
            val decayedVitals = PetDecayEngine.calculateDecay(pet.vitals)
            val updatedPet = pet.copy(vitals = decayedVitals)
            val mood = MoodCalculator.calculateMood(decayedVitals)
            PetWithMood(updatedPet, mood)
        }
    }
}


// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.MoodCalculator
import com.healthcompanion.core.domain.engine.NightWindow
import com.healthcompanion.core.domain.engine.PetDecayEngine
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.Mood
import com.healthcompanion.core.model.Pet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

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
 * Decay is re-evaluated whenever the stored pet changes **and** every [refreshIntervalMillis] while the
 * stream is collected, so an open screen keeps advancing even when nothing is written. The ticker only
 * runs while someone collects (e.g. `WhileSubscribed` in the ViewModel), so it costs nothing in the background.
 *
 * @property repository The [PetRepository] providing access to companion persistence.
 * @property clock Source of "now" for decay evaluation.
 * @property refreshIntervalMillis How often decay is re-evaluated while collected (default: 1 minute).
 */
class GetPetStateUseCase(
    private val repository: PetRepository,
    private val clock: Clock,
    private val refreshIntervalMillis: Long = DEFAULT_REFRESH_INTERVAL_MS
) {

    /**
     * Executes the reactive stream query.
     *
     * ### Kotlin Flow Mechanics:
     * - `repository.getPetFlow()`: Upstream cold stream, re-emitting on every database write.
     * - `combine(..., ticker)`: Re-runs the transformation when either the pet or the ticker emits.
     *
     * @return Cold [Flow] emitting [PetWithMood] on every pet change and every refresh tick.
     */
    fun execute(): Flow<PetWithMood> {
        return combine(repository.getPetFlow(), ticker()) { pet, _ -> withMood(pet) }
    }

    /**
     * One-shot snapshot for pull-based surfaces (tile, complication) that cannot collect a stream.
     *
     * @return The stored pet with decay applied up to now, and its mood.
     */
    suspend fun current(): PetWithMood = withMood(repository.getPet())

    private fun withMood(pet: Pet): PetWithMood {
        val now = clock.nowMillis()
        val zone = clock.zone()
        val decayedVitals = PetDecayEngine.calculateDecay(pet.vitals, now, zone)
        val updatedPet = pet.copy(vitals = decayedVitals)
        val mood = MoodCalculator.calculateMood(decayedVitals, isNightTime = NightWindow.DEFAULT.isNight(now, zone))
        return PetWithMood(updatedPet, mood)
    }

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(refreshIntervalMillis)
        }
    }

    companion object {
        const val DEFAULT_REFRESH_INTERVAL_MS = 60_000L
    }
}

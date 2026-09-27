// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.usecase

import gg.dunder.thriveling.core.domain.engine.MoodCalculator
import gg.dunder.thriveling.core.domain.engine.NightWindow
import gg.dunder.thriveling.core.domain.engine.PetDecayEngine
import gg.dunder.thriveling.core.domain.repository.PetRepository
import gg.dunder.thriveling.core.domain.repository.SettingsRepository
import gg.dunder.thriveling.core.domain.time.Clock
import gg.dunder.thriveling.core.model.Mood
import gg.dunder.thriveling.core.model.Pet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

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
 * @property settingsRepository Source of the user's bedtime (DD-48); a changed bedtime re-evaluates immediately.
 * @property clock Source of "now" for decay evaluation.
 * @property refreshIntervalMillis How often decay is re-evaluated while collected (default: 1 minute).
 */
class GetPetStateUseCase(
    private val repository: PetRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val refreshIntervalMillis: Long = DEFAULT_REFRESH_INTERVAL_MS
) {

    /**
     * Executes the reactive stream query.
     *
     * ### Kotlin Flow Mechanics:
     * - `repository.getPetFlow()`: Upstream cold stream, re-emitting on every database write.
     * - `combine(..., bedtime, merge(ticker, refresh))`: Re-runs the transformation when the pet, the
     *   bedtime, the ticker or [refresh] emits.
     *
     * @param refresh Extra re-evaluation signals, e.g. the once-a-minute ambient update: in ambient mode
     *   the CPU may sleep through the ticker's `delay`, while the ambient callback is guaranteed (DD-44).
     * @return Cold [Flow] emitting [PetWithMood] on every pet change, refresh tick and [refresh] signal.
     */
    fun execute(refresh: Flow<Unit> = emptyFlow()): Flow<PetWithMood> {
        val bedtime = settingsRepository.getSettingsFlow().map { it.bedtime }.distinctUntilChanged()
        return combine(repository.getPetFlow(), bedtime, merge(ticker(), refresh)) { pet, nightWindow, _ ->
            withMood(pet, nightWindow)
        }
    }

    /**
     * One-shot snapshot for pull-based surfaces (tile, complication) that cannot collect a stream.
     *
     * @return The stored pet with decay applied up to now, and its mood.
     */
    suspend fun current(): PetWithMood = withMood(repository.getPet(), settingsRepository.getSettings().bedtime)

    private fun withMood(pet: Pet, nightWindow: NightWindow): PetWithMood {
        val now = clock.nowMillis()
        val zone = clock.zone()
        val decayedVitals = PetDecayEngine.calculateDecay(pet.vitals, now, zone, nightWindow)
        val updatedPet = pet.copy(vitals = decayedVitals)
        val mood = MoodCalculator.calculateMood(decayedVitals, isNightTime = nightWindow.isNight(now, zone))
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

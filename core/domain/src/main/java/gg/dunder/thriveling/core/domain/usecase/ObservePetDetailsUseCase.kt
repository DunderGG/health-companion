// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.usecase

import gg.dunder.thriveling.core.domain.engine.GoalHistory
import gg.dunder.thriveling.core.domain.repository.PetRepository
import gg.dunder.thriveling.core.domain.repository.SettingsRepository
import gg.dunder.thriveling.core.domain.time.Clock
import gg.dunder.thriveling.core.model.Pet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * What the pet details screen shows (DD-53).
 *
 * @property pet The pet, for its name, stage, XP and archetype.
 * @property goalHistory Days that reached each daily focus goal.
 */
data class PetDetails(val pet: Pet, val goalHistory: GoalHistory)

/**
 * Observes the pet and its goal record for the details screen (DD-53). The habit history is read from the
 * start of the day the pet was born, so a new pet (DD-52) starts a new stream.
 *
 * @property repository Source of the pet and its habit history.
 * @property settingsRepository Source of the user's daily goals.
 * @property clock Source of "now" and the local time zone.
 */
class ObservePetDetailsUseCase(
    private val repository: PetRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock
) {

    /** @return Cold [Flow] of the details, emitted again only when they change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun execute(): Flow<PetDetails> {
        val goals = settingsRepository.getSettingsFlow().map { it.dailyGoals }.distinctUntilChanged()
        val pets = repository.getPetFlow()

        return pets
            .map { it.bornTimestamp }
            .distinctUntilChanged()
            .flatMapLatest { born ->
                combine(pets, repository.habitEventsSinceFlow(startOfDay(born)), goals) { pet, events, dailyGoals ->
                    PetDetails(
                        pet = pet,
                        goalHistory = GoalHistory.of(events, dailyGoals, pet.bornTimestamp, clock.nowMillis(), clock.zone())
                    )
                }
            }
            .distinctUntilChanged()
    }

    private fun startOfDay(epochMillis: Long): Long = Instant.ofEpochMilli(epochMillis)
        .atZone(clock.zone())
        .toLocalDate()
        .atStartOfDay(clock.zone())
        .toInstant()
        .toEpochMilli()
}

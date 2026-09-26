// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.ArchetypeSelector
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.repository.SettingsRepository
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.PetArchetype
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Observes which daily focus goals today's habits have reached (DD-47): the per-day rules that choose the
 * pet's archetype ([ArchetypeSelector.focusAreasReached]), measured against the user's own goals (DD-48),
 * e.g. 6,000 steps for cardio by default.
 *
 * "Today" is evaluated on every emission, so a stream that stays open past midnight starts the new day
 * empty. Events are read from the start of the day the stream was opened; later days are always included.
 *
 * @property repository Source of the habit history.
 * @property settingsRepository Source of the user's daily goals.
 * @property clock Source of "now" and the local time zone.
 */
class ObserveDailyFocusUseCase(
    private val repository: PetRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock
) {

    /**
     * @return Cold [Flow] of today's reached focus areas, emitted again only when the set changes. A changed
     *   goal re-evaluates the set immediately.
     */
    fun execute(): Flow<Set<PetArchetype>> {
        val startOfToday = localDay(clock.nowMillis())
            .atStartOfDay(clock.zone())
            .toInstant()
            .toEpochMilli()

        val goals = settingsRepository.getSettingsFlow().map { it.dailyGoals }.distinctUntilChanged()

        return combine(repository.habitEventsSinceFlow(startOfToday), goals) { events, dailyGoals ->
            val today = localDay(clock.nowMillis())
            ArchetypeSelector.focusAreasReached(events.filter { localDay(it.timestampMillis) == today }, dailyGoals)
        }
            .distinctUntilChanged()
    }

    private fun localDay(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(clock.zone()).toLocalDate()
}

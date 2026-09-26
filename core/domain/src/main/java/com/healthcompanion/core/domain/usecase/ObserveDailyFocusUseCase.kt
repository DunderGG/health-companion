// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.ArchetypeSelector
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.PetArchetype
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Observes which daily focus goals today's habits have reached (DD-47): the same per-day rules that
 * choose the pet's archetype ([ArchetypeSelector.focusAreasReached]), e.g. 6,000 steps for cardio.
 *
 * "Today" is evaluated on every emission, so a stream that stays open past midnight starts the new day
 * empty. Events are read from the start of the day the stream was opened; later days are always included.
 *
 * @property repository Source of the habit history.
 * @property clock Source of "now" and the local time zone.
 */
class ObserveDailyFocusUseCase(
    private val repository: PetRepository,
    private val clock: Clock
) {

    /**
     * @return Cold [Flow] of today's reached focus areas, emitted again only when the set changes.
     */
    fun execute(): Flow<Set<PetArchetype>> {
        val startOfToday = localDay(clock.nowMillis())
            .atStartOfDay(clock.zone())
            .toInstant()
            .toEpochMilli()

        return repository.habitEventsSinceFlow(startOfToday)
            .map { events ->
                val today = localDay(clock.nowMillis())
                ArchetypeSelector.focusAreasReached(events.filter { localDay(it.timestampMillis) == today })
            }
            .distinctUntilChanged()
    }

    private fun localDay(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(clock.zone()).toLocalDate()
}

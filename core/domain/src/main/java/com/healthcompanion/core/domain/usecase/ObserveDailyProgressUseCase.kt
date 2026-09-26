// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.DailyProgress
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.repository.SettingsRepository
import com.healthcompanion.core.domain.settings.DailyGoals
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.HabitEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Observes today's progress towards the daily focus goals (DD-47, DD-49): the totals shown on the goals
 * page, and the goals they meet ([DailyProgress.goalsMet]) for the goal vibration (DD-54). Progress is measured
 * against the user's own goals (DD-48), e.g. 6,000 steps for cardio by default.
 *
 * "Today" is evaluated on every emission, so a stream that stays open past midnight starts the new day
 * empty. Events are read from the start of the day the stream was opened; later days are always included.
 *
 * @property repository Source of the habit history.
 * @property settingsRepository Source of the user's daily goals.
 * @property clock Source of "now" and the local time zone.
 */
class ObserveDailyProgressUseCase(
    private val repository: PetRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock
) {

    /**
     * @return Cold [Flow] of today's progress, emitted again only when it changes. A changed goal
     *   re-evaluates it immediately.
     */
    fun execute(): Flow<DailyProgress> {
        val goals = settingsRepository.getSettingsFlow().map { it.dailyGoals }.distinctUntilChanged()

        return combine(repository.habitEventsSinceFlow(startOfToday()), goals, ::today)
            .distinctUntilChanged()
    }

    /** One-shot snapshot for pull-based surfaces, such as the step complication (DD-51). */
    suspend fun current(): DailyProgress =
        today(repository.habitEventsSinceFlow(startOfToday()).first(), settingsRepository.getSettings().dailyGoals)

    private fun today(events: List<HabitEvent>, goals: DailyGoals): DailyProgress {
        val today = localDay(clock.nowMillis())
        return DailyProgress.of(events.filter { localDay(it.timestampMillis) == today }, goals)
    }

    private fun startOfToday(): Long = localDay(clock.nowMillis())
        .atStartOfDay(clock.zone())
        .toInstant()
        .toEpochMilli()

    private fun localDay(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(clock.zone()).toLocalDate()
}

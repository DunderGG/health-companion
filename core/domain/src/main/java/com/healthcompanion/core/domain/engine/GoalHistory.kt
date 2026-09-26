// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.domain.settings.DailyGoals
import com.healthcompanion.core.model.HabitEvent
import com.healthcompanion.core.model.PetArchetype
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * How many days in a stretch of days reached each daily focus goal (DD-53).
 *
 * @property days Length of the stretch, today included.
 * @property reachedDays Days that reached each goal, keyed by the archetype the goal counts towards
 *   ([PetArchetype.CARDIO_RUNNER], [PetArchetype.IRON_BEAST], [PetArchetype.ZEN_SAGE]); a goal never
 *   reached is missing.
 */
data class GoalTally(val days: Int, val reachedDays: Map<PetArchetype, Int>) {
    /** Days in the stretch that reached the goal counting towards [area]. */
    fun daysReached(area: PetArchetype): Int = reachedDays[area] ?: 0
}

/**
 * The pet's goal record for the details screen (DD-53): days that reached each daily focus goal in the last
 * [ArchetypeSelector.WINDOW_DAYS] days, and since the pet was born. Each day is measured with
 * [DailyProgress], like the goals page, against the user's current goals.
 *
 * @property lastWeek The last [ArchetypeSelector.WINDOW_DAYS] days, or fewer for a younger pet.
 * @property sinceBirth Every day since the pet was born.
 * @property today Today's progress, shown instead of day counts while the pet is one day old.
 */
data class GoalHistory(val lastWeek: GoalTally, val sinceBirth: GoalTally, val today: DailyProgress) {

    companion object {
        /**
         * @param events Habit events since the pet was born; anything later than [nowMillis] is ignored.
         * @param goals The targets to measure each day against.
         * @param bornMillis When the pet was born; its local day is the first day counted.
         * @param nowMillis Now; its local day is the last day counted, even while still in progress.
         * @param zone Time zone defining local days.
         */
        fun of(
            events: List<HabitEvent>,
            goals: DailyGoals,
            bornMillis: Long,
            nowMillis: Long,
            zone: ZoneId
        ): GoalHistory {
            val today = localDay(nowMillis, zone)
            val bornDay = minOf(localDay(bornMillis, zone), today)
            val weekStart = maxOf(bornDay, today.minusDays((ArchetypeSelector.WINDOW_DAYS - 1).toLong()))

            val progressByDay: Map<LocalDate, DailyProgress> = events
                .filter { it.timestampMillis <= nowMillis }
                .groupBy { localDay(it.timestampMillis, zone) }
                .filterKeys { it in bornDay..today }
                .mapValues { (_, dayEvents) -> DailyProgress.of(dayEvents, goals) }
            val reachedByDay = progressByDay.mapValues { (_, progress) -> progress.reached }

            return GoalHistory(
                lastWeek = tally(reachedByDay, weekStart, today),
                sinceBirth = tally(reachedByDay, bornDay, today),
                today = progressByDay[today] ?: DailyProgress(goals)
            )
        }

        private fun tally(reachedByDay: Map<LocalDate, Set<PetArchetype>>, first: LocalDate, last: LocalDate): GoalTally {
            val inRange = reachedByDay.filterKeys { it in first..last }.values
            return GoalTally(
                days = ChronoUnit.DAYS.between(first, last).toInt() + 1,
                reachedDays = inRange.flatten().groupingBy { it }.eachCount()
            )
        }

        private fun localDay(epochMillis: Long, zone: ZoneId): LocalDate =
            Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import kotlin.math.floor

/**
 * How much of a cumulative daily total has already been applied to the pet.
 *
 * @property epochDay Local calendar day ([java.time.LocalDate.toEpochDay]) the total belongs to.
 * @property consumedTotal Portion of that day's running total already turned into habits.
 */
data class DailyTotalBaseline(
    val epochDay: Long,
    val consumedTotal: Double
)

/**
 * Result of consuming a new daily total reading.
 *
 * @property consumed Newly consumed amount to apply to the pet (always `>= 0`, a multiple of the granularity).
 * @property baseline Baseline to persist for the next reading.
 */
data class DailyTotalUpdate(
    val consumed: Double,
    val baseline: DailyTotalBaseline
)

/**
 * Converts cumulative "since local midnight" totals (e.g. Health Services `STEPS_DAILY`)
 * into non-negative deltas that can be applied to the pet exactly once.
 *
 * Deltas are consumed in whole multiples of a granularity (e.g. 200 steps). The remainder stays
 * unconsumed and carries over to the next reading, so small batches never lose progress to
 * integer rounding in XP calculations.
 *
 * Rules:
 * - **New day** (or no baseline yet): everything since midnight is new.
 * - **Same day**: only the increase over the consumed total is new.
 * - **Earlier day** (stale reading delivered after rollover): ignored.
 * - **Total below the consumed total on the same day**: ignored, baseline kept. This is usually an
 *   out-of-order batch; re-anchoring would double count once the newer total arrives again. After a
 *   genuine sensor counter reset this under-counts until the total catches up, which is the safer failure.
 *
 * ### Kotlin vs C++ Note:
 * - **`object`**: A stateless singleton namespace, similar to a C++ namespace of free functions.
 */
object DailyTotalTracker {

    /**
     * @param previous Persisted baseline, or `null` if this data type has never been consumed.
     * @param epochDay Local calendar day of the new reading.
     * @param total Cumulative total for [epochDay] reported by the sensor.
     * @param granularity Smallest unit to consume; the remainder carries over.
     */
    fun consume(
        previous: DailyTotalBaseline?,
        epochDay: Long,
        total: Double,
        granularity: Double
    ): DailyTotalUpdate {
        require(granularity > 0.0) { "Granularity must be positive" }

        val alreadyConsumed = when {
            previous == null || epochDay > previous.epochDay -> 0.0
            epochDay < previous.epochDay -> return DailyTotalUpdate(0.0, previous)
            total <= previous.consumedTotal -> return DailyTotalUpdate(0.0, previous)
            else -> previous.consumedTotal
        }

        val delta = total - alreadyConsumed
        val consumed = floor(delta / granularity) * granularity
        return DailyTotalUpdate(consumed, DailyTotalBaseline(epochDay, alreadyConsumed + consumed))
    }
}

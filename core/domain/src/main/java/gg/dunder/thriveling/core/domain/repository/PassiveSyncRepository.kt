// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.repository

/**
 * Persists bookkeeping for passive sensor ingestion, so each piece of sensor data
 * is applied to the pet at most once.
 *
 * Every method must be atomic with respect to concurrent callers: two sensor batches
 * processed in parallel must never both consume the same delta.
 */
interface PassiveSyncRepository {

    /**
     * Atomically consumes the unapplied part of a cumulative daily total.
     * See [gg.dunder.thriveling.core.domain.engine.DailyTotalTracker] for the rules.
     *
     * @param key Stable identifier of the data type (e.g. `"steps_daily"`).
     * @param epochDay Local calendar day of the reading.
     * @param total Cumulative total for that day.
     * @param granularity Smallest unit to consume; the remainder carries over.
     * @return The newly consumed amount (`>= 0`).
     */
    suspend fun consumeDailyTotal(key: String, epochDay: Long, total: Double, granularity: Double): Double

    /**
     * Atomically claims the right to award a heart-rate habit, limiting it to once per [minIntervalMillis].
     *
     * @return `true` if the caller may award the habit now (the claim is recorded), `false` otherwise.
     */
    suspend fun tryClaimHeartRateAward(nowMillis: Long, minIntervalMillis: Long): Boolean
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthcompanion.core.domain.engine.DailyTotalBaseline
import com.healthcompanion.core.domain.engine.DailyTotalTracker
import com.healthcompanion.core.domain.repository.PassiveSyncRepository

/**
 * [PassiveSyncRepository] backed by Jetpack DataStore Preferences.
 *
 * DataStore's `edit { }` serializes all read-modify-write transforms on the file, so concurrent
 * sensor batches can never consume the same delta twice. Kept outside Room on purpose: adding a
 * table would require a schema migration (see AR-8 in `docs/ARCHITECTURE.md`).
 *
 * ### Kotlin vs C++ Note:
 * - **`DataStore.edit { }`**: A suspending, serialized transaction on a small key-value file —
 *   comparable to a mutex-guarded read/modify/`fsync` of a config file.
 *
 * @property dataStore Preferences store holding the sync bookkeeping.
 */
class PassiveSyncRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : PassiveSyncRepository {

    override suspend fun consumeDailyTotal(
        key: String,
        epochDay: Long,
        total: Double,
        granularity: Double
    ): Double {
        val dayKey = longPreferencesKey("${key}_epoch_day")
        val consumedKey = doublePreferencesKey("${key}_consumed_total")
        var consumed = 0.0

        dataStore.edit { prefs ->
            val previous = prefs[dayKey]?.let { day ->
                DailyTotalBaseline(epochDay = day, consumedTotal = prefs[consumedKey] ?: 0.0)
            }
            val update = DailyTotalTracker.consume(previous, epochDay, total, granularity)
            prefs[dayKey] = update.baseline.epochDay
            prefs[consumedKey] = update.baseline.consumedTotal
            consumed = update.consumed
        }
        return consumed
    }

    override suspend fun tryClaimHeartRateAward(nowMillis: Long, minIntervalMillis: Long): Boolean {
        var claimed = false

        dataStore.edit { prefs ->
            val last = prefs[LAST_HEART_RATE_AWARD_KEY]
            claimed = last == null || nowMillis - last >= minIntervalMillis || nowMillis < last
            if (claimed) {
                prefs[LAST_HEART_RATE_AWARD_KEY] = nowMillis
            }
        }
        return claimed
    }

    companion object {
        private val LAST_HEART_RATE_AWARD_KEY = longPreferencesKey("heart_rate_last_award_millis")

        // DataStore requires exactly one instance per file per process; the delegate guarantees it.
        private val Context.passiveSyncDataStore by preferencesDataStore(name = "passive_sync")

        /**
         * Returns a repository backed by the process-wide `passive_sync` DataStore.
         */
        fun getInstance(context: Context): PassiveSyncRepositoryImpl {
            return PassiveSyncRepositoryImpl(context.applicationContext.passiveSyncDataStore)
        }
    }
}

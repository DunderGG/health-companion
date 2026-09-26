// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthcompanion.core.domain.engine.CriticalVital
import com.healthcompanion.core.domain.repository.VitalAlertStateRepository
import kotlinx.coroutines.flow.first

/**
 * [VitalAlertStateRepository] backed by its own small DataStore file (`vital_alerts`).
 *
 * Stored as enum names; names that no longer exist are ignored on read.
 *
 * @property dataStore Preferences store holding the notified set.
 */
class VitalAlertStateRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : VitalAlertStateRepository {

    override suspend fun notifiedVitals(): Set<CriticalVital> {
        val names = dataStore.data.first()[NOTIFIED_KEY].orEmpty()
        return CriticalVital.entries.filter { it.name in names }.toSet()
    }

    override suspend fun setNotifiedVitals(vitals: Set<CriticalVital>) {
        dataStore.edit { prefs -> prefs[NOTIFIED_KEY] = vitals.map { it.name }.toSet() }
    }

    companion object {
        private val NOTIFIED_KEY = stringSetPreferencesKey("notified_vitals")

        // DataStore requires exactly one instance per file per process; the delegate guarantees it.
        private val Context.vitalAlertsDataStore by preferencesDataStore(name = "vital_alerts")

        /**
         * Returns a repository backed by the process-wide `vital_alerts` DataStore.
         */
        fun getInstance(context: Context): VitalAlertStateRepositoryImpl {
            return VitalAlertStateRepositoryImpl(context.applicationContext.vitalAlertsDataStore)
        }
    }
}

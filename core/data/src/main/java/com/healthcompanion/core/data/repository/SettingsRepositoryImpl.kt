// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthcompanion.core.domain.engine.NightWindow
import com.healthcompanion.core.domain.repository.SettingsRepository
import com.healthcompanion.core.domain.settings.DailyGoals
import com.healthcompanion.core.domain.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] backed by its own small DataStore file (`user_settings`).
 *
 * Missing keys read as the defaults, so nothing is written until the user changes something. A stored
 * value the current app wouldn't offer (e.g. from a future version with wider ranges) falls back to its
 * default instead of failing.
 *
 * @property dataStore Preferences store holding the settings.
 */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override fun getSettingsFlow(): Flow<UserSettings> = dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    override suspend fun getSettings(): UserSettings = dataStore.data.first().toSettings()

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings): UserSettings {
        var updated = UserSettings.DEFAULT
        dataStore.edit { prefs ->
            updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
        return updated
    }

    private fun Preferences.toSettings(): UserSettings {
        val defaultGoals = DailyGoals.DEFAULT
        val goals = DailyGoals(
            steps = this[STEPS_KEY].validIn(DailyGoals.STEPS) ?: defaultGoals.steps,
            waterMl = this[WATER_ML_KEY].validIn(DailyGoals.WATER_ML) ?: defaultGoals.waterMl,
            healthyMeals = this[HEALTHY_MEALS_KEY].validIn(DailyGoals.HEALTHY_MEALS) ?: defaultGoals.healthyMeals
        )

        val start = this[BEDTIME_START_KEY]
        val end = this[BEDTIME_END_KEY]
        val bedtime = if (start != null && end != null &&
            start in UserSettings.BEDTIME_HOURS && end in UserSettings.WAKE_UP_HOURS
        ) {
            NightWindow(startHour = start, endHour = end)
        } else {
            NightWindow.DEFAULT
        }

        return UserSettings(
            dailyGoals = goals,
            bedtime = bedtime,
            hapticsEnabled = this[HAPTICS_KEY] ?: UserSettings.DEFAULT.hapticsEnabled
        )
    }

    private fun MutablePreferences.write(settings: UserSettings) {
        this[STEPS_KEY] = settings.dailyGoals.steps
        this[WATER_ML_KEY] = settings.dailyGoals.waterMl
        this[HEALTHY_MEALS_KEY] = settings.dailyGoals.healthyMeals
        this[BEDTIME_START_KEY] = settings.bedtime.startHour
        this[BEDTIME_END_KEY] = settings.bedtime.endHour
        this[HAPTICS_KEY] = settings.hapticsEnabled
    }

    /** This value if it is one of [progression]'s values, otherwise `null`. */
    private fun Int?.validIn(progression: IntProgression): Int? = this?.takeIf { it in progression }

    companion object {
        private val STEPS_KEY = intPreferencesKey("goal_steps")
        private val WATER_ML_KEY = intPreferencesKey("goal_water_ml")
        private val HEALTHY_MEALS_KEY = intPreferencesKey("goal_healthy_meals")
        private val BEDTIME_START_KEY = intPreferencesKey("bedtime_start_hour")
        private val BEDTIME_END_KEY = intPreferencesKey("bedtime_end_hour")
        private val HAPTICS_KEY = booleanPreferencesKey("haptics_enabled")

        // DataStore requires exactly one instance per file per process; the delegate guarantees it.
        private val Context.userSettingsDataStore by preferencesDataStore(name = "user_settings")

        /**
         * Returns a repository backed by the process-wide `user_settings` DataStore.
         */
        fun getInstance(context: Context): SettingsRepositoryImpl {
            return SettingsRepositoryImpl(context.applicationContext.userSettingsDataStore)
        }
    }
}

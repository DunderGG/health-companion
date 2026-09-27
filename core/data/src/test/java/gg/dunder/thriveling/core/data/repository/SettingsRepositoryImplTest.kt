// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import gg.dunder.thriveling.core.domain.engine.NightWindow
import gg.dunder.thriveling.core.domain.settings.DailyGoals
import gg.dunder.thriveling.core.domain.settings.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            tempFolder.newFile("user_settings_test.preferences_pb").apply { delete() }
        }
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun `an empty store reads as the defaults`() = runBlocking {
        assertEquals(UserSettings.DEFAULT, SettingsRepositoryImpl(dataStore).getSettings())
    }

    @Test
    fun `changes round-trip and are emitted`() = runBlocking {
        val repository = SettingsRepositoryImpl(dataStore)
        val changed = UserSettings(
            dailyGoals = DailyGoals(steps = 8_500, waterMl = 2_000, healthyMeals = 3),
            bedtime = NightWindow(startHour = 0, endHour = 8),
            hapticsEnabled = false
        )

        val stored = repository.updateSettings { changed }

        assertEquals(changed, stored)
        assertEquals(changed, repository.getSettings())
        assertEquals(changed, repository.getSettingsFlow().first())
    }

    @Test
    fun `stored values the app doesn't offer fall back to their defaults`() = runBlocking {
        dataStore.edit {
            it[intPreferencesKey("goal_steps")] = 1_234 // not a multiple of 500
            it[intPreferencesKey("goal_healthy_meals")] = 4
            it[intPreferencesKey("bedtime_start_hour")] = 12 // not a bedtime option
            it[intPreferencesKey("bedtime_end_hour")] = 6
        }

        val settings = SettingsRepositoryImpl(dataStore).getSettings()

        assertEquals(DailyGoals(healthyMeals = 4), settings.dailyGoals)
        assertEquals(NightWindow.DEFAULT, settings.bedtime)
    }
}

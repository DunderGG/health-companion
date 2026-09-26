// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.repository

import com.healthcompanion.core.domain.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Persistence for the user's [UserSettings] (DD-48).
 */
interface SettingsRepository {

    /** The current settings, re-emitted after every change. */
    fun getSettingsFlow(): Flow<UserSettings>

    /** One-shot read, for pull-based surfaces and background work. */
    suspend fun getSettings(): UserSettings

    /**
     * Atomically replaces the settings with [transform] applied to the current ones.
     *
     * @return The stored settings.
     */
    suspend fun updateSettings(transform: (UserSettings) -> UserSettings): UserSettings
}

/**
 * [SettingsRepository] held in memory only: for tests and previews.
 *
 * @param initial The settings to start with.
 */
class InMemorySettingsRepository(initial: UserSettings = UserSettings.DEFAULT) : SettingsRepository {

    private val settings = MutableStateFlow(initial)

    override fun getSettingsFlow(): Flow<UserSettings> = settings

    override suspend fun getSettings(): UserSettings = settings.value

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings): UserSettings {
        settings.update(transform)
        return settings.value
    }
}

/**
 * [SettingsRepository] decorator that invokes [onSettingsChanged] after every committed change, like
 * [NotifyingPetRepository]: a new bedtime changes the mood shown on the tile and complication, and when
 * alerts may fire.
 *
 * @property delegate The repository doing the actual work.
 * @property onSettingsChanged Called after each successful write.
 */
class NotifyingSettingsRepository(
    private val delegate: SettingsRepository,
    private val onSettingsChanged: () -> Unit
) : SettingsRepository by delegate {

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings): UserSettings =
        delegate.updateSettings(transform).also { onSettingsChanged() }
}

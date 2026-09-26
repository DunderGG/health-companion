// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthcompanion.core.domain.repository.SettingsRepository
import com.healthcompanion.core.domain.settings.UserSettings
import com.healthcompanion.core.domain.usecase.StartOverUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State and actions of the settings screens (DD-48). Every change is saved at once; there is no
 * "confirm" step, as in the system settings.
 *
 * @param settingsRepository Where the settings are stored.
 * @param startOverUseCase Replaces the pet with a new one (DD-52).
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val startOverUseCase: StartOverUseCase
) : ViewModel() {

    /** The stored settings, or `null` until they have been read. */
    val settings: StateFlow<UserSettings?> = settingsRepository.getSettingsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /**
     * Stores [value] for [field].
     *
     * @param value One of [SettingField.options].
     */
    fun setValue(field: SettingField, value: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings { field.update(it, value) }
        }
    }

    /**
     * Deletes the pet and its history and starts over with a new pet (DD-52). The caller must have asked
     * the user to confirm.
     *
     * @param onDone Called on the main thread once the new pet is stored.
     */
    fun startOver(onDone: () -> Unit) {
        viewModelScope.launch {
            startOverUseCase.execute()
            onDone()
        }
    }

    /** Switches the pet's vibration patterns on or off. */
    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(hapticsEnabled = enabled) }
        }
    }
}

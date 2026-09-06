// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthcompanion.core.domain.usecase.GetPetStateUseCase
import com.healthcompanion.core.domain.usecase.LogHabitUseCase
import com.healthcompanion.core.model.HabitType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PetViewModel(
    private val getPetStateUseCase: GetPetStateUseCase,
    private val logHabitUseCase: LogHabitUseCase
) : ViewModel() {

    private val _isPetting = MutableStateFlow(false)
    private var lastPetTimestamp: Long = 0L

    companion object {
        const val PET_COOLDOWN_MS = 10_000L // 10-second cooldown between petting interactions
    }

    val uiState: StateFlow<PetUiState> = combine(
        getPetStateUseCase.execute(),
        _isPetting
    ) { petWithMood, isPetting ->
        PetUiState.Success(
            pet = petWithMood.pet,
            mood = petWithMood.mood,
            isPettingFeedbackActive = isPetting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PetUiState.Loading
    )

    fun logWater(ml: Int = 250) {
        viewModelScope.launch {
            logHabitUseCase.execute(HabitType.Hydration(ml))
        }
    }

    fun logMeal(isHealthy: Boolean) {
        viewModelScope.launch {
            logHabitUseCase.execute(HabitType.Meal(isHealthy = isHealthy))
        }
    }

    fun petCompanion(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastPetTimestamp < PET_COOLDOWN_MS || _isPetting.value) {
            return false
        }
        lastPetTimestamp = now
        viewModelScope.launch {
            _isPetting.value = true
            logHabitUseCase.execute(HabitType.PettingInteraction(1.0f))
            delay(1500)
            _isPetting.value = false
        }
        return true
    }
}


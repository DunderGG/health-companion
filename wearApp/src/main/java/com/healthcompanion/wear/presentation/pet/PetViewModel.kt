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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PetViewModel(
    private val getPetStateUseCase: GetPetStateUseCase,
    private val logHabitUseCase: LogHabitUseCase
) : ViewModel() {

    private val _isPetting = MutableStateFlow(false)

    val uiState: StateFlow<PetUiState> = getPetStateUseCase.execute()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        ).let { flow ->
            val result = MutableStateFlow<PetUiState>(PetUiState.Loading)
            viewModelScope.launch {
                flow.collect { petWithMood ->
                    if (petWithMood != null) {
                        result.value = PetUiState.Success(
                            pet = petWithMood.pet,
                            mood = petWithMood.mood,
                            isPettingFeedbackActive = _isPetting.value
                        )
                    }
                }
            }
            result.asStateFlow()
        }

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

    fun petCompanion() {
        viewModelScope.launch {
            _isPetting.value = true
            logHabitUseCase.execute(HabitType.PettingInteraction(1.0f))
            delay(1200)
            _isPetting.value = false
        }
    }
}


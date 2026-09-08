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

/**
 * ViewModel managing the presentation state and user interactions for the Wear OS companion screen.
 *
 * ### Kotlin vs C++ Note:
 * - **`viewModelScope` & RAII Lifetime**: Coroutines launched in `viewModelScope` are bound to the
 *   lifespan of the ViewModel. When the user exits the screen, `viewModelScope` is cancelled automatically,
 *   cancelling all pending child tasks (analogous to C++20 `std::jthread` joining/cancelling on destruction).
 * - **Reactive Combination (`combine`)**: Merges the database stream (`GetPetStateUseCase`) and local UI state
 *   (`_isPetting`) into a single unified [PetUiState.Success] output stream.
 * - **Hot State (`.stateIn`)**:
 *   - Converts a cold stream into a hot, replayable `StateFlow` with an initial [PetUiState.Loading] value.
 *   - `SharingStarted.WhileSubscribed(5000)`: Upstream flows are kept alive for 5 seconds after the last UI
 *     collector leaves (preventing database reconnect thrashing during rapid screen rotations or ambient transitions).
 *
 * @param getPetStateUseCase Domain use case observing pet vitals and calculated mood.
 * @param logHabitUseCase Domain use case dispatching health habits and interactions.
 */
class PetViewModel(
    private val getPetStateUseCase: GetPetStateUseCase,
    private val logHabitUseCase: LogHabitUseCase
) : ViewModel() {

    private val _isPetting = MutableStateFlow(false)
    private var lastPetTimestamp: Long = 0L

    companion object {
        /** Minimum cooldown interval (10 seconds) required between touch petting interactions. */
        const val PET_COOLDOWN_MS = 10_000L
    }

    /**
     * Hot observable stream of [PetUiState] driving the Composable UI.
     * Emits [PetUiState.Loading] until the database yields the first pet snapshot.
     */
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

    /**
     * Logs hydration intake in response to user tapping the water quick-action token.
     *
     * @param ml Volume in milliliters (defaults to 250 mL).
     */
    fun logWater(ml: Int = 250) {
        viewModelScope.launch {
            logHabitUseCase.execute(HabitType.Hydration(ml))
        }
    }

    /**
     * Logs nutrition intake in response to user tapping the meal quick-action token.
     *
     * @param isHealthy `true` if wholesome nutrition; `false` if indulgent snack.
     */
    fun logMeal(isHealthy: Boolean) {
        viewModelScope.launch {
            logHabitUseCase.execute(HabitType.Meal(isHealthy = isHealthy))
        }
    }

    /**
     * Handles direct touch interaction on the companion character sprite.
     *
     * ### Cooldown & Concurrency:
     * - Enforces a 10-second debounce cooldown ([PET_COOLDOWN_MS]) between petting sessions.
     * - Activates [_isPetting] state for 1500ms to drive the UI heart burst and bouncy hop.
     * - Dispatches [HabitType.PettingInteraction] to award happiness and XP in the game engine.
     *
     * @return `true` if petting was accepted; `false` if rejected due to active cooldown.
     */
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


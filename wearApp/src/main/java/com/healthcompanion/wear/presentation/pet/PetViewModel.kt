// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthcompanion.core.domain.repository.SettingsRepository
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.domain.usecase.GetPetStateUseCase
import com.healthcompanion.core.domain.usecase.LogHabitUseCase
import com.healthcompanion.core.domain.usecase.ObserveDailyFocusUseCase
import com.healthcompanion.core.domain.usecase.ObservePetActivityUseCase
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Mood
import com.healthcompanion.core.model.PetActivity
import com.healthcompanion.wear.haptics.PetHapticEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing the presentation state and user interactions for the Wear OS companion screen.
 *
 * ### Kotlin vs C++ Note:
 * - **`viewModelScope` & RAII Lifetime**: Coroutines launched in `viewModelScope` are bound to the
 *   lifespan of the ViewModel. When the user exits the screen, `viewModelScope` is cancelled automatically,
 *   cancelling all pending child tasks (analogous to C++20 `std::jthread` joining/cancelling on destruction).
 * - **Reactive Combination (`combine`)**: Merges the database stream (`GetPetStateUseCase`), local UI state
 *   (`_isPetting`) and the live step reaction (`ObservePetActivityUseCase`) into a single unified
 *   [PetUiState.Success] output stream.
 * - **Hot State (`.stateIn`)**:
 *   - Converts a cold stream into a hot, replayable `StateFlow` with an initial [PetUiState.Loading] value.
 *   - `SharingStarted.WhileSubscribed(5000)`: Upstream flows are kept alive for 5 seconds after the last UI
 *     collector leaves (preventing database reconnect thrashing during rapid screen rotations or ambient transitions).
 *
 * @param getPetStateUseCase Domain use case observing pet vitals and calculated mood.
 * @param logHabitUseCase Domain use case dispatching health habits and interactions.
 * @param observePetActivityUseCase Live walking/running reaction to the user's steps.
 * @param observeDailyFocusUseCase Today's reached focus goals, for the goal haptic (DD-47).
 * @param settingsRepository Whether haptics are switched on (DD-48).
 * @param clock Source of "now" for the petting cooldown.
 */
class PetViewModel(
    private val getPetStateUseCase: GetPetStateUseCase,
    private val logHabitUseCase: LogHabitUseCase,
    private val observePetActivityUseCase: ObservePetActivityUseCase,
    private val observeDailyFocusUseCase: ObserveDailyFocusUseCase,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock
) : ViewModel() {

    private val _isPetting = MutableStateFlow(false)
    private var lastPetTimestamp: Long = 0L

    private val isAmbient = MutableStateFlow(false)
    private val ambientUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val pettingAccepted = MutableSharedFlow<PetHapticEvent>(extraBufferCapacity = 1)

    companion object {
        /** Minimum cooldown interval (10 seconds) required between touch petting interactions. */
        const val PET_COOLDOWN_MS = 10_000L
    }

    /**
     * Live gait, or [PetActivity.IDLE] without touching the step sensor while in ambient mode:
     * `flatMapLatest` cancels the sensor flow (and unregisters the listener) on entering ambient (DD-44).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val activity = isAmbient.flatMapLatest { ambient ->
        if (ambient) flowOf(PetActivity.IDLE) else observePetActivityUseCase.execute()
    }

    /**
     * Hot observable stream of [PetUiState] driving the Composable UI.
     * Emits [PetUiState.Loading] until the database yields the first pet snapshot.
     */
    val uiState: StateFlow<PetUiState> = combine(
        getPetStateUseCase.execute(refresh = ambientUpdates),
        _isPetting,
        activity
    ) { petWithMood, isPetting, activity ->
        PetUiState.Success(
            pet = petWithMood.pet,
            mood = petWithMood.mood,
            isPettingFeedbackActive = isPetting,
            // A sleeping pet stays asleep rather than sleepwalking alongside the user (DD-39).
            activity = if (petWithMood.mood == Mood.SLEEPING) PetActivity.IDLE else activity
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PetUiState.Loading
    )

    /**
     * Moments to play as vibration patterns (DD-47): an accepted pet, the pet evolving, and a daily focus
     * goal being reached.
     *
     * Cold, and meant to be collected only while the pet UI is shown: each collection takes the current
     * stage and goals as its baseline, so opening the app never replays something that happened while it
     * was closed. Nothing is emitted while the user has switched haptics off.
     */
    val hapticEvents: Flow<PetHapticEvent> = merge(
        pettingAccepted,
        uiState
            .filterIsInstance<PetUiState.Success>()
            .map { it.pet.stage }
            .distinctUntilChanged()
            .changes()
            .filter { (before, after) -> after.level > before.level }
            .map { PetHapticEvent.EVOLUTION },
        observeDailyFocusUseCase.execute()
            .changes()
            .filter { (before, after) -> (after - before).isNotEmpty() }
            .map { PetHapticEvent.GOAL_REACHED }
    ).filter { settingsRepository.getSettings().hapticsEnabled }

    /**
     * Called when the activity enters or leaves ambient (always-on) mode.
     *
     * @param ambient `true` on entering ambient mode.
     */
    fun setAmbient(ambient: Boolean) {
        isAmbient.value = ambient
    }

    /** Called on the once-a-minute ambient update, so decay and mood are re-evaluated. */
    fun onAmbientUpdate() {
        ambientUpdates.tryEmit(Unit)
    }

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
        val now = clock.nowMillis()
        if (now - lastPetTimestamp < PET_COOLDOWN_MS || _isPetting.value) {
            return false
        }
        lastPetTimestamp = now
        pettingAccepted.tryEmit(PetHapticEvent.PETTING)
        viewModelScope.launch {
            _isPetting.value = true
            logHabitUseCase.execute(HabitType.PettingInteraction(1.0f))
            delay(1500)
            _isPetting.value = false
        }
        return true
    }
}

/**
 * Consecutive pairs `(previous, current)`; the first value only becomes the baseline and is not emitted.
 */
private fun <T> Flow<T>.changes(): Flow<Pair<T, T>> = flow {
    var previous: Any? = NoValue
    collect { current ->
        @Suppress("UNCHECKED_CAST")
        if (previous !== NoValue) emit(previous as T to current)
        previous = current
    }
}

private object NoValue

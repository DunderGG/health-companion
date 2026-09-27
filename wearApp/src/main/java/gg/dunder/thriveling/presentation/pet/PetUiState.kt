// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.presentation.pet

import gg.dunder.thriveling.core.model.Mood
import gg.dunder.thriveling.core.model.Pet
import gg.dunder.thriveling.core.model.PetActivity

/**
 * Represents the immutable UI state rendered by [PetScreen].
 *
 * ### Kotlin vs C++ Note:
 * - **Unidirectional Data Flow (UDF)**: In modern Android/Compose architecture, the UI does not hold
 *   its own mutable state; instead, it is a pure function of an immutable state object (`UI = f(State)`).
 * - **`sealed interface` as Variant**: Equivalent to `std::variant<Loading, Success>` in C++.
 *   Ensures that invalid intermediate states (such as displaying pet vitals before they have loaded)
 *   are unrepresentable at compile time.
 */
sealed interface PetUiState {

    /** Initial state while asynchronous loading from SQLite database is in flight. */
    data object Loading : PetUiState

    /**
     * Active state when companion data is loaded and ready for user interaction.
     *
     * @property pet Current [Pet] entity with calculated vitals.
     * @property mood Derived emotional state ([Mood]) driving animations and expressions.
     * @property isPettingFeedbackActive When `true`, indicates that petting feedback (spring hop & hearts)
     *                                  is actively playing.
     * @property isPettingRewarded Whether the current pet earns happiness, so the hearts show (DD-61).
     * @property activity Live gait mirroring the user's steps (walking / running alongside them).
     */
    data class Success(
        val pet: Pet,
        val mood: Mood,
        val isPettingFeedbackActive: Boolean = false,
        val isPettingRewarded: Boolean = false,
        val activity: PetActivity = PetActivity.IDLE
    ) : PetUiState
}


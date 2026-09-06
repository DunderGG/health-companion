// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import com.healthcompanion.core.model.Mood
import com.healthcompanion.core.model.Pet

sealed interface PetUiState {
    data object Loading : PetUiState
    data class Success(
        val pet: Pet,
        val mood: Mood,
        val isPettingFeedbackActive: Boolean = false
    ) : PetUiState
}


// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.presentation.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gg.dunder.thriveling.core.domain.usecase.ObservePetDetailsUseCase
import gg.dunder.thriveling.core.domain.usecase.PetDetails
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * State of the pet details screen (DD-53).
 *
 * @param observePetDetailsUseCase The pet and its goal record.
 */
class PetDetailsViewModel(observePetDetailsUseCase: ObservePetDetailsUseCase) : ViewModel() {

    /** The pet and its goal record; `null` until first read. */
    val details: StateFlow<PetDetails?> = observePetDetailsUseCase.execute().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
}

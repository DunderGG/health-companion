// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet

class LogHabitUseCase(
    private val repository: PetRepository
) {
    suspend fun execute(habit: HabitType): Pet {
        return repository.recordHabit(habit)
    }
}


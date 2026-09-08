// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet

/**
 * Use case responsible for processing and logging user health habits.
 *
 * Encapsulates the interaction boundary between UI/Sensors and the underlying repository.
 *
 * @property repository The [PetRepository] responsible for companion persistence and state mutation.
 */
class LogHabitUseCase(
    private val repository: PetRepository
) {

    /**
     * Records a health habit event, applying stat boosts, awarding XP, and checking for evolution.
     *
     * @param habit The health event or interaction to log ([HabitType]).
     * @return The updated [Pet] entity after the habit has been integrated and persisted.
     */
    suspend fun execute(habit: HabitType): Pet {
        return repository.recordHabit(habit)
    }
}


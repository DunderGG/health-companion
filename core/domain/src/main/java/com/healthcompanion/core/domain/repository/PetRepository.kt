// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.repository

import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import kotlinx.coroutines.flow.Flow

/**
 * Clean Architecture repository interface for companion persistence and state streaming.
 */
interface PetRepository {
    fun getPetFlow(): Flow<Pet>
    suspend fun getPet(): Pet
    suspend fun updatePet(pet: Pet)
    suspend fun recordHabit(habit: HabitType): Pet
}


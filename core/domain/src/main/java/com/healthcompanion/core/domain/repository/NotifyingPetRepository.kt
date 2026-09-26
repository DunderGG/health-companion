// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.repository

import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import kotlinx.coroutines.flow.Flow

/**
 * [PetRepository] decorator that invokes [onPetChanged] after every committed mutation.
 *
 * Used to refresh pull-based system surfaces (Tiles, Complications) that cannot observe the
 * database `Flow` themselves. The callback runs only after the delegate returns successfully,
 * i.e. after the transaction has committed.
 *
 * ### Kotlin vs C++ Note:
 * - **`by delegate`** (class delegation): Kotlin generates forwarding methods for the whole interface,
 *   like a C++ wrapper that inherits the interface and forwards every virtual call to a held pointer;
 *   only the overridden methods add behaviour.
 *
 * @property delegate The repository doing the actual work.
 * @property onPetChanged Called after each successful write.
 */
class NotifyingPetRepository(
    private val delegate: PetRepository,
    private val onPetChanged: () -> Unit
) : PetRepository by delegate {

    override fun getPetFlow(): Flow<Pet> = delegate.getPetFlow()

    override suspend fun updatePet(transform: (Pet) -> Pet): Pet =
        delegate.updatePet(transform).also { onPetChanged() }

    override suspend fun recordHabit(habit: HabitType): Pet =
        delegate.recordHabit(habit).also { onPetChanged() }

    override suspend fun recordHabits(habits: List<HabitType>): Pet =
        delegate.recordHabits(habits).also { onPetChanged() }

    override suspend fun startOver(): Pet =
        delegate.startOver().also { onPetChanged() }
}

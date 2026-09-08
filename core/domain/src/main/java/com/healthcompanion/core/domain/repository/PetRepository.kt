// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.repository

import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import kotlinx.coroutines.flow.Flow

/**
 * Clean Architecture repository interface specifying data access and persistence operations for the companion.
 *
 * ### Kotlin vs C++ Note:
 * - **`interface`**: Like an abstract base class containing only pure virtual methods in C++ (`virtual ... = 0`).
 * - **`Flow<T>`**: An asynchronous, cold reactive stream (similar to Rx streams or an async generator).
 *   Consumers subscribe via `.collect { }`. New values are pushed automatically whenever the underlying
 *   database tables change.
 * - **`suspend fun`**: A Kotlin coroutine function. Similar to C++20 functions returning awaitable types
 *   (`co_await`). It can pause execution without blocking the underlying OS worker thread, allowing high
 *   concurrency on resource-constrained smartwatch hardware.
 */
interface PetRepository {

    /**
     * Observes continuous companion updates as a reactive stream.
     * Emits a new [Pet] snapshot whenever the companion is updated in the database.
     *
     * @return A cold [Flow] emitting the latest [Pet] state.
     */
    fun getPetFlow(): Flow<Pet>

    /**
     * Performs a one-shot asynchronous query to retrieve the current companion state.
     *
     * @return Current [Pet] snapshot from persistence (or default seed if none exists).
     */
    suspend fun getPet(): Pet

    /**
     * Asynchronously updates or overwrites the companion record in persistence.
     *
     * @param pet The modified [Pet] entity to save.
     */
    suspend fun updatePet(pet: Pet)

    /**
     * Atomically applies a health habit: computes decay up to current time, applies habit boost,
     * awards XP, checks evolution thresholds, persists changes to the database, and returns the result.
     *
     * @param habit The health event or interaction to process ([HabitType]).
     * @return The updated and evolved [Pet] state immediately after persistence.
     */
    suspend fun recordHabit(habit: HabitType): Pet
}


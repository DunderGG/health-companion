// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.repository

import gg.dunder.thriveling.core.model.HabitEvent
import gg.dunder.thriveling.core.model.HabitType
import gg.dunder.thriveling.core.model.Pet
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
     * Atomically reads the current companion, applies [transform], and persists the result.
     * The read and write happen in one transaction, so concurrent updates are never lost.
     *
     * @param transform Pure function computing the new [Pet] from the current one.
     * @return The persisted [Pet].
     */
    suspend fun updatePet(transform: (Pet) -> Pet): Pet

    /**
     * Atomically applies a health habit: computes decay up to current time, applies habit boost,
     * awards XP, checks evolution thresholds, persists changes to the database, and returns the result.
     * A meal or drink still in its [gg.dunder.thriveling.core.domain.engine.CareCooldown] is ignored (DD-59).
     *
     * @param habit The health event or interaction to process ([HabitType]).
     * @return The updated and evolved [Pet] state immediately after persistence.
     */
    suspend fun recordHabit(habit: HabitType): Pet

    /**
     * Atomically applies several habits in order within a single write, e.g. all habits
     * derived from one passive sensor batch. Meals and drinks still in their cooldown are dropped (DD-59).
     *
     * @param habits The habits to apply, in order.
     * @return The updated and evolved [Pet] state immediately after persistence.
     */
    suspend fun recordHabits(habits: List<HabitType>): Pet

    /**
     * Observes the habit history from [fromMillis] on, oldest first. Emits again after every write that
     * records habits, e.g. to detect a daily focus goal being reached (DD-47).
     *
     * @param fromMillis Epoch milliseconds; older events are not included.
     * @return A cold [Flow] of the matching events.
     */
    fun habitEventsSinceFlow(fromMillis: Long): Flow<List<HabitEvent>>

    /**
     * Replaces the companion with a brand-new default pet and deletes the whole habit history, in one
     * transaction (DD-52). Settings and sensor bookkeeping are not part of this repository and are kept.
     *
     * @return The new pet.
     */
    suspend fun startOver(): Pet
}


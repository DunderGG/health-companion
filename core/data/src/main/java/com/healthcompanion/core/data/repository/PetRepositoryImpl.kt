// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.repository

import androidx.room.withTransaction
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.db.entity.PetEntity
import com.healthcompanion.core.domain.engine.EvolutionEngine
import com.healthcompanion.core.domain.engine.PetDecayEngine
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.Vitals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Concrete implementation of [PetRepository] orchestrating persistence with SQLite via
 * [CompanionDatabase], game mechanics via [PetDecayEngine], and milestone progression via [EvolutionEngine].
 *
 * All mutations run as a single read-modify-write inside a database transaction ([updatePet]),
 * so concurrent writers (UI taps, passive sensor batches, background workers) can never
 * silently overwrite each other's changes.
 *
 * ### Kotlin vs C++ Note:
 * - **Destructuring Declarations**: `val (updatedVitals, xpGained) = PetDecayEngine.applyHabit(...)`
 *   is Kotlin's syntactic equivalent to C++17 structured bindings `auto [updatedVitals, xpGained] = ...`,
 *   unpacking the `Pair` returned by the engine.
 * - **`withTransaction { }`**: Suspends until Room grants the single write transaction, then runs the
 *   block atomically — comparable to holding a `std::scoped_lock` around a `BEGIN IMMEDIATE ... COMMIT`
 *   pair, but without blocking the underlying thread while waiting.
 *
 * @property database Room database providing the DAO and transaction scope.
 * @property clock Source of "now" for decay, habit timestamps and the default pet's birth time.
 */
class PetRepositoryImpl(
    private val database: CompanionDatabase,
    private val clock: Clock
) : PetRepository {

    private val petDao = database.petDao()

    /**
     * Streams continuous companion updates. If the database is currently empty, seeds a default
     * companion ([createDefaultPet]); Room's invalidation then re-emits it as the first value.
     *
     * @return [Flow] emitting [Pet] domain objects.
     */
    override fun getPetFlow(): Flow<Pet> {
        return petDao.getPetFlow()
            .onEach { entity ->
                if (entity == null) {
                    petDao.insertIfAbsent(PetEntity.fromDomain(createDefaultPet()))
                }
            }
            .filterNotNull()
            .map { it.toDomain() }
    }

    /**
     * Asynchronously retrieves the companion snapshot. If no record exists,
     * seeds the database with a default companion [createDefaultPet] and returns it.
     *
     * @return Current or newly created default [Pet].
     */
    override suspend fun getPet(): Pet {
        return database.withTransaction { loadOrSeedPet() }
    }

    /**
     * Atomically reads the current pet, applies [transform], and persists the result
     * inside a single database transaction.
     *
     * @param transform Pure function computing the new pet state from the current one.
     * @return The persisted [Pet].
     */
    override suspend fun updatePet(transform: (Pet) -> Pet): Pet {
        return database.withTransaction {
            val updatedPet = transform(loadOrSeedPet())
            petDao.insertOrUpdate(PetEntity.fromDomain(updatedPet))
            updatedPet
        }
    }

    /**
     * Orchestrates habit logging atomically via [updatePet]:
     * 1. Computes decay and applies habit stat boosts via [PetDecayEngine.applyHabit].
     * 2. Evaluates XP gains and potential evolution via [EvolutionEngine.checkEvolution].
     *
     * @param habit The health habit or interaction event ([HabitType]).
     * @return The updated and evolved [Pet] instance.
     */
    override suspend fun recordHabit(habit: HabitType): Pet {
        return recordHabits(listOf(habit))
    }

    /**
     * Applies each habit in order (decay, boosts, XP, evolution) within one [updatePet] transaction.
     *
     * @param habits The habits to apply, in order.
     * @return The updated and evolved [Pet] instance.
     */
    override suspend fun recordHabits(habits: List<HabitType>): Pet {
        return updatePet { startPet ->
            // Read "now" inside the transaction so it is never older than a concurrently stored timestamp.
            val now = clock.nowMillis()
            habits.fold(startPet) { currentPet, habit ->
                val (updatedVitals, xpGained) = PetDecayEngine.applyHabit(
                    vitals = currentPet.vitals,
                    habit = habit,
                    currentTimeMillis = now
                )
                EvolutionEngine.checkEvolution(
                    pet = currentPet.copy(vitals = updatedVitals),
                    additionalXp = xpGained
                )
            }
        }
    }

    /**
     * Loads the stored pet, or seeds and returns the default pet if none exists.
     * Must be called inside a transaction when the result feeds a subsequent write.
     */
    private suspend fun loadOrSeedPet(): Pet {
        petDao.getPet()?.let { return it.toDomain() }

        val defaultPet = createDefaultPet()
        petDao.insertIfAbsent(PetEntity.fromDomain(defaultPet))
        return defaultPet
    }

    /**
     * Creates a default starting companion ("Aura" in [com.healthcompanion.core.model.EvolutionStage.HATCHLING]).
     *
     * @return Fresh starting [Pet] domain instance.
     */
    private fun createDefaultPet(): Pet {
        val now = clock.nowMillis()
        return Pet(
            id = "companion_primary",
            name = "Aura",
            vitals = Vitals(lastUpdatedTimestamp = now),
            bornTimestamp = now
        )
    }
}


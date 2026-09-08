// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.repository

import com.healthcompanion.core.data.db.dao.PetDao
import com.healthcompanion.core.data.db.entity.PetEntity
import com.healthcompanion.core.domain.engine.EvolutionEngine
import com.healthcompanion.core.domain.engine.PetDecayEngine
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Concrete implementation of [PetRepository] orchestrating persistence with SQLite via [PetDao],
 * game mechanics via [PetDecayEngine], and milestone progression via [EvolutionEngine].
 *
 * ### Kotlin vs C++ Note:
 * - **Destructuring Declarations**: `val (updatedVitals, xpGained) = PetDecayEngine.applyHabit(...)`
 *   is Kotlin's syntactic equivalent to C++17 structured bindings `auto [updatedVitals, xpGained] = ...`,
 *   unpacking the `Pair` returned by the engine.
 * - **Safe Calls & Elvis Operator**: `entity?.toDomain() ?: fallback` combines conditional member
 *   access with null coalescing, ensuring safe defaults without verbose `if (ptr != nullptr)` checks.
 *
 * @property petDao Room Data Access Object for database queries and writes.
 */
class PetRepositoryImpl(
    private val petDao: PetDao
) : PetRepository {

    /**
     * Streams continuous companion updates. If the database is currently empty,
     * seeds the database with a default companion [createDefaultPet] and emits it.
     *
     * @return [Flow] emitting [Pet] domain objects.
     */
    override fun getPetFlow(): Flow<Pet> {
        return petDao.getPetFlow().map { entity ->
            entity?.toDomain() ?: createDefaultPet().also { defaultPet ->
                petDao.insertOrUpdate(PetEntity.fromDomain(defaultPet))
            }
        }
    }

    /**
     * Asynchronously retrieves the companion snapshot. If no record exists,
     * seeds the database with a default companion [createDefaultPet] and returns it.
     *
     * @return Current or newly created default [Pet].
     */
    override suspend fun getPet(): Pet {
        val entity = petDao.getPet()
        return if (entity != null) {
            entity.toDomain()
        } else {
            val defaultPet = createDefaultPet()
            petDao.insertOrUpdate(PetEntity.fromDomain(defaultPet))
            defaultPet
        }
    }

    /**
     * Persists an updated companion domain object to the SQLite database.
     *
     * @param pet Domain [Pet] instance to persist.
     */
    override suspend fun updatePet(pet: Pet) {
        petDao.insertOrUpdate(PetEntity.fromDomain(pet))
    }

    /**
     * Orchestrates habit logging:
     * 1. Fetches current pet state.
     * 2. Computes decay and applies habit stat boosts via [PetDecayEngine.applyHabit].
     * 3. Evaluates XP gains and potential evolution via [EvolutionEngine.checkEvolution].
     * 4. Saves the resulting pet to SQLite.
     *
     * @param habit The health habit or interaction event ([HabitType]).
     * @return The updated and evolved [Pet] instance.
     */
    override suspend fun recordHabit(habit: HabitType): Pet {
        val currentPet = getPet()
        val (updatedVitals, xpGained) = PetDecayEngine.applyHabit(
            vitals = currentPet.vitals,
            habit = habit
        )
        val evolvedPet = EvolutionEngine.checkEvolution(
            pet = currentPet.copy(vitals = updatedVitals),
            additionalXp = xpGained
        )
        petDao.insertOrUpdate(PetEntity.fromDomain(evolvedPet))
        return evolvedPet
    }

    /**
     * Creates a default starting companion ("Aura" in [com.healthcompanion.core.model.EvolutionStage.HATCHLING]).
     *
     * @return Fresh starting [Pet] domain instance.
     */
    private fun createDefaultPet(): Pet {
        return Pet(
            id = "companion_primary",
            name = "Aura"
        )
    }
}


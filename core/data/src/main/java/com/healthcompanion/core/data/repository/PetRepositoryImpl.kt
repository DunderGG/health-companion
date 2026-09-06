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

class PetRepositoryImpl(
    private val petDao: PetDao
) : PetRepository {

    override fun getPetFlow(): Flow<Pet> {
        return petDao.getPetFlow().map { entity ->
            entity?.toDomain() ?: createDefaultPet().also { defaultPet ->
                petDao.insertOrUpdate(PetEntity.fromDomain(defaultPet))
            }
        }
    }

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

    override suspend fun updatePet(pet: Pet) {
        petDao.insertOrUpdate(PetEntity.fromDomain(pet))
    }

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

    private fun createDefaultPet(): Pet {
        return Pet(
            id = "companion_primary",
            name = "Aura"
        )
    }
}


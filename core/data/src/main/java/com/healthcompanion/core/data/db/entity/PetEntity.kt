// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.healthcompanion.core.model.EvolutionStage
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.PetArchetype
import com.healthcompanion.core.model.Vitals

/**
 * Room database entity persisting companion state.
 */
@Entity(tableName = "pets")
data class PetEntity(
    @PrimaryKey val id: String = "companion_primary",
    val name: String,
    val stage: String,
    val archetype: String,
    val energy: Float,
    val hunger: Float,
    val hydration: Float,
    val fitness: Float,
    val happiness: Float,
    val lastUpdatedTimestamp: Long,
    val experiencePoints: Int,
    val bornTimestamp: Long
) {
    fun toDomain(): Pet {
        return Pet(
            id = id,
            name = name,
            stage = EvolutionStage.valueOf(stage),
            archetype = PetArchetype.valueOf(archetype),
            vitals = Vitals(
                energy = energy,
                hunger = hunger,
                hydration = hydration,
                fitness = fitness,
                happiness = happiness,
                lastUpdatedTimestamp = lastUpdatedTimestamp
            ),
            experiencePoints = experiencePoints,
            bornTimestamp = bornTimestamp
        )
    }

    companion object {
        fun fromDomain(pet: Pet): PetEntity {
            return PetEntity(
                id = pet.id,
                name = pet.name,
                stage = pet.stage.name,
                archetype = pet.archetype.name,
                energy = pet.vitals.energy,
                hunger = pet.vitals.hunger,
                hydration = pet.vitals.hydration,
                fitness = pet.vitals.fitness,
                happiness = pet.vitals.happiness,
                lastUpdatedTimestamp = pet.vitals.lastUpdatedTimestamp,
                experiencePoints = pet.experiencePoints,
                bornTimestamp = pet.bornTimestamp
            )
        }
    }
}


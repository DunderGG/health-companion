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
 * Room database entity representing a persistent table row for the companion.
 *
 * ### Kotlin vs C++ Note:
 * - **Room ORM**: Room is an SQLite Object-Relational Mapping library for Android.
 *   `@Entity(tableName = "pets")` generates the underlying SQLite table `CREATE TABLE pets (...)`.
 * - **Flattening / Data Mapping**: Domain models like [Pet] contain nested value objects ([Vitals])
 *   and enums. In relational storage, these are flattened into primitive column types (TEXT, REAL, INTEGER).
 * - **Serialization**: `toDomain()` and `fromDomain()` act as conversion operators / serializers
 *   between the database representation and the rich domain model.
 *
 * @property id Primary key column identifying the companion (defaults to `"companion_primary"`).
 * @property name User-facing display name stored as text.
 * @property stage Stored string representation of [EvolutionStage] enum constant.
 * @property archetype Stored string representation of [PetArchetype] enum constant.
 * @property energy Companion energy stat in range `[0, 100]`.
 * @property hunger Companion hunger stat in range `[0, 100]`.
 * @property hydration Companion hydration stat in range `[0, 100]`.
 * @property fitness Companion fitness stat in range `[0, 100]`.
 * @property happiness Companion happiness stat in range `[0, 100]`.
 * @property lastUpdatedTimestamp Epoch timestamp in milliseconds of last state update.
 * @property experiencePoints Total experience points accumulated.
 * @property bornTimestamp Epoch timestamp in milliseconds of pet creation.
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

    /**
     * Converts this flat database entity into the rich, type-safe domain [Pet] entity.
     *
     * @return Fully populated [Pet] instance with nested [Vitals] and validated invariants.
     */
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
        /**
         * Creates a flat [PetEntity] from a domain [Pet] instance ready for SQLite insertion.
         *
         * @param pet Domain [Pet] instance to flatten.
         * @return [PetEntity] table row object.
         */
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


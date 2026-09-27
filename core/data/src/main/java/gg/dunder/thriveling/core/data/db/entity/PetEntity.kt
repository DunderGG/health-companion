// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import gg.dunder.thriveling.core.model.EvolutionStage
import gg.dunder.thriveling.core.model.Pet
import gg.dunder.thriveling.core.model.PetArchetype
import gg.dunder.thriveling.core.model.Vitals
import gg.dunder.thriveling.core.model.toVitalRange

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
     * Tolerant of bad rows: a stored row must never crash every load of the pet. Out-of-range or
     * `NaN` vitals are clamped, negative XP becomes 0, an unknown stage name is re-derived from XP,
     * and an unknown archetype name falls back to [PetArchetype.BALANCED].
     *
     * @return Fully populated [Pet] instance with nested [Vitals] and validated invariants.
     */
    fun toDomain(): Pet {
        val safeXp = experiencePoints.coerceAtLeast(0)
        return Pet(
            id = id,
            name = name,
            stage = EvolutionStage.entries.find { it.name == stage } ?: EvolutionStage.fromXp(safeXp),
            archetype = PetArchetype.entries.find { it.name == archetype } ?: PetArchetype.BALANCED,
            vitals = Vitals(
                energy = energy.toVitalRange(),
                hunger = hunger.toVitalRange(),
                hydration = hydration.toVitalRange(),
                fitness = fitness.toVitalRange(),
                happiness = happiness.toVitalRange(),
                lastUpdatedTimestamp = lastUpdatedTimestamp
            ),
            experiencePoints = safeXp,
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


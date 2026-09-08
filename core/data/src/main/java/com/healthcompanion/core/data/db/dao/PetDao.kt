// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.healthcompanion.core.data.db.entity.PetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) defining SQLite CRUD operations for companion records.
 *
 * ### Kotlin vs C++ Note:
 * - **Code Generation**: In Kotlin/Android, the developer declares an `interface` with annotations
 *   like `@Query` and `@Insert`. The compiler's Kotlin Symbol Processing (KSP) generates the C++-style
 *   concrete implementation class with prepared statements, parameter binding, and cursor deserialization.
 * - **Reactive Invalidation**: `getPetFlow(...)` returns a `Flow` that registers an SQLite table observer.
 *   Whenever any database write modifies the `pets` table, Room automatically re-runs the query and
 *   emits the fresh result to subscribers.
 * - **Nullable Types (`PetEntity?`)**: The `?` suffix indicates that the result can be null (analogous
 *   to `std::optional<PetEntity>` in C++).
 */
@Dao
interface PetDao {

    /**
     * Observes the companion record as a reactive stream.
     * Re-emits whenever the `pets` table is modified.
     *
     * @param petId Target pet primary key (default: `"companion_primary"`).
     * @return A cold [Flow] emitting the current [PetEntity], or `null` if the table is empty.
     */
    @Query("SELECT * FROM pets WHERE id = :petId LIMIT 1")
    fun getPetFlow(petId: String = "companion_primary"): Flow<PetEntity?>

    /**
     * Performs a one-shot asynchronous query to retrieve the companion record.
     *
     * @param petId Target pet primary key (default: `"companion_primary"`).
     * @return The [PetEntity] table row, or `null` if no matching record exists.
     */
    @Query("SELECT * FROM pets WHERE id = :petId LIMIT 1")
    suspend fun getPet(petId: String = "companion_primary"): PetEntity?

    /**
     * Inserts or replaces (UPSERT) a companion record in the database.
     *
     * @param pet The entity to persist.
     * @return The SQLite `rowid` of the inserted/updated record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(pet: PetEntity): Long

    /**
     * Updates an existing companion record matching the entity's primary key.
     *
     * @param pet The entity with updated fields.
     * @return Number of rows updated (1 if found, 0 otherwise).
     */
    @Update
    suspend fun update(pet: PetEntity): Int
}


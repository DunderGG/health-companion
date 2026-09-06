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

@Dao
interface PetDao {
    @Query("SELECT * FROM pets WHERE id = :petId LIMIT 1")
    fun getPetFlow(petId: String = "companion_primary"): Flow<PetEntity?>

    @Query("SELECT * FROM pets WHERE id = :petId LIMIT 1")
    suspend fun getPet(petId: String = "companion_primary"): PetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(pet: PetEntity): Long

    @Update
    suspend fun update(pet: PetEntity): Int
}


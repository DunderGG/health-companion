// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.healthcompanion.core.data.db.entity.HabitEventEntity

/**
 * Data access for the `habit_events` history table.
 */
@Dao
interface HabitEventDao {

    /** Appends applied habits to the history. */
    @Insert
    suspend fun insertAll(events: List<HabitEventEntity>)

    /** Events at or after [fromMillis], oldest first. */
    @Query("SELECT * FROM habit_events WHERE timestampMillis >= :fromMillis ORDER BY timestampMillis")
    suspend fun eventsSince(fromMillis: Long): List<HabitEventEntity>

    /** Deletes events older than [cutoffMillis] to keep the table small on the watch. */
    @Query("DELETE FROM habit_events WHERE timestampMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}

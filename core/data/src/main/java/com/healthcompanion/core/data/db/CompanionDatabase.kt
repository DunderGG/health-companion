// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.healthcompanion.core.data.db.dao.PetDao
import com.healthcompanion.core.data.db.entity.PetEntity

@Database(
    entities = [PetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CompanionDatabase : RoomDatabase() {
    abstract fun petDao(): PetDao

    companion object {
        @Volatile
        private var INSTANCE: CompanionDatabase? = null

        fun getInstance(context: Context): CompanionDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CompanionDatabase::class.java,
                    "health_companion.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}


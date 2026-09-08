// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.healthcompanion.core.data.db.dao.PetDao
import com.healthcompanion.core.data.db.entity.PetEntity

/**
 * Room Database definition and thread-safe singleton provider for the companion SQLite database.
 *
 * ### Kotlin vs C++ Note:
 * - **`abstract class`**: Like a C++ base class with pure virtual functions. Room generates the concrete
 *   subclass (`CompanionDatabase_Impl`) at compile time.
 * - **`@Volatile`**: Maps to atomic memory fences / `std::atomic` in C++. Guarantees that writes to
 *   [INSTANCE] are immediately visible to other threads across CPU cache lines.
 * - **Double-Checked Locking**: The `INSTANCE ?: synchronized(this) { INSTANCE ?: ... }` block is the
 *   classic double-checked locking idiom, identical to thread-safe lazy singleton instantiation in C++
 *   before `std::call_once` or C++11 magic statics.
 * - **Scope Function `.also { ... }`**: A Kotlin standard library extension function that passes the
 *   receiver as `it` into the lambda, executes side-effects (here, assigning to [INSTANCE]), and returns
 *   the original receiver object.
 */
@Database(
    entities = [PetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CompanionDatabase : RoomDatabase() {

    /**
     * Abstract factory method returning the DAO for companion records.
     * Room generates the concrete implementation.
     */
    abstract fun petDao(): PetDao

    companion object {
        @Volatile
        private var INSTANCE: CompanionDatabase? = null

        /**
         * Returns the thread-safe singleton instance of [CompanionDatabase].
         * Initializes and builds the Room database on first invocation.
         *
         * @param context Android application or component context (uses applicationContext to prevent memory leaks).
         * @return The active [CompanionDatabase] instance.
         */
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


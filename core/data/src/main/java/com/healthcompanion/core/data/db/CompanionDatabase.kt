// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.db

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.healthcompanion.core.data.db.dao.HabitEventDao
import com.healthcompanion.core.data.db.dao.PetDao
import com.healthcompanion.core.data.db.entity.HabitEventEntity
import com.healthcompanion.core.data.db.entity.PetEntity
import com.healthcompanion.core.data.db.migrations.ALL_MIGRATIONS

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
    entities = [PetEntity::class, HabitEventEntity::class],
    version = CompanionDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // v2: adds the habit_events table (AR-3).
        AutoMigration(from = 1, to = 2)
    ]
)
abstract class CompanionDatabase : RoomDatabase() {

    /**
     * Abstract factory method returning the DAO for companion records.
     * Room generates the concrete implementation.
     */
    abstract fun petDao(): PetDao

    /** DAO for the habit history (schema v2+). */
    abstract fun habitEventDao(): HabitEventDao

    companion object {
        @Volatile
        private var INSTANCE: CompanionDatabase? = null

        /**
         * Returns the thread-safe singleton instance of [CompanionDatabase].
         * Initializes and builds the Room database on first invocation.
         *
         * Upgrades always go through [ALL_MIGRATIONS]; there is no destructive fallback, so a missing
         * migration fails loudly instead of deleting the pet. Debuggable builds additionally allow a
         * destructive *downgrade* (e.g. installing an older branch build over a newer one).
         *
         * @param context Android application or component context (uses applicationContext to prevent memory leaks).
         * @return The active [CompanionDatabase] instance.
         */
        fun getInstance(context: Context): CompanionDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): CompanionDatabase {
            val builder = Room.databaseBuilder(appContext, CompanionDatabase::class.java, DATABASE_NAME)
                .addMigrations(*ALL_MIGRATIONS)

            val isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                builder.fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            }
            return builder.build()
        }

        private const val DATABASE_NAME = "companion.db"

        /** Current schema version. Bump together with a migration in [ALL_MIGRATIONS]. */
        const val VERSION = 2
    }
}


// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.healthcompanion.core.data.db.migrations.ALL_MIGRATIONS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the on-disk schema so a user's pet survives app updates.
 *
 * Uses the schema JSON exported to `core/data/schemas/` and verifies that a database created from it
 * opens with the current entities and migrations, with its data intact. Fails when a migration in
 * [ALL_MIGRATIONS] is missing or produces a schema that differs from the entities.
 *
 * Note: KSP re-exports the *current* version's JSON on every build, so an entity change without a
 * version bump silently rewrites that file. That case is caught by CI, which fails if
 * `core/data/schemas/` differs from the committed files.
 *
 * When adding version N, add a test that creates the database at N-1 with representative data,
 * runs `helper.runMigrationsAndValidate(DB_NAME, N, true, *ALL_MIGRATIONS)`, and checks the data survived.
 */
@RunWith(RobolectricTestRunner::class)
class CompanionDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CompanionDatabase::class.java
    )

    @Test
    fun `current exported schema matches the entities and preserves the pet`() = runBlocking {
        helper.createDatabase(DB_NAME, CompanionDatabase.VERSION).apply {
            execSQL(
                """
                INSERT INTO pets (id, name, stage, archetype, energy, hunger, hydration, fitness,
                                  happiness, lastUpdatedTimestamp, experiencePoints, bornTimestamp)
                VALUES ('companion_primary', 'Kairo', 'TEEN', 'CARDIO_RUNNER', 50, 60, 70, 80, 90,
                        1000, 800, 500)
                """.trimIndent()
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, CompanionDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

        try {
            val pet = db.petDao().getPet()!!.toDomain()
            assertEquals("Kairo", pet.name)
            assertEquals(800, pet.experiencePoints)
            assertEquals(80f, pet.vitals.fitness, 0f)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}

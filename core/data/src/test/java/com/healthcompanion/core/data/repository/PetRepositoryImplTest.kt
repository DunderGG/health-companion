// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.HabitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for [PetRepositoryImpl] against an in-memory Room database.
 *
 * Runs on the JVM via Robolectric so it executes as part of `./gradlew test` (and CI)
 * without requiring an emulator or physical watch.
 */
@RunWith(RobolectricTestRunner::class)
class PetRepositoryImplTest {

    private lateinit var db: CompanionDatabase
    private lateinit var repository: PetRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CompanionDatabase::class.java).build()
        repository = PetRepositoryImpl(db, Clock.SYSTEM)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `concurrent recordHabit calls lose no updates`() = runBlocking {
        val startXp = repository.getPet().experiencePoints
        val writers = 50

        // Each hydration log awards a fixed 15 XP. XP is unbounded, so any lost
        // read-modify-write shows up as a shortfall in the final total.
        (1..writers).map {
            async(Dispatchers.Default) { repository.recordHabit(HabitType.Hydration(250)) }
        }.awaitAll()

        assertEquals(startXp + writers * 15, repository.getPet().experiencePoints)
    }

    @Test
    fun `concurrent updatePet transforms lose no updates`() = runBlocking {
        val startXp = repository.getPet().experiencePoints
        val writers = 50

        (1..writers).map {
            async(Dispatchers.Default) {
                repository.updatePet { it.copy(experiencePoints = it.experiencePoints + 1) }
            }
        }.awaitAll()

        assertEquals(startXp + writers, repository.getPet().experiencePoints)
    }

    @Test
    fun `seeding the default pet never overwrites an existing pet`() = runBlocking {
        val logged = repository.recordHabit(HabitType.Hydration(250))

        // Observing the flow must return the stored pet, not re-seed a fresh default.
        val observed = repository.getPetFlow().first()

        assertEquals(logged.experiencePoints, observed.experiencePoints)
        assertEquals(logged.vitals, observed.vitals)
    }

    @Test
    fun `seeded pet and habit timestamps come from the injected clock`() = runBlocking {
        var nowMillis = 5_000_000L
        val clockedRepository = PetRepositoryImpl(db, Clock { nowMillis })

        val seeded = clockedRepository.getPet()
        assertEquals(5_000_000L, seeded.bornTimestamp)
        assertEquals(5_000_000L, seeded.vitals.lastUpdatedTimestamp)

        nowMillis = 5_060_000L
        val logged = clockedRepository.recordHabit(HabitType.Hydration(250))
        assertEquals(5_060_000L, logged.vitals.lastUpdatedTimestamp)
    }

    @Test
    fun `observing an empty database seeds and emits the default pet`() = runBlocking {
        val observed = repository.getPetFlow().first()

        assertEquals("companion_primary", observed.id)
        assertEquals(observed, repository.getPet())
    }
}

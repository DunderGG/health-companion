// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.db.entity.HabitEventEntity
import com.healthcompanion.core.domain.engine.NightWindow
import com.healthcompanion.core.domain.repository.InMemorySettingsRepository
import com.healthcompanion.core.domain.settings.UserSettings
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.EvolutionStage
import com.healthcompanion.core.model.HabitEvent
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.PetArchetype
import com.healthcompanion.core.model.Vitals
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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
        repository = PetRepositoryImpl(db, Clock.SYSTEM, InMemorySettingsRepository())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val day = 24 * 60 * 60 * 1000L

    private fun utcClock(now: () -> Long) = object : Clock {
        override fun nowMillis(): Long = now()
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    @Test
    fun `the habit history flow includes newly recorded habits from the given time on`() = runBlocking {
        val now = Instant.parse("2026-01-10T18:00:00Z").toEpochMilli()
        val clockedRepository = PetRepositoryImpl(db, utcClock { now }, InMemorySettingsRepository())
        db.habitEventDao().insertAll(listOf(HabitEventEntity.fromDomain(HabitType.Steps(500), now - day)))

        clockedRepository.recordHabit(HabitType.Hydration(250))

        assertEquals(
            listOf(HabitEvent(HabitType.Hydration(250), now)),
            clockedRepository.habitEventsSinceFlow(now - 60_000).first()
        )
    }

    @Test
    fun `reaching teen locks in the archetype from recent habit history`() = runBlocking {
        val now = Instant.parse("2026-01-10T18:00:00Z").toEpochMilli()
        val clockedRepository = PetRepositoryImpl(db, utcClock { now }, InMemorySettingsRepository())

        // Five consistent 7,000-step days in the past week.
        db.habitEventDao().insertAll(
            (0 until 5).map { back -> HabitEventEntity.fromDomain(HabitType.Steps(7_000), now - back * day) }
        )
        clockedRepository.updatePet { it.copy(stage = EvolutionStage.CHILD, experiencePoints = 740) }

        val evolved = clockedRepository.recordHabit(HabitType.Hydration(250)) // +15 XP -> 755 (TEEN)

        assertEquals(EvolutionStage.TEEN, evolved.stage)
        assertEquals(PetArchetype.CARDIO_RUNNER, evolved.archetype)
        assertEquals(PetArchetype.CARDIO_RUNNER, clockedRepository.getPet().archetype)
    }

    @Test
    fun `recorded habits are stored in the history and old history is pruned`() = runBlocking {
        val now = Instant.parse("2026-03-01T12:00:00Z").toEpochMilli()
        val clockedRepository = PetRepositoryImpl(db, utcClock { now }, InMemorySettingsRepository())
        db.habitEventDao().insertAll(listOf(HabitEventEntity.fromDomain(HabitType.Steps(500), now - 31 * day)))

        clockedRepository.recordHabits(listOf(HabitType.Hydration(250), HabitType.Meal(isHealthy = true)))

        val history = db.habitEventDao().eventsSince(0L).mapNotNull { it.toDomain() }
        assertEquals(
            listOf(HabitEvent(HabitType.Hydration(250), now), HabitEvent(HabitType.Meal(isHealthy = true), now)),
            history
        )
    }

    @Test
    fun `decay on write sleeps through the user's bedtime`() = runBlocking {
        val evening = Instant.parse("2026-03-01T18:00:00Z").toEpochMilli()
        var now = evening
        val settings = InMemorySettingsRepository(UserSettings(bedtime = NightWindow(startHour = 18, endHour = 6)))
        val clockedRepository = PetRepositoryImpl(db, utcClock { now }, settings)
        clockedRepository.updatePet { it.copy(vitals = Vitals(energy = 50f, lastUpdatedTimestamp = evening)) }

        now = evening + 2 * 60 * 60 * 1000L
        val pet = clockedRepository.recordHabit(HabitType.Hydration(250))

        // Two hours asleep recover energy (+8/h); with the default 22:00 bedtime it would have drained to 46.
        assertEquals(66f, pet.vitals.energy, 0.01f)
    }

    @Test
    fun `starting over replaces the pet with a fresh one and deletes the habit history`() = runBlocking {
        var now = Instant.parse("2026-03-01T12:00:00Z").toEpochMilli()
        val clockedRepository = PetRepositoryImpl(db, utcClock { now }, InMemorySettingsRepository())
        clockedRepository.updatePet {
            it.copy(name = "Mochi", stage = EvolutionStage.TEEN, experiencePoints = 900, archetype = PetArchetype.ZEN_SAGE)
        }
        clockedRepository.recordHabits(listOf(HabitType.Steps(7_000), HabitType.Hydration(250)))

        now += day
        val fresh = clockedRepository.startOver()

        assertEquals(fresh, clockedRepository.getPet())
        // A default pet, born now, with full vitals.
        assertEquals(Pet(vitals = Vitals(lastUpdatedTimestamp = now), bornTimestamp = now), fresh)
        assertEquals(emptyList<HabitEvent>(), db.habitEventDao().eventsSince(0L).mapNotNull { it.toDomain() })
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
        val clockedRepository = PetRepositoryImpl(db, Clock { nowMillis }, InMemorySettingsRepository())

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

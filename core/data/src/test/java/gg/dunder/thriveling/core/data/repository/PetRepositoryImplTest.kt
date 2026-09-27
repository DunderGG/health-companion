// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import gg.dunder.thriveling.core.data.db.CompanionDatabase
import gg.dunder.thriveling.core.data.db.entity.HabitEventEntity
import gg.dunder.thriveling.core.domain.engine.CareCooldown
import gg.dunder.thriveling.core.domain.engine.NightWindow
import gg.dunder.thriveling.core.domain.repository.InMemorySettingsRepository
import gg.dunder.thriveling.core.domain.settings.UserSettings
import gg.dunder.thriveling.core.domain.time.Clock
import gg.dunder.thriveling.core.model.EvolutionStage
import gg.dunder.thriveling.core.model.HabitEvent
import gg.dunder.thriveling.core.model.HabitType
import gg.dunder.thriveling.core.model.Pet
import gg.dunder.thriveling.core.model.PetArchetype
import gg.dunder.thriveling.core.model.Vitals
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
        // A default pet, born now, with half-full vitals.
        assertEquals(Pet(vitals = Vitals.starting(now), bornTimestamp = now), fresh)
        assertEquals(emptyList<HabitEvent>(), db.habitEventDao().eventsSince(0L).mapNotNull { it.toDomain() })
    }

    @Test
    fun `concurrent recordHabit calls lose no updates`() = runBlocking {
        val startXp = repository.getPet().experiencePoints
        val writers = 50

        // Each heart-rate reading awards a fixed 5 XP (and, unlike care by hand, has no cooldown). XP is
        // unbounded, so any lost read-modify-write shows up as a shortfall in the final total.
        (1..writers).map {
            async(Dispatchers.Default) { repository.recordHabit(HabitType.HeartRate(bpm = 70f)) }
        }.awaitAll()

        assertEquals(startXp + writers * 5, repository.getPet().experiencePoints)
    }

    @Test
    fun `concurrent drinks count only once within the cooldown`() = runBlocking {
        val startXp = repository.getPet().experiencePoints

        (1..10).map {
            async(Dispatchers.Default) { repository.recordHabit(HabitType.Hydration(250)) }
        }.awaitAll()

        assertEquals(startXp + 15, repository.getPet().experiencePoints)
        assertEquals(1, db.habitEventDao().eventsSince(0L).size)
    }

    @Test
    fun `petting rewards the pet once an hour`() = runBlocking {
        val start = Instant.parse("2026-03-01T12:00:00Z").toEpochMilli()
        var now = start
        val clockedRepository = PetRepositoryImpl(db, utcClock { now }, InMemorySettingsRepository())
        val petted = clockedRepository.recordHabit(HabitType.PettingInteraction(1.0f))

        now = start + 10 * 60_000L
        assertEquals(petted, clockedRepository.recordHabit(HabitType.PettingInteraction(1.0f)))

        now = start + CareCooldown.COOLDOWN_MS
        val again = clockedRepository.recordHabit(HabitType.PettingInteraction(1.0f))
        assertEquals(petted.experiencePoints + 5, again.experiencePoints)
    }

    @Test
    fun `meals and drinks are ignored for an hour after the last one, each on its own cooldown`() = runBlocking {
        val start = Instant.parse("2026-03-01T12:00:00Z").toEpochMilli()
        var now = start
        val clockedRepository = PetRepositoryImpl(db, utcClock { now }, InMemorySettingsRepository())
        val drunk = clockedRepository.recordHabit(HabitType.Hydration(250))

        now = start + 30 * 60_000L
        // A second drink, and a snack in the same batch as a meal, are dropped; the meal is not.
        assertEquals(drunk, clockedRepository.recordHabit(HabitType.Hydration(250)))
        clockedRepository.recordHabits(listOf(HabitType.Meal(isHealthy = true), HabitType.Meal(isHealthy = false)))

        now = start + CareCooldown.COOLDOWN_MS
        clockedRepository.recordHabit(HabitType.Hydration(250))

        assertEquals(
            listOf(
                HabitEvent(HabitType.Hydration(250), start),
                HabitEvent(HabitType.Meal(isHealthy = true), start + 30 * 60_000L),
                HabitEvent(HabitType.Hydration(250), start + CareCooldown.COOLDOWN_MS)
            ),
            db.habitEventDao().eventsSince(0L).mapNotNull { it.toDomain() }
        )
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
    fun `the seeded pet starts with half-full vitals`() = runBlocking {
        val clockedRepository = PetRepositoryImpl(db, Clock { 5_000_000L }, InMemorySettingsRepository())

        assertEquals(Vitals.starting(5_000_000L), clockedRepository.getPet().vitals)
        assertEquals(50f, Vitals.STARTING_LEVEL, 0f)
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

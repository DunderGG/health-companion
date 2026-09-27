// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.usecase

import gg.dunder.thriveling.core.domain.engine.NightWindow
import gg.dunder.thriveling.core.domain.repository.InMemorySettingsRepository
import gg.dunder.thriveling.core.domain.repository.PetRepository
import gg.dunder.thriveling.core.domain.settings.UserSettings
import gg.dunder.thriveling.core.domain.time.Clock
import gg.dunder.thriveling.core.model.HabitEvent
import gg.dunder.thriveling.core.model.HabitType
import gg.dunder.thriveling.core.model.Mood
import gg.dunder.thriveling.core.model.Pet
import gg.dunder.thriveling.core.model.Vitals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class GetPetStateUseCaseTest {

    private val start = 1_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun `open screen keeps decaying on each tick without any database write`() = runTest {
        val repository = FakePetRepository(Pet(vitals = Vitals(hydration = 50f, lastUpdatedTimestamp = start)))
        val clock = utcClock { start + testScheduler.currentTime }
        val useCase = GetPetStateUseCase(repository, InMemorySettingsRepository(), clock, refreshIntervalMillis = hour)

        val emissions = mutableListOf<PetWithMood>()
        backgroundScope.launch { useCase.execute().collect { emissions += it } }
        runCurrent()

        advanceTimeBy(hour)
        runCurrent()

        assertEquals(2, emissions.size)
        assertEquals(50f, emissions[0].pet.vitals.hydration, 0.01f)
        // 1 hour of decay at 3.0/hr, with no write to the repository.
        assertEquals(47f, emissions[1].pet.vitals.hydration, 0.01f)
    }

    @Test
    fun `stored pet changes are emitted immediately`() = runTest {
        val repository = FakePetRepository(Pet(vitals = Vitals(hydration = 50f, lastUpdatedTimestamp = start)))
        val useCase = GetPetStateUseCase(repository, InMemorySettingsRepository(), utcClock { start }, refreshIntervalMillis = hour)

        val emissions = mutableListOf<PetWithMood>()
        backgroundScope.launch { useCase.execute().collect { emissions += it } }
        runCurrent()

        repository.pet.value = Pet(vitals = Vitals(hydration = 90f, lastUpdatedTimestamp = start))
        runCurrent()

        assertTrue(emissions.size >= 2)
        assertEquals(90f, emissions.last().pet.vitals.hydration, 0.01f)
    }

    @Test
    fun `pet is shown sleeping during its night window`() = runTest {
        val lateEvening = java.time.Instant.parse("2026-01-10T23:00:00Z").toEpochMilli()
        val repository = FakePetRepository(Pet(vitals = Vitals(energy = 90f, lastUpdatedTimestamp = lateEvening)))
        val useCase =
            GetPetStateUseCase(repository, InMemorySettingsRepository(), utcClock { lateEvening }, refreshIntervalMillis = hour)

        val emissions = mutableListOf<PetWithMood>()
        backgroundScope.launch { useCase.execute().collect { emissions += it } }
        runCurrent()

        assertEquals(gg.dunder.thriveling.core.model.Mood.SLEEPING, emissions.single().mood)
    }

    @Test
    fun `a refresh signal re-evaluates decay between ticks`() = runTest {
        var now = start
        val repository = FakePetRepository(Pet(vitals = Vitals(hydration = 50f, lastUpdatedTimestamp = start)))
        val useCase = GetPetStateUseCase(repository, InMemorySettingsRepository(), utcClock { now }, refreshIntervalMillis = 24 * hour)
        val refresh = MutableSharedFlow<Unit>()

        val emissions = mutableListOf<PetWithMood>()
        backgroundScope.launch { useCase.execute(refresh).collect { emissions += it } }
        runCurrent()

        // Wall time jumps while the ticker's delay doesn't advance, as when the CPU sleeps in ambient mode.
        now = start + hour
        refresh.emit(Unit)
        runCurrent()

        assertEquals(2, emissions.size)
        assertEquals(47f, emissions.last().pet.vitals.hydration, 0.01f)
    }

    @Test
    fun `current snapshot applies decay up to now`() = runTest {
        val repository = FakePetRepository(Pet(vitals = Vitals(hydration = 50f, lastUpdatedTimestamp = start)))
        val useCase = GetPetStateUseCase(repository, InMemorySettingsRepository(), utcClock { start + 2 * hour })

        // 2 hours of decay at 3.0/hr.
        assertEquals(44f, useCase.current().pet.vitals.hydration, 0.01f)
    }

    @Test
    fun `the user's bedtime decides when the pet sleeps, and a new bedtime applies at once`() = runTest {
        val evening = java.time.Instant.parse("2026-01-10T20:30:00Z").toEpochMilli()
        val repository = FakePetRepository(Pet(vitals = Vitals(energy = 90f, lastUpdatedTimestamp = evening)))
        val settings = InMemorySettingsRepository(UserSettings(bedtime = NightWindow(startHour = 20, endHour = 6)))
        val useCase = GetPetStateUseCase(repository, settings, utcClock { evening }, refreshIntervalMillis = hour)

        val emissions = mutableListOf<PetWithMood>()
        backgroundScope.launch { useCase.execute().collect { emissions += it } }
        runCurrent()
        assertEquals(Mood.SLEEPING, emissions.last().mood)
        assertEquals(Mood.SLEEPING, useCase.current().mood)

        settings.updateSettings { it.copy(bedtime = NightWindow.DEFAULT) }
        runCurrent()
        assertTrue(emissions.last().mood != Mood.SLEEPING)
    }

    /** Deterministic clock in UTC; `start` (1970-01-12 13:46 UTC) is daytime, outside the night window. */
    private fun utcClock(now: () -> Long) = object : Clock {
        override fun nowMillis(): Long = now()
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    private class FakePetRepository(initial: Pet) : PetRepository {
        val pet = MutableStateFlow(initial)

        override fun getPetFlow(): Flow<Pet> = pet
        override suspend fun getPet(): Pet = pet.value
        override suspend fun updatePet(transform: (Pet) -> Pet): Pet = transform(pet.value).also { pet.value = it }
        override suspend fun recordHabit(habit: HabitType): Pet = pet.value
        override fun habitEventsSinceFlow(fromMillis: Long): Flow<List<HabitEvent>> = flowOf(emptyList())
        override suspend fun startOver(): Pet = error("not used")
        override suspend fun recordHabits(habits: List<HabitType>): Pet = pet.value
    }
}

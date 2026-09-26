// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.repository.InMemorySettingsRepository
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.HabitEvent
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.PetArchetype
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ObservePetDetailsUseCaseTest {

    private val now = Instant.parse("2026-01-10T08:00:00Z").toEpochMilli()
    private val day = 24 * 3_600_000L

    private val clock = object : Clock {
        override fun nowMillis(): Long = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    private val pet = MutableStateFlow(Pet(bornTimestamp = now - 2 * day))
    private val history = MutableStateFlow<List<HabitEvent>>(emptyList())

    private val repository = object : PetRepository {
        val requestedFrom = mutableListOf<Long>()
        override fun habitEventsSinceFlow(fromMillis: Long): Flow<List<HabitEvent>> {
            requestedFrom += fromMillis
            return history.map { events -> events.filter { it.timestampMillis >= fromMillis } }
        }
        override fun getPetFlow(): Flow<Pet> = pet
        override suspend fun getPet(): Pet = error("not used")
        override suspend fun updatePet(transform: (Pet) -> Pet): Pet = error("not used")
        override suspend fun recordHabit(habit: HabitType): Pet = error("not used")
        override suspend fun startOver(): Pet = error("not used")
        override suspend fun recordHabits(habits: List<HabitType>): Pet = error("not used")
    }

    private fun useCase() = ObservePetDetailsUseCase(repository, InMemorySettingsRepository(), clock)

    @Test
    fun `history is read from the start of the pet's birth day`() = runTest {
        backgroundScope.launch { useCase().execute().collect {} }
        runCurrent()

        assertEquals(listOf(Instant.parse("2026-01-08T00:00:00Z").toEpochMilli()), repository.requestedFrom)
    }

    @Test
    fun `a new pet starts with a clean record`() = runTest {
        history.value = listOf(HabitEvent(HabitType.Steps(7_000), now - day))
        val emissions = mutableListOf<PetDetails>()
        backgroundScope.launch { useCase().execute().collect { emissions += it } }
        runCurrent()

        pet.value = Pet(name = "Nova", bornTimestamp = now)
        runCurrent()

        assertEquals(1, emissions.first().goalHistory.sinceBirth.daysReached(PetArchetype.CARDIO_RUNNER))
        assertEquals("Nova", emissions.last().pet.name)
        assertEquals(1, emissions.last().goalHistory.sinceBirth.days)
        assertEquals(0, emissions.last().goalHistory.sinceBirth.daysReached(PetArchetype.CARDIO_RUNNER))
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.Vitals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetPetStateUseCaseTest {

    private val start = 1_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun `open screen keeps decaying on each tick without any database write`() = runTest {
        val repository = FakePetRepository(Pet(vitals = Vitals(hydration = 50f, lastUpdatedTimestamp = start)))
        val clock = Clock { start + testScheduler.currentTime }
        val useCase = GetPetStateUseCase(repository, clock, refreshIntervalMillis = hour)

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
        val useCase = GetPetStateUseCase(repository, Clock { start }, refreshIntervalMillis = hour)

        val emissions = mutableListOf<PetWithMood>()
        backgroundScope.launch { useCase.execute().collect { emissions += it } }
        runCurrent()

        repository.pet.value = Pet(vitals = Vitals(hydration = 90f, lastUpdatedTimestamp = start))
        runCurrent()

        assertTrue(emissions.size >= 2)
        assertEquals(90f, emissions.last().pet.vitals.hydration, 0.01f)
    }

    private class FakePetRepository(initial: Pet) : PetRepository {
        val pet = MutableStateFlow(initial)

        override fun getPetFlow(): Flow<Pet> = pet
        override suspend fun getPet(): Pet = pet.value
        override suspend fun updatePet(transform: (Pet) -> Pet): Pet = transform(pet.value).also { pet.value = it }
        override suspend fun recordHabit(habit: HabitType): Pet = pet.value
        override suspend fun recordHabits(habits: List<HabitType>): Pet = pet.value
    }
}

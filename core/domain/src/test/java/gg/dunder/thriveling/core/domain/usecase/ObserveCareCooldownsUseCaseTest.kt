// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.usecase

import gg.dunder.thriveling.core.domain.engine.CareAction
import gg.dunder.thriveling.core.domain.engine.CareCooldown
import gg.dunder.thriveling.core.domain.repository.PetRepository
import gg.dunder.thriveling.core.domain.time.Clock
import gg.dunder.thriveling.core.model.HabitEvent
import gg.dunder.thriveling.core.model.HabitType
import gg.dunder.thriveling.core.model.Pet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class ObserveCareCooldownsUseCaseTest {

    private val minute = 60_000L

    private val history = MutableStateFlow<List<HabitEvent>>(emptyList())

    private val repository = object : PetRepository {
        override fun habitEventsSinceFlow(fromMillis: Long): Flow<List<HabitEvent>> = history
        override fun getPetFlow(): Flow<Pet> = error("not used")
        override suspend fun getPet(): Pet = error("not used")
        override suspend fun updatePet(transform: (Pet) -> Pet): Pet = error("not used")
        override suspend fun recordHabit(habit: HabitType): Pet = error("not used")
        override suspend fun recordHabits(habits: List<HabitType>): Pet = error("not used")
        override suspend fun startOver(): Pet = error("not used")
    }

    /** A clock that follows the test's virtual time, so `delay` moves "now" forward. */
    private fun TestScope.useCase() = ObserveCareCooldownsUseCase(
        repository,
        object : Clock {
            override fun nowMillis(): Long = testScheduler.currentTime
            override fun zone(): ZoneId = ZoneOffset.UTC
        }
    )

    @Test
    fun `a logged drink dims water until its cooldown ends`() = runTest {
        val emissions = mutableListOf<Set<CareAction>>()
        backgroundScope.launch { useCase().execute().collect { emissions += it } }
        runCurrent()

        advanceTimeBy(10 * minute)
        history.value = listOf(HabitEvent(HabitType.Hydration(250), testScheduler.currentTime))
        runCurrent()
        advanceTimeBy(CareCooldown.COOLDOWN_MS - 1)
        assertEquals(setOf(CareAction.WATER), emissions.last())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(emptySet(), setOf(CareAction.WATER), emptySet<CareAction>()), emissions)
    }

    @Test
    fun `food and water cool down separately`() = runTest {
        advanceTimeBy(2 * CareCooldown.COOLDOWN_MS)
        val now = testScheduler.currentTime
        history.value = listOf(
            HabitEvent(HabitType.Meal(isHealthy = true), now - 50 * minute),
            HabitEvent(HabitType.Hydration(250), now - 20 * minute)
        )
        val emissions = mutableListOf<Set<CareAction>>()
        backgroundScope.launch { useCase().execute().collect { emissions += it } }
        runCurrent()

        advanceTimeBy(10 * minute)
        runCurrent()

        assertEquals(listOf(setOf(CareAction.FOOD, CareAction.WATER), setOf(CareAction.WATER)), emissions)
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.repository.InMemorySettingsRepository
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.settings.DailyGoals
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

class ObserveDailyFocusUseCaseTest {

    private val morning = Instant.parse("2026-01-10T08:00:00Z").toEpochMilli()
    private val hour = 3_600_000L
    private var now = morning

    private val clock = object : Clock {
        override fun nowMillis(): Long = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    private val settings = InMemorySettingsRepository()

    private val history = MutableStateFlow<List<HabitEvent>>(emptyList())

    private val repository = object : PetRepository {
        var requestedFrom: Long? = null
        override fun habitEventsSinceFlow(fromMillis: Long): Flow<List<HabitEvent>> {
            requestedFrom = fromMillis
            return history.map { events -> events.filter { it.timestampMillis >= fromMillis } }
        }
        override fun getPetFlow(): Flow<Pet> = error("not used")
        override suspend fun getPet(): Pet = error("not used")
        override suspend fun updatePet(transform: (Pet) -> Pet): Pet = error("not used")
        override suspend fun recordHabit(habit: HabitType): Pet = error("not used")
        override suspend fun recordHabits(habits: List<HabitType>): Pet = error("not used")
    }

    private fun log(habit: HabitType, at: Long = now) {
        history.value = history.value + HabitEvent(habit, at)
    }

    @Test
    fun `reading starts at the beginning of today`() = runTest {
        ObserveDailyFocusUseCase(repository, settings, clock).execute()
        assertEquals(Instant.parse("2026-01-10T00:00:00Z").toEpochMilli(), repository.requestedFrom)
    }

    @Test
    fun `a goal appears once today's habits cross its threshold, and only changes are emitted`() = runTest {
        val emissions = mutableListOf<Set<PetArchetype>>()
        backgroundScope.launch { ObserveDailyFocusUseCase(repository, settings, clock).execute().collect { emissions += it } }
        runCurrent()

        log(HabitType.Steps(3_000))
        runCurrent()
        log(HabitType.Steps(3_000))
        runCurrent()
        log(HabitType.Steps(500))
        runCurrent()

        assertEquals(listOf(emptySet(), setOf(PetArchetype.CARDIO_RUNNER)), emissions)
    }

    @Test
    fun `a stream left open past midnight starts the new day empty`() = runTest {
        log(HabitType.Steps(7_000))
        val emissions = mutableListOf<Set<PetArchetype>>()
        backgroundScope.launch { ObserveDailyFocusUseCase(repository, settings, clock).execute().collect { emissions += it } }
        runCurrent()

        now = morning + 20 * hour // 04:00 the next day
        log(HabitType.Steps(100))
        runCurrent()

        assertEquals(listOf(setOf(PetArchetype.CARDIO_RUNNER), emptySet()), emissions)
    }

    @Test
    fun `goals are measured against the user's targets, and a changed target applies at once`() = runTest {
        settings.updateSettings { it.copy(dailyGoals = DailyGoals(steps = 8_000)) }
        log(HabitType.Steps(7_000))
        val emissions = mutableListOf<Set<PetArchetype>>()
        backgroundScope.launch { ObserveDailyFocusUseCase(repository, settings, clock).execute().collect { emissions += it } }
        runCurrent()

        settings.updateSettings { it.copy(dailyGoals = DailyGoals(steps = 5_000)) }
        runCurrent()

        assertEquals(listOf(emptySet(), setOf(PetArchetype.CARDIO_RUNNER)), emissions)
    }
}

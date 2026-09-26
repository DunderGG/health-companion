// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.CriticalVital
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.repository.VitalAlertStateRepository
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.Vitals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CheckCriticalVitalsUseCaseTest {

    private val noon = Instant.parse("2026-01-10T12:00:00Z").toEpochMilli()
    private val hour = 3_600_000L

    @Test
    fun `vitals are decayed to now before checking, and the alert is recorded`() = runTest {
        // Stored hydration 30 two hours ago → 24 now (3/h): critical.
        val pet = Pet(name = "Mochi", vitals = Vitals(hydration = 30f, lastUpdatedTimestamp = noon - 2 * hour))
        val state = FakeAlertState()
        val useCase = CheckCriticalVitalsUseCase(FakePetRepository(pet), state, utcClock(noon))

        val check = useCase.execute()

        assertEquals("Mochi", check.petName)
        assertEquals(setOf(CriticalVital.HYDRATION), check.plan.newlyCritical)
        assertEquals(setOf(CriticalVital.HYDRATION), state.notified)
    }

    @Test
    fun `a second check in the same episode alerts nothing`() = runTest {
        val pet = Pet(vitals = Vitals(hunger = 10f, lastUpdatedTimestamp = noon))
        val state = FakeAlertState()
        val useCase = CheckCriticalVitalsUseCase(FakePetRepository(pet), state, utcClock(noon))

        useCase.execute()
        val second = useCase.execute()

        assertTrue(second.plan.newlyCritical.isEmpty())
    }

    private fun utcClock(now: Long) = object : Clock {
        override fun nowMillis(): Long = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    private class FakeAlertState : VitalAlertStateRepository {
        var notified: Set<CriticalVital> = emptySet()
        override suspend fun notifiedVitals(): Set<CriticalVital> = notified
        override suspend fun setNotifiedVitals(vitals: Set<CriticalVital>) {
            notified = vitals
        }
    }

    private class FakePetRepository(private val pet: Pet) : PetRepository {
        override fun getPetFlow(): Flow<Pet> = flowOf(pet)
        override suspend fun getPet(): Pet = pet
        override suspend fun updatePet(transform: (Pet) -> Pet): Pet = error("check must not write")
        override suspend fun recordHabit(habit: HabitType): Pet = error("check must not write")
        override suspend fun recordHabits(habits: List<HabitType>): Pet = error("check must not write")
    }
}

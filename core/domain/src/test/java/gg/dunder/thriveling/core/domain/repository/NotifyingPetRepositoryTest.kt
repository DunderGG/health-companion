// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.repository

import gg.dunder.thriveling.core.model.HabitEvent
import gg.dunder.thriveling.core.model.HabitType
import gg.dunder.thriveling.core.model.Pet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NotifyingPetRepositoryTest {

    private var notifications = 0

    @Test
    fun `every successful mutation notifies once`() = runTest {
        val repository = NotifyingPetRepository(StubPetRepository()) { notifications++ }

        repository.recordHabit(HabitType.Hydration())
        repository.recordHabits(listOf(HabitType.Hydration(), HabitType.PettingInteraction()))
        repository.updatePet { it }
        repository.startOver()

        assertEquals(4, notifications)
    }

    @Test
    fun `reads do not notify`() = runTest {
        val repository = NotifyingPetRepository(StubPetRepository()) { notifications++ }

        repository.getPet()
        repository.getPetFlow()

        assertEquals(0, notifications)
    }

    @Test
    fun `failed mutation does not notify`() {
        val repository = NotifyingPetRepository(StubPetRepository(fail = true)) { notifications++ }

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { repository.recordHabit(HabitType.Hydration()) }
        }
        assertEquals(0, notifications)
    }

    private class StubPetRepository(private val fail: Boolean = false) : PetRepository {
        override fun getPetFlow(): Flow<Pet> = flowOf(Pet())
        override suspend fun getPet(): Pet = Pet()
        override suspend fun updatePet(transform: (Pet) -> Pet): Pet = write { transform(Pet()) }
        override suspend fun recordHabit(habit: HabitType): Pet = write { Pet() }
        override fun habitEventsSinceFlow(fromMillis: Long): Flow<List<HabitEvent>> = flowOf(emptyList())
        override suspend fun recordHabits(habits: List<HabitType>): Pet = write { Pet() }
        override suspend fun startOver(): Pet = write { Pet() }

        private fun write(block: () -> Pet): Pet {
            check(!fail) { "write failed" }
            return block()
        }
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.DailyTotalBaseline
import com.healthcompanion.core.domain.engine.DailyTotalTracker
import com.healthcompanion.core.domain.repository.PassiveSyncRepository
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngestPassiveDataUseCaseTest {

    private val day = 20_000L
    private val now = 1_000_000_000L

    private val petRepository = RecordingPetRepository()
    private val useCase = IngestPassiveDataUseCase(petRepository, InMemoryPassiveSyncRepository())

    @Test
    fun `re-delivered daily step total is applied only once`() = runTest {
        val batch = PassiveDataBatch(steps = DailyTotalReading(day, 8_000.0))

        useCase.execute(batch, now)
        val second = useCase.execute(batch, now + 60_000)

        assertEquals(listOf(listOf<HabitType>(HabitType.Steps(8_000))), petRepository.recordedBatches)
        assertNull(second)
    }

    @Test
    fun `growing daily totals apply only the increase`() = runTest {
        useCase.execute(PassiveDataBatch(steps = DailyTotalReading(day, 1_000.0)), now)
        useCase.execute(PassiveDataBatch(steps = DailyTotalReading(day, 1_450.0)), now + 60_000)

        assertEquals(
            listOf(listOf(HabitType.Steps(1_000)), listOf(HabitType.Steps(400))),
            petRepository.recordedBatches
        )
    }

    @Test
    fun `all habits from one batch are recorded in a single write`() = runTest {
        val batch = PassiveDataBatch(
            steps = DailyTotalReading(day, 600.0),
            floors = DailyTotalReading(day, 12.0),
            latestHeartRateBpm = 72.0
        )

        useCase.execute(batch, now)

        assertEquals(
            listOf(listOf(HabitType.Steps(600), HabitType.Steps(200), HabitType.HeartRate(72f))),
            petRepository.recordedBatches
        )
    }

    @Test
    fun `heart rate is awarded at most once per interval`() = runTest {
        val interval = IngestPassiveDataUseCase.HEART_RATE_AWARD_INTERVAL_MS
        val batch = PassiveDataBatch(latestHeartRateBpm = 65.0)

        useCase.execute(batch, now)
        useCase.execute(batch, now + interval - 1)
        useCase.execute(batch, now + interval)

        assertEquals(2, petRepository.recordedBatches.size)
    }

    @Test
    fun `batch with nothing new does not touch the pet`() = runTest {
        val result = useCase.execute(PassiveDataBatch(steps = DailyTotalReading(day, 150.0)), now)

        assertNull(result)
        assertEquals(emptyList<List<HabitType>>(), petRepository.recordedBatches)
    }

    private class RecordingPetRepository : PetRepository {
        val recordedBatches = mutableListOf<List<HabitType>>()

        override fun getPetFlow(): Flow<Pet> = flowOf(Pet())
        override suspend fun getPet(): Pet = Pet()
        override suspend fun updatePet(transform: (Pet) -> Pet): Pet = transform(Pet())
        override suspend fun recordHabit(habit: HabitType): Pet = recordHabits(listOf(habit))
        override suspend fun recordHabits(habits: List<HabitType>): Pet {
            recordedBatches += habits
            return Pet()
        }
    }

    private class InMemoryPassiveSyncRepository : PassiveSyncRepository {
        private val baselines = mutableMapOf<String, DailyTotalBaseline>()
        private var lastHeartRateAward: Long? = null

        override suspend fun consumeDailyTotal(key: String, epochDay: Long, total: Double, granularity: Double): Double {
            val update = DailyTotalTracker.consume(baselines[key], epochDay, total, granularity)
            baselines[key] = update.baseline
            return update.consumed
        }

        override suspend fun tryClaimHeartRateAward(nowMillis: Long, minIntervalMillis: Long): Boolean {
            val last = lastHeartRateAward
            if (last != null && nowMillis - last < minIntervalMillis) return false
            lastHeartRateAward = nowMillis
            return true
        }
    }
}

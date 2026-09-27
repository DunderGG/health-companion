// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.usecase

import gg.dunder.thriveling.core.domain.engine.StepCadence
import gg.dunder.thriveling.core.domain.sensor.LiveStepSource
import gg.dunder.thriveling.core.domain.time.Clock
import gg.dunder.thriveling.core.model.PetActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservePetActivityUseCaseTest {

    private val start = 1_000_000_000L

    @Test
    fun `starts idle, walks with the user and stops shortly after they do`() = runTest {
        val steps = MutableSharedFlow<Long>(extraBufferCapacity = 64)
        val clock = Clock { start + testScheduler.currentTime }
        val useCase = ObservePetActivityUseCase({ steps }, clock)

        val emissions = mutableListOf<PetActivity>()
        backgroundScope.launch { useCase.execute().collect { emissions += it } }
        runCurrent()
        assertEquals(listOf(PetActivity.IDLE), emissions)

        walk(steps, clock, count = 6, intervalMillis = 500)
        assertEquals(listOf(PetActivity.IDLE, PetActivity.WALKING), emissions)

        // No more steps: the ticker returns the pet to idle after the idle timeout.
        advanceTimeBy(StepCadence.IDLE_AFTER_MS + ObservePetActivityUseCase.DEFAULT_TICK_MS)
        runCurrent()
        assertEquals(listOf(PetActivity.IDLE, PetActivity.WALKING, PetActivity.IDLE), emissions)
    }

    @Test
    fun `a running cadence is mirrored as running`() = runTest {
        val steps = MutableSharedFlow<Long>(extraBufferCapacity = 64)
        val clock = Clock { start + testScheduler.currentTime }
        val useCase = ObservePetActivityUseCase({ steps }, clock)

        val emissions = mutableListOf<PetActivity>()
        backgroundScope.launch { useCase.execute().collect { emissions += it } }
        runCurrent()

        walk(steps, clock, count = 8, intervalMillis = 360)

        assertEquals(PetActivity.RUNNING, emissions.last())
    }

    @Test
    fun `without a step sensor the pet just idles`() = runTest {
        val useCase = ObservePetActivityUseCase({ emptyFlow() }, Clock { start + testScheduler.currentTime })

        val emissions = mutableListOf<PetActivity>()
        backgroundScope.launch { useCase.execute().collect { emissions += it } }
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(listOf(PetActivity.IDLE), emissions)
    }

    private fun TestScope.walk(steps: MutableSharedFlow<Long>, clock: Clock, count: Int, intervalMillis: Long) {
        repeat(count) {
            steps.tryEmit(clock.nowMillis())
            runCurrent()
            advanceTimeBy(intervalMillis)
        }
        runCurrent()
    }
}

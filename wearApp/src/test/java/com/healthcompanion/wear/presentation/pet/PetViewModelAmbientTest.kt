// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.sensor.LiveStepSource
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.domain.usecase.GetPetStateUseCase
import com.healthcompanion.core.domain.usecase.LogHabitUseCase
import com.healthcompanion.core.domain.usecase.ObservePetActivityUseCase
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.Vitals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PetViewModelAmbientTest {

    private val dispatcher = StandardTestDispatcher()

    /** 1970-01-12 13:46 UTC: daytime, outside the pet's night window. */
    private val start = 1_000_000_000L
    private val hour = 3_600_000L
    private var now = start

    private val clock = object : Clock {
        override fun nowMillis(): Long = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    /** Number of live collectors of the step sensor, i.e. whether the listener is registered. */
    private var activeStepListeners = 0

    private val stepSource = LiveStepSource {
        flow {
            activeStepListeners++
            try {
                awaitCancellation()
            } finally {
                activeStepListeners--
            }
        }
    }

    private val repository = object : PetRepository {
        val pet = MutableStateFlow(Pet(vitals = Vitals(hydration = 50f, lastUpdatedTimestamp = start)))
        override fun getPetFlow(): Flow<Pet> = pet
        override suspend fun getPet(): Pet = pet.value
        override suspend fun updatePet(transform: (Pet) -> Pet): Pet = transform(pet.value).also { pet.value = it }
        override suspend fun recordHabit(habit: HabitType): Pet = pet.value
        override suspend fun recordHabits(habits: List<HabitType>): Pet = pet.value
    }

    private fun viewModel() = PetViewModel(
        getPetStateUseCase = GetPetStateUseCase(repository, clock, refreshIntervalMillis = 24 * hour),
        logHabitUseCase = LogHabitUseCase(repository),
        observePetActivityUseCase = ObservePetActivityUseCase(stepSource, clock),
        clock = clock
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collect(viewModel: PetViewModel): MutableList<PetUiState> {
        val states = mutableListOf<PetUiState>()
        backgroundScope.launch { viewModel.uiState.collect { states += it } }
        runCurrent()
        return states
    }

    @Test
    fun `the step sensor is released in ambient mode and re-acquired on exit`() = runTest(dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        assertEquals(1, activeStepListeners)

        viewModel.setAmbient(true)
        runCurrent()
        assertEquals(0, activeStepListeners)

        viewModel.setAmbient(false)
        runCurrent()
        assertEquals(1, activeStepListeners)
    }

    @Test
    fun `the ambient update re-evaluates decay`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val states = collect(viewModel)
        viewModel.setAmbient(true)
        runCurrent()

        // An hour passes without the decay ticker firing, as when the CPU sleeps in ambient mode.
        now = start + hour
        viewModel.onAmbientUpdate()
        runCurrent()

        val latest = states.last() as PetUiState.Success
        assertEquals(47f, latest.pet.vitals.hydration, 0.01f)
    }
}

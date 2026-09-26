// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import com.healthcompanion.core.domain.engine.DailyProgress
import com.healthcompanion.core.domain.repository.InMemorySettingsRepository
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.sensor.LiveStepSource
import com.healthcompanion.core.domain.settings.DailyGoals
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.domain.usecase.GetPetStateUseCase
import com.healthcompanion.core.domain.usecase.LogHabitUseCase
import com.healthcompanion.core.domain.usecase.ObserveDailyProgressUseCase
import com.healthcompanion.core.domain.usecase.ObservePetActivityUseCase
import com.healthcompanion.core.model.EvolutionStage
import com.healthcompanion.core.model.HabitEvent
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.PetArchetype
import com.healthcompanion.core.model.Vitals
import com.healthcompanion.wear.haptics.PetHapticEvent
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
class PetViewModelTest {

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
        val history = MutableStateFlow<List<HabitEvent>>(emptyList())
        override fun habitEventsSinceFlow(fromMillis: Long): Flow<List<HabitEvent>> = history
        override fun getPetFlow(): Flow<Pet> = pet
        override suspend fun getPet(): Pet = pet.value
        override suspend fun updatePet(transform: (Pet) -> Pet): Pet = transform(pet.value).also { pet.value = it }
        override suspend fun recordHabit(habit: HabitType): Pet = pet.value
        override suspend fun recordHabits(habits: List<HabitType>): Pet = pet.value
    }

    private val settings = InMemorySettingsRepository()

    private fun viewModel() = PetViewModel(
        getPetStateUseCase = GetPetStateUseCase(repository, settings, clock, refreshIntervalMillis = 24 * hour),
        logHabitUseCase = LogHabitUseCase(repository),
        observePetActivityUseCase = ObservePetActivityUseCase(stepSource, clock),
        observeDailyProgressUseCase = ObserveDailyProgressUseCase(repository, settings, clock),
        settingsRepository = settings,
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

    private fun TestScope.collectHaptics(viewModel: PetViewModel): MutableList<PetHapticEvent> {
        val events = mutableListOf<PetHapticEvent>()
        backgroundScope.launch { viewModel.hapticEvents.collect { events += it } }
        runCurrent()
        return events
    }

    @Test
    fun `an accepted pet purrs, a pet in its cooldown does not`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val haptics = collectHaptics(viewModel)
        now = start + PetViewModel.PET_COOLDOWN_MS

        viewModel.petCompanion()
        runCurrent()
        viewModel.petCompanion() // still in the cooldown
        runCurrent()

        assertEquals(listOf(PetHapticEvent.PETTING), haptics)
    }

    @Test
    fun `growing into the next stage plays the evolution pattern once`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val haptics = collectHaptics(viewModel)

        repository.pet.value = repository.pet.value.copy(stage = EvolutionStage.CHILD)
        runCurrent()
        repository.pet.value = repository.pet.value.copy(experiencePoints = 10) // same stage
        runCurrent()

        assertEquals(listOf(PetHapticEvent.EVOLUTION), haptics)
    }

    @Test
    fun `reaching a daily focus goal plays the goal pattern, but not goals already reached on opening`() = runTest(dispatcher) {
        repository.history.value = listOf(HabitEvent(HabitType.Steps(7_000), start))
        val viewModel = viewModel()
        val haptics = collectHaptics(viewModel)
        assertEquals(emptyList<PetHapticEvent>(), haptics)

        repository.history.value += HabitEvent(HabitType.HeartRate(bpm = 130f), start)
        runCurrent()

        assertEquals(listOf(PetHapticEvent.GOAL_REACHED), haptics)
    }

    @Test
    fun `today's goal progress follows the habit history and the user's goals`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val progress = mutableListOf<DailyProgress?>()
        backgroundScope.launch { viewModel.dailyProgress.collect { progress += it } }
        runCurrent()

        repository.history.value = listOf(HabitEvent(HabitType.Steps(4_000), start))
        runCurrent()
        settings.updateSettings { it.copy(dailyGoals = DailyGoals(steps = 4_000)) }
        runCurrent()

        assertEquals(4_000, progress.last()?.steps)
        assertEquals(setOf(PetArchetype.CARDIO_RUNNER), progress.last()?.reached)
    }

    @Test
    fun `no pattern plays while the user has switched haptics off`() = runTest(dispatcher) {
        settings.updateSettings { it.copy(hapticsEnabled = false) }
        val viewModel = viewModel()
        val haptics = collectHaptics(viewModel)
        now = start + PetViewModel.PET_COOLDOWN_MS

        viewModel.petCompanion()
        repository.pet.value = repository.pet.value.copy(stage = EvolutionStage.CHILD)
        repository.history.value += HabitEvent(HabitType.Steps(7_000), start)
        runCurrent()

        assertEquals(emptyList<PetHapticEvent>(), haptics)
    }
}

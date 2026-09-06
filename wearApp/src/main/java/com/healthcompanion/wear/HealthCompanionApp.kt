// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.repository.PetRepositoryImpl
import com.healthcompanion.core.data.workers.PetDecayWorker
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.usecase.GetPetStateUseCase
import com.healthcompanion.core.domain.usecase.LogHabitUseCase
import com.healthcompanion.core.health.HealthServicesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class HealthCompanionApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: CompanionDatabase
        private set
    lateinit var petRepository: PetRepository
        private set
    lateinit var getPetStateUseCase: GetPetStateUseCase
        private set
    lateinit var logHabitUseCase: LogHabitUseCase
        private set
    lateinit var healthServicesManager: HealthServicesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Room Database & Repositories
        database = CompanionDatabase.getInstance(this)
        petRepository = PetRepositoryImpl(database.petDao())
        getPetStateUseCase = GetPetStateUseCase(petRepository)
        logHabitUseCase = LogHabitUseCase(petRepository)
        healthServicesManager = HealthServicesManager(this)

        // Schedule periodic battery-efficient decay check (every 2 hours)
        schedulePeriodicDecay()

        // Register passive step tracking via Health Services
        appScope.launch {
            healthServicesManager.registerPassiveDataService()
        }
    }

    private fun schedulePeriodicDecay() {
        val decayRequest = PeriodicWorkRequestBuilder<PetDecayWorker>(2, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PetPeriodicDecayWork",
            ExistingPeriodicWorkPolicy.KEEP,
            decayRequest
        )
    }

    companion object {
        lateinit var instance: HealthCompanionApp
            private set
    }
}


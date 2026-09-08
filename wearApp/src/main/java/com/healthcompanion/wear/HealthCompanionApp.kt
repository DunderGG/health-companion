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

/**
 * Wear OS Application class serving as the composition root and service locator for the application.
 *
 * ### Kotlin vs C++ Note:
 * - **`lateinit var`**: In Kotlin, non-nullable types must be initialized in constructors by default.
 *   `lateinit` defers initialization until Android's [onCreate] lifecycle callback, avoiding the overhead
 *   of nullable `T?` types and null-checks everywhere (analogous to declaring a member pointer that is
 *   guaranteed to be instantiated in an init method before any usage).
 * - **`private set`**: Exposes a public read-only property with a private mutating setter, equivalent
 *   to `const T& getProperty() const` in C++ with a private `setProperty(...)`.
 * - **Manual Dependency Injection**: Instantiates singletons (database, repository, use cases) once
 *   during app startup and provides them to ViewModels and services throughout the app lifecycle.
 */
class HealthCompanionApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Singleton SQLite Room database instance. */
    lateinit var database: CompanionDatabase
        private set

    /** Repository instance managing pet state and persistence. */
    lateinit var petRepository: PetRepository
        private set

    /** Use case streaming pet vitals with lazy decay evaluation. */
    lateinit var getPetStateUseCase: GetPetStateUseCase
        private set

    /** Use case for recording health habits and touch interactions. */
    lateinit var logHabitUseCase: LogHabitUseCase
        private set

    /** Manager for Wear OS Health Services sensor subscriptions. */
    lateinit var healthServicesManager: HealthServicesManager
        private set

    /**
     * Initializes singletons, schedules periodic background decay checks,
     * and subscribes to passive sensor tracking via Health Services.
     */
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

    /**
     * Enqueues a unique periodic WorkManager task running [PetDecayWorker] every 2 hours.
     * Uses [ExistingPeriodicWorkPolicy.KEEP] so existing scheduled jobs are preserved across app launches.
     */
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
        /**
         * Globally accessible reference to the [HealthCompanionApp] instance.
         */
        lateinit var instance: HealthCompanionApp
            private set
    }
}


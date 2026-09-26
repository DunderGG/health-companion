// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.healthcompanion.core.data.workers.PetDecayWorker
import com.healthcompanion.core.domain.usecase.IngestPassiveDataUseCase
import com.healthcompanion.core.health.PassiveDataDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Wear OS Application class owning the app's single dependency graph ([AppContainer]).
 *
 * Android components obtain dependencies from here: activities and the tile via
 * `(application as HealthCompanionApp).container`, and the library-module
 * [com.healthcompanion.core.health.PassiveDataService] via the [PassiveDataDependencies] interface.
 *
 * ### Kotlin vs C++ Note:
 * - **`lateinit var`**: In Kotlin, non-nullable types must be initialized in constructors by default.
 *   `lateinit` defers initialization until Android's [onCreate] lifecycle callback (analogous to a member
 *   pointer guaranteed to be set in an init method before any usage).
 * - **`private set`**: Exposes a public read-only property with a private mutating setter, equivalent
 *   to `const T& getProperty() const` in C++ with a private `setProperty(...)`.
 */
class HealthCompanionApp : Application(), PassiveDataDependencies {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The application's dependency graph. */
    lateinit var container: AppContainer
        private set

    override val ingestPassiveDataUseCase: IngestPassiveDataUseCase
        get() = container.ingestPassiveDataUseCase

    /**
     * Builds the dependency graph, schedules periodic background decay checks,
     * and syncs passive sensor tracking via Health Services.
     */
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Schedule periodic battery-efficient decay check (every 2 hours)
        schedulePeriodicDecay()

        // Sync the passive Health Services registration with the current permissions.
        // Cheap no-op when nothing changed; re-registers after a reboot or permission change.
        appScope.launch {
            try {
                container.healthServicesManager.ensureRegistered()
            } catch (e: Exception) {
                Log.w(TAG, "Passive registration failed", e)
            }
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

    private companion object {
        const val TAG = "HealthCompanionApp"
    }
}

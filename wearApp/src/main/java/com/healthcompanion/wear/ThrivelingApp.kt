// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear

import android.app.Application
import android.util.Log
import androidx.work.WorkManager
import com.healthcompanion.core.domain.usecase.IngestPassiveDataUseCase
import com.healthcompanion.core.health.PassiveDataDependencies
import com.healthcompanion.wear.notifications.VitalAlertWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wear OS Application class owning the app's single dependency graph ([AppContainer]).
 *
 * Android components obtain dependencies from here: activities and the tile via
 * `(application as ThrivelingApp).container`, and the library-module
 * [com.healthcompanion.core.health.PassiveDataService] via the [PassiveDataDependencies] interface.
 *
 * ### Kotlin vs C++ Note:
 * - **`lateinit var`**: In Kotlin, non-nullable types must be initialized in constructors by default.
 *   `lateinit` defers initialization until Android's [onCreate] lifecycle callback (analogous to a member
 *   pointer guaranteed to be set in an init method before any usage).
 * - **`private set`**: Exposes a public read-only property with a private mutating setter, equivalent
 *   to `const T& getProperty() const` in C++ with a private `setProperty(...)`.
 */
class ThrivelingApp : Application(), PassiveDataDependencies {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The application's dependency graph. */
    lateinit var container: AppContainer
        private set

    override val ingestPassiveDataUseCase: IngestPassiveDataUseCase
        get() = container.ingestPassiveDataUseCase

    /**
     * Builds the dependency graph, removes obsolete background work, prepares critical-vital
     * notifications, and syncs passive sensor tracking via Health Services.
     */
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        cancelLegacyDecayWork()

        // Critical-vital notifications: the channel must exist before the first post,
        // and a check is scheduled unless one is already pending.
        container.vitalAlertNotifier.createChannel()
        VitalAlertWorker.ensureScheduled(this)

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
     * Earlier versions scheduled a 2-hour periodic `PetDecayWorker`. Decay is computed on read, so that
     * worker was removed (AR-5); cancel the job persisted by WorkManager on upgraded installs so it never
     * tries to instantiate the deleted worker class. Cheap and idempotent.
     */
    private fun cancelLegacyDecayWork() {
        WorkManager.getInstance(this).cancelUniqueWork(LEGACY_DECAY_WORK_NAME)
    }

    private companion object {
        const val TAG = "ThrivelingApp"
        const val LEGACY_DECAY_WORK_NAME = "PetPeriodicDecayWork"
    }
}

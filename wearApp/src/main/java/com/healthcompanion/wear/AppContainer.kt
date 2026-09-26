// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear

import android.content.Context
import androidx.wear.tiles.TileService
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.repository.PassiveSyncRepositoryImpl
import com.healthcompanion.core.data.repository.PetRepositoryImpl
import com.healthcompanion.core.data.repository.VitalAlertStateRepositoryImpl
import com.healthcompanion.core.domain.repository.NotifyingPetRepository
import com.healthcompanion.core.domain.repository.PassiveSyncRepository
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.domain.usecase.CheckCriticalVitalsUseCase
import com.healthcompanion.core.domain.usecase.GetPetStateUseCase
import com.healthcompanion.core.domain.usecase.IngestPassiveDataUseCase
import com.healthcompanion.core.domain.usecase.LogHabitUseCase
import com.healthcompanion.core.domain.usecase.ObserveDailyFocusUseCase
import com.healthcompanion.core.domain.usecase.ObservePetActivityUseCase
import com.healthcompanion.core.health.HealthServicesManager
import com.healthcompanion.core.health.SensorLiveStepSource
import com.healthcompanion.wear.complications.PetMoodComplicationService
import com.healthcompanion.wear.notifications.VitalAlertNotifier
import com.healthcompanion.wear.notifications.VitalAlertWorker
import com.healthcompanion.wear.tiles.PetStatusTileService
import com.healthcompanion.wear.tiles.TileClickLedger

/**
 * Composition root: the single dependency graph shared by the activity, view models,
 * the passive data service, the tile and the complication.
 *
 * Every dependency is created lazily, because the process is often started just to deliver a
 * sensor batch or render a tile, and should only build what that entry point needs.
 *
 * ### Kotlin vs C++ Note:
 * - **Manual Dependency Injection**: Equivalent to a hand-written factory/registry object that owns
 *   long-lived services and hands out references, instead of each component constructing its own.
 * - **`by lazy`**: Thread-safe, construct-on-first-use members (like `std::call_once` per member).
 *
 * @param context Any context; only the application context is retained.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Wall clock used by all game logic. */
    val clock: Clock = Clock.SYSTEM

    val database: CompanionDatabase by lazy { CompanionDatabase.getInstance(appContext) }

    /**
     * Every committed write also asks the system to refresh the pull-based Tile and complication, and re-checks
     * critical vitals, so all writers (UI, passive sensors) keep them current without knowing about them.
     */
    val petRepository: PetRepository by lazy {
        NotifyingPetRepository(PetRepositoryImpl(database, clock)) {
            requestSurfaceRefresh()
            VitalAlertWorker.requestCheck(appContext)
        }
    }

    val passiveSyncRepository: PassiveSyncRepository by lazy { PassiveSyncRepositoryImpl.getInstance(appContext) }

    val getPetStateUseCase: GetPetStateUseCase by lazy { GetPetStateUseCase(petRepository, clock) }

    val logHabitUseCase: LogHabitUseCase by lazy { LogHabitUseCase(petRepository) }

    val ingestPassiveDataUseCase: IngestPassiveDataUseCase by lazy {
        IngestPassiveDataUseCase(petRepository, passiveSyncRepository, clock)
    }

    /** Live, cosmetic step reactions; the step sensor is held only while the pet screen collects it. */
    val observePetActivityUseCase: ObservePetActivityUseCase by lazy {
        ObservePetActivityUseCase(SensorLiveStepSource(appContext), clock)
    }

    /** Today's reached focus goals, for the goal haptic (DD-47). */
    val observeDailyFocusUseCase: ObserveDailyFocusUseCase by lazy { ObserveDailyFocusUseCase(petRepository, clock) }

    val checkCriticalVitalsUseCase: CheckCriticalVitalsUseCase by lazy {
        CheckCriticalVitalsUseCase(petRepository, VitalAlertStateRepositoryImpl.getInstance(appContext), clock)
    }

    val vitalAlertNotifier: VitalAlertNotifier by lazy { VitalAlertNotifier(appContext) }

    val healthServicesManager: HealthServicesManager by lazy { HealthServicesManager(appContext) }

    /** Makes each tap on the tile's water button log exactly once (DD-42). */
    val tileClickLedger: TileClickLedger by lazy { TileClickLedger.create(appContext) }

    /** Requests a Tile re-render and fresh complication data; the system throttles and coalesces frequent requests. */
    private fun requestSurfaceRefresh() {
        TileService.getUpdater(appContext).requestUpdate(PetStatusTileService::class.java)
        PetMoodComplicationService.requestRefresh(appContext)
    }
}

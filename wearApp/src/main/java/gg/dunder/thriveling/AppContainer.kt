// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling

import android.content.Context
import androidx.wear.tiles.TileService
import gg.dunder.thriveling.core.data.db.CompanionDatabase
import gg.dunder.thriveling.core.data.repository.PassiveSyncRepositoryImpl
import gg.dunder.thriveling.core.data.repository.PetRepositoryImpl
import gg.dunder.thriveling.core.data.repository.SettingsRepositoryImpl
import gg.dunder.thriveling.core.data.repository.VitalAlertStateRepositoryImpl
import gg.dunder.thriveling.core.domain.repository.NotifyingPetRepository
import gg.dunder.thriveling.core.domain.repository.NotifyingSettingsRepository
import gg.dunder.thriveling.core.domain.repository.PassiveSyncRepository
import gg.dunder.thriveling.core.domain.repository.PetRepository
import gg.dunder.thriveling.core.domain.repository.SettingsRepository
import gg.dunder.thriveling.core.domain.time.Clock
import gg.dunder.thriveling.core.domain.usecase.CheckCriticalVitalsUseCase
import gg.dunder.thriveling.core.domain.usecase.GetPetStateUseCase
import gg.dunder.thriveling.core.domain.usecase.IngestPassiveDataUseCase
import gg.dunder.thriveling.core.domain.usecase.LogHabitUseCase
import gg.dunder.thriveling.core.domain.usecase.ObserveCareCooldownsUseCase
import gg.dunder.thriveling.core.domain.usecase.ObserveDailyProgressUseCase
import gg.dunder.thriveling.core.domain.usecase.ObservePetActivityUseCase
import gg.dunder.thriveling.core.domain.usecase.ObservePetDetailsUseCase
import gg.dunder.thriveling.core.domain.usecase.StartOverUseCase
import gg.dunder.thriveling.core.health.HealthServicesManager
import gg.dunder.thriveling.core.health.SensorLiveStepSource
import gg.dunder.thriveling.complications.PetMoodComplicationService
import gg.dunder.thriveling.complications.StepGoalComplicationService
import gg.dunder.thriveling.notifications.VitalAlertNotifier
import gg.dunder.thriveling.notifications.VitalAlertWorker
import gg.dunder.thriveling.tiles.PetStatusTileService
import gg.dunder.thriveling.tiles.TileClickLedger

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
        NotifyingPetRepository(PetRepositoryImpl(database, clock, settingsRepository)) {
            requestSurfaceRefresh()
            VitalAlertWorker.requestCheck(appContext)
        }
    }

    /**
     * The user's goals, bedtime and haptics switch (DD-48). A new bedtime changes the mood on the tile and
     * complication and the alerts' quiet hours, so every change refreshes them and re-checks vitals.
     */
    val settingsRepository: SettingsRepository by lazy {
        NotifyingSettingsRepository(SettingsRepositoryImpl.getInstance(appContext)) {
            requestSurfaceRefresh()
            VitalAlertWorker.requestCheck(appContext)
        }
    }

    val passiveSyncRepository: PassiveSyncRepository by lazy { PassiveSyncRepositoryImpl.getInstance(appContext) }

    val getPetStateUseCase: GetPetStateUseCase by lazy { GetPetStateUseCase(petRepository, settingsRepository, clock) }

    val logHabitUseCase: LogHabitUseCase by lazy { LogHabitUseCase(petRepository) }

    /** Deletes the pet and its history for a new one, from Settings (DD-52). */
    val startOverUseCase: StartOverUseCase by lazy { StartOverUseCase(petRepository) }

    val ingestPassiveDataUseCase: IngestPassiveDataUseCase by lazy {
        IngestPassiveDataUseCase(petRepository, passiveSyncRepository, clock)
    }

    /** Live, cosmetic step reactions; the step sensor is held only while the pet screen collects it. */
    val observePetActivityUseCase: ObservePetActivityUseCase by lazy {
        ObservePetActivityUseCase(SensorLiveStepSource(appContext), clock)
    }

    /** Today's reached focus goals against the user's own goals, for the goal haptic (DD-47, DD-48). */
    val observeDailyProgressUseCase: ObserveDailyProgressUseCase by lazy {
        ObserveDailyProgressUseCase(petRepository, settingsRepository, clock)
    }

    /** Which care buttons are in their one-hour cooldown, to dim them (DD-59). */
    val observeCareCooldownsUseCase: ObserveCareCooldownsUseCase by lazy {
        ObserveCareCooldownsUseCase(petRepository, clock)
    }

    val observePetDetailsUseCase: ObservePetDetailsUseCase by lazy {
        ObservePetDetailsUseCase(petRepository, settingsRepository, clock)
    }

    val checkCriticalVitalsUseCase: CheckCriticalVitalsUseCase by lazy {
        CheckCriticalVitalsUseCase(
            petRepository,
            VitalAlertStateRepositoryImpl.getInstance(appContext),
            settingsRepository,
            clock
        )
    }

    val vitalAlertNotifier: VitalAlertNotifier by lazy { VitalAlertNotifier(appContext) }

    val healthServicesManager: HealthServicesManager by lazy { HealthServicesManager(appContext) }

    /** Makes each tap on the tile's water button log exactly once (DD-42). */
    val tileClickLedger: TileClickLedger by lazy { TileClickLedger.create(appContext) }

    /** Requests a Tile re-render and fresh data for both complications; the system throttles and coalesces frequent requests. */
    private fun requestSurfaceRefresh() {
        TileService.getUpdater(appContext).requestUpdate(PetStatusTileService::class.java)
        PetMoodComplicationService.requestRefresh(appContext)
        StepGoalComplicationService.requestRefresh(appContext)
    }
}

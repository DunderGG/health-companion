// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear

import android.content.Context
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.repository.PassiveSyncRepositoryImpl
import com.healthcompanion.core.data.repository.PetRepositoryImpl
import com.healthcompanion.core.domain.repository.PassiveSyncRepository
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.domain.usecase.GetPetStateUseCase
import com.healthcompanion.core.domain.usecase.IngestPassiveDataUseCase
import com.healthcompanion.core.domain.usecase.LogHabitUseCase
import com.healthcompanion.core.health.HealthServicesManager

/**
 * Composition root: the single dependency graph shared by the activity, view models,
 * the passive data service and the tile.
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

    val petRepository: PetRepository by lazy { PetRepositoryImpl(database, clock) }

    val passiveSyncRepository: PassiveSyncRepository by lazy { PassiveSyncRepositoryImpl.getInstance(appContext) }

    val getPetStateUseCase: GetPetStateUseCase by lazy { GetPetStateUseCase(petRepository, clock) }

    val logHabitUseCase: LogHabitUseCase by lazy { LogHabitUseCase(petRepository) }

    val ingestPassiveDataUseCase: IngestPassiveDataUseCase by lazy {
        IngestPassiveDataUseCase(petRepository, passiveSyncRepository, clock)
    }

    val healthServicesManager: HealthServicesManager by lazy { HealthServicesManager(appContext) }
}

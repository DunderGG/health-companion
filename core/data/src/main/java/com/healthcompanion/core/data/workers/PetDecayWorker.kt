// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.repository.PetRepositoryImpl
import com.healthcompanion.core.domain.engine.PetDecayEngine

/**
 * Background worker executing battery-efficient periodic decay computation via Android WorkManager.
 *
 * ### Kotlin vs C++ Note:
 * - **WorkManager & `CoroutineWorker`**: Analogous to an OS daemon or system cron job runner.
 *   `CoroutineWorker` executes [doWork] inside a background coroutine without holding wake locks
 *   or maintaining an open thread pool.
 * - **Result States**: Returns `Result.success()` upon successful database update, or `Result.retry()`
 *   to instruct the OS to reschedule with exponential backoff if an exception occurs.
 *
 * @param context Android context passed by WorkManager runtime.
 * @param params Execution parameters such as run attempt count and input data.
 */
class PetDecayWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /**
     * Executes the background decay calculation:
     * 1. Acquires database instance and repository.
     * 2. Retrieves current pet snapshot.
     * 3. Calculates elapsed time decay.
     * 4. Persists the decayed state back to SQLite.
     *
     * @return [Result.success] if the decay write succeeded; [Result.retry] if an exception occurred.
     */
    override suspend fun doWork(): Result {
        return try {
            val db = CompanionDatabase.getInstance(applicationContext)
            val repository = PetRepositoryImpl(db.petDao())
            val pet = repository.getPet()

            val decayedVitals = PetDecayEngine.calculateDecay(pet.vitals)
            repository.updatePet(pet.copy(vitals = decayedVitals))

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}


// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.repository.PetRepositoryImpl
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.domain.usecase.CalculateDecayUseCase

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
     * applies elapsed time decay to the stored pet and persists it atomically
     * via [CalculateDecayUseCase].
     *
     * @return [Result.success] if the decay write succeeded; [Result.retry] if an exception occurred.
     */
    override suspend fun doWork(): Result {
        return try {
            val db = CompanionDatabase.getInstance(applicationContext)
            CalculateDecayUseCase(PetRepositoryImpl(db, Clock.SYSTEM), Clock.SYSTEM).execute()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}


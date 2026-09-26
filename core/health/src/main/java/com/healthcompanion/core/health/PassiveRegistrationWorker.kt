// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Re-creates the Health Services passive registration, which does not survive a reboot.
 *
 * Runs as a WorkManager job rather than inside the boot receiver: at boot, Health Services may take
 * 10+ seconds to acknowledge a registration, longer than a `BroadcastReceiver` may run.
 */
class PassiveRegistrationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            HealthServicesManager(applicationContext).ensureRegistered(force = true)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Passive re-registration failed; will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PassiveRegistration"
        private const val UNIQUE_WORK_NAME = "PassiveListenerReRegistration"

        /** Enqueues a single (deduplicated) re-registration job. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PassiveRegistrationWorker>().build()
            )
        }
    }
}

/**
 * Receives `BOOT_COMPLETED` and delegates re-registration to [PassiveRegistrationWorker].
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PassiveRegistrationWorker.enqueue(context)
        }
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import gg.dunder.thriveling.ThrivelingApp
import java.util.concurrent.TimeUnit

/**
 * Runs one critical-vital check: posts alerts for newly critical vitals, clears alerts for recovered
 * ones, and schedules the next check at the predicted threshold crossing (DD-40).
 *
 * Scheduling is event-driven, with no periodic work:
 * - [requestCheck] (immediate, `REPLACE`) after every committed pet write, since a write can restore a
 *   vital or change when the next one crosses its threshold.
 * - [ensureScheduled] (immediate, `KEEP`) on process start and whenever the app resumes, so a pending
 *   check is left alone, but a chain stopped for lack of notification permission restarts.
 * - The worker itself appends the next delayed check (`APPEND_OR_REPLACE`). Appending, rather than
 *   replacing, avoids cancelling the worker that is still running. A later `REPLACE` drops the whole chain.
 *
 * WorkManager persists the scheduled check across reboots.
 */
class VitalAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Without permission nothing could be shown, and recording vitals as notified would swallow their
        // alerts. Stop the chain instead; granting the permission or reopening the app starts it again.
        if (!VitalAlertNotifier.canPostNotifications(applicationContext)) {
            Log.d(TAG, "Notifications not permitted; skipping vital check.")
            return Result.success()
        }

        return try {
            val container = (applicationContext as ThrivelingApp).container
            val check = container.checkCriticalVitalsUseCase.execute()
            val notifier = container.vitalAlertNotifier

            check.plan.restored.forEach { notifier.cancel(it) }
            check.plan.newlyCritical.forEach { notifier.show(it, check.petName) }

            check.plan.nextCheckAtMillis?.let { at ->
                val delay = (at - container.clock.nowMillis()).coerceAtLeast(MIN_DELAY_MS)
                enqueue(applicationContext, delay, ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
            Log.d(TAG, "Vital check: $check")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Vital check failed; will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "VitalAlertWorker"
        private const val UNIQUE_WORK_NAME = "CriticalVitalCheck"

        /** Floor for the next delay, so clock skew can never turn the chain into a busy loop. */
        private const val MIN_DELAY_MS = 60_000L

        /** Re-evaluates now, replacing any pending check. Call after every committed pet write. */
        fun requestCheck(context: Context) {
            enqueue(context, delayMillis = 0L, ExistingWorkPolicy.REPLACE)
        }

        /** Evaluates now unless a check is already pending. Call on process start. */
        fun ensureScheduled(context: Context) {
            enqueue(context, delayMillis = 0L, ExistingWorkPolicy.KEEP)
        }

        private fun enqueue(context: Context, delayMillis: Long, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<VitalAlertWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
        }
    }
}

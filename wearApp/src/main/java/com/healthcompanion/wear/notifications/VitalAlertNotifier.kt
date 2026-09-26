// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.healthcompanion.core.domain.engine.CriticalVital
import com.healthcompanion.wear.MainActivity
import com.healthcompanion.wear.R

/**
 * Posts and clears the local "your pet is thirsty / hungry" notifications.
 *
 * One notification per [CriticalVital] with a stable id, so a later check can cancel exactly the
 * notification whose vital has recovered. Tapping it opens the pet screen.
 *
 * @param context Any context; only the application context is retained.
 */
class VitalAlertNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    /** Creates the notification channel. Idempotent; call once per process start. */
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.vital_alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(R.string.vital_alert_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /** Posts the alert for [vital]. Silently does nothing while notifications are not permitted. */
    fun show(vital: CriticalVital, petName: String) {
        if (!canPostNotifications(appContext)) return

        val (title, text) = when (vital) {
            CriticalVital.HYDRATION ->
                appContext.getString(R.string.vital_alert_thirsty_title, petName) to
                    appContext.getString(R.string.vital_alert_thirsty_text)
            CriticalVital.HUNGER ->
                appContext.getString(R.string.vital_alert_hungry_title, petName) to
                    appContext.getString(R.string.vital_alert_hungry_text)
        }

        val openApp = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(vital.notificationId, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    /** Removes the alert for [vital], if shown. */
    fun cancel(vital: CriticalVital) {
        notificationManager.cancel(vital.notificationId)
    }

    private val CriticalVital.notificationId: Int
        get() = NOTIFICATION_ID_BASE + ordinal

    companion object {
        private const val CHANNEL_ID = "vital_alerts"
        private const val NOTIFICATION_ID_BASE = 1_000

        /** Runtime permission needed to post notifications, or `null` below API 33 where none exists. */
        val runtimePermission: String?
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else {
                null
            }

        fun canPostNotifications(context: Context): Boolean {
            val permission = runtimePermission
            val granted = permission == null ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}

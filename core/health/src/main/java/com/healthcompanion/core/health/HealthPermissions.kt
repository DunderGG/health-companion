// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Permission names and checks for Wear OS health sensors, resolved per API level.
 *
 * - **Activity** (`ACTIVITY_RECOGNITION`): steps and floors. This is the *core* permission;
 *   without it the app runs in degraded mode.
 * - **Heart rate** (optional): `BODY_SENSORS` up to API 35, `health.READ_HEART_RATE` from API 36
 *   (Wear OS 6). Passive delivery in the background additionally needs `BODY_SENSORS_BACKGROUND`
 *   (API 33–35) or `health.READ_HEALTH_DATA_IN_BACKGROUND` (API 36+), requested separately after
 *   the foreground permission is granted.
 *
 * ### Kotlin vs C++ Note:
 * - **`Build.VERSION.SDK_INT` branches**: Runtime feature detection, comparable to checking a kernel
 *   or library version before calling a newer API in C++.
 */
object HealthPermissions {

    /** Core permission for steps and floors. */
    const val ACTIVITY_RECOGNITION: String = Manifest.permission.ACTIVITY_RECOGNITION

    /** Foreground heart-rate permission for the running API level. */
    val heartRate: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            android.health.connect.HealthPermissions.READ_HEART_RATE
        } else {
            Manifest.permission.BODY_SENSORS
        }

    /** Background body-sensor permission for the running API level, or `null` where none exists (API < 33). */
    val heartRateBackground: String?
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
                android.health.connect.HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                Manifest.permission.BODY_SENSORS_BACKGROUND
            else -> null
        }

    /** Permissions requested together during onboarding. */
    val foregroundPermissions: Array<String>
        get() = arrayOf(ACTIVITY_RECOGNITION, heartRate)

    /** Whether the core (activity) permission is granted; without it the app is in degraded mode. */
    fun hasCorePermission(context: Context): Boolean = isGranted(context, ACTIVITY_RECOGNITION)

    /** Whether heart rate may be delivered passively, i.e. foreground and (where applicable) background access. */
    fun canReadHeartRateInBackground(context: Context): Boolean {
        val background = heartRateBackground
        return isGranted(context, heartRate) && (background == null || isGranted(context, background))
    }

    /** Whether the separate background heart-rate request should be offered (foreground granted, background missing). */
    fun shouldRequestBackgroundHeartRate(context: Context): Boolean {
        val background = heartRateBackground ?: return false
        return isGranted(context, heartRate) && !isGranted(context, background)
    }

    /** Sensors the current grants allow, before device capability filtering. */
    fun permittedSensors(context: Context): Set<PassiveSensor> {
        return PassiveSensorPlanner.permittedSensors(
            activityRecognitionGranted = hasCorePermission(context),
            heartRateReadableInBackground = canReadHeartRateInBackground(context)
        )
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

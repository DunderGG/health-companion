// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Helper object providing permission constants and validation for Wear OS health hardware sensors.
 *
 * ### Kotlin vs C++ Note:
 * - `REQUIRED_PERMISSIONS.all { ... }`: The `.all` standard library function tests whether every element
 *   satisfies the predicate, identical to `std::all_of(begin, end, pred)` in C++.
 */
object HealthPermissions {

    /**
     * Array of dangerous runtime permissions required to access the watch's PPG sensor and pedometer hardware.
     */
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.BODY_SENSORS
    )

    /**
     * Checks whether all permissions in [REQUIRED_PERMISSIONS] are currently granted by the user.
     *
     * @param context Android context for permission checking.
     * @return `true` if all required permissions are granted; `false` if any permission is denied or revoked.
     */
    fun hasPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}


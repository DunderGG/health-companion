// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.permission

/**
 * Represents the current state of health-sensor runtime permissions.
 */
sealed interface PermissionState {
    /** Initial check is in progress. */
    data object Checking : PermissionState

    /** All required permissions (BODY_SENSORS, ACTIVITY_RECOGNITION) are granted. */
    data object Granted : PermissionState

    /** Permissions have not been requested yet — show the onboarding permission screen. */
    data object Required : PermissionState

    /**
     * User has denied permissions (possibly permanently).
     * App enters degraded mode; PetScreen shows an "Enable sensors" chip
     * that links to system Settings.
     */
    data object Denied : PermissionState
}


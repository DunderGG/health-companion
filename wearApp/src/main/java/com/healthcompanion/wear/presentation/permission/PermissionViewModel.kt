// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.permission

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthcompanion.core.health.HealthPermissions
import com.healthcompanion.core.health.HealthServicesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages runtime permission state for BODY_SENSORS and ACTIVITY_RECOGNITION.
 *
 * Responsible for:
 * - Checking current permission grants on startup and resume.
 * - Processing the result of the system permission dialog.
 * - Triggering Health Services registration after a successful grant.
 */
class PermissionViewModel(
    private val application: Application,
    private val healthServicesManager: HealthServicesManager
) : ViewModel() {

    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.Checking)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    init {
        checkPermissions()
    }

    /**
     * Re-checks permission status. Call on startup and when the Activity resumes
     * (e.g. after returning from system Settings).
     */
    fun checkPermissions() {
        val granted = HealthPermissions.hasPermissions(application)
        if (granted) {
            _permissionState.value = PermissionState.Granted
            registerHealthServicesIfNeeded()
        } else if (_permissionState.value != PermissionState.Denied) {
            // Only move to Required if we haven't already been denied.
            // This prevents resetting a Denied state on resume when
            // the user hasn't actually changed anything in Settings.
            _permissionState.value = PermissionState.Required
        }
    }

    /**
     * Called from the Activity's permission result callback.
     *
     * @param grants Map of permission name → granted boolean from
     *               [ActivityResultContracts.RequestMultiplePermissions].
     * @param shouldShowRationale Lambda that checks
     *        [Activity.shouldShowRequestPermissionRationale] for a given permission.
     *        When false after a denial, the system won't show the dialog again (permanent deny).
     */
    fun onPermissionResult(
        grants: Map<String, Boolean>,
        shouldShowRationale: (String) -> Boolean
    ) {
        val allGranted = grants.values.all { it }

        if (allGranted) {
            _permissionState.value = PermissionState.Granted
            registerHealthServicesIfNeeded()
        } else {
            // Check if any denied permission can no longer show rationale —
            // that means the user selected "Don't ask again" (permanent denial).
            val permanentlyDenied = grants
                .filter { !it.value }
                .any { !shouldShowRationale(it.key) }

            if (permanentlyDenied) {
                _permissionState.value = PermissionState.Denied
            } else {
                // User denied but can still be asked again on next attempt.
                _permissionState.value = PermissionState.Denied
            }
        }
    }

    private fun registerHealthServicesIfNeeded() {
        viewModelScope.launch {
            healthServicesManager.tryRegisterPassiveDataService()
        }
    }
}


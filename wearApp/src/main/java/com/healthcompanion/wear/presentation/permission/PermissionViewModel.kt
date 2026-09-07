// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.permission

import android.app.Application
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
            if (_permissionState.value != PermissionState.Granted) {
                _permissionState.value = PermissionState.Granted
                registerHealthServicesIfNeeded()
            }
        } else when (_permissionState.value) {
            // Initial check — permissions haven't been requested yet.
            PermissionState.Checking -> _permissionState.value = PermissionState.Required

            // Permissions were previously granted but have since been revoked
            // (e.g. user toggled them off in system Settings). Move to Denied
            // so the degraded-mode chip appears.
            PermissionState.Granted -> _permissionState.value = PermissionState.Denied

            // Already in Required or Denied — no change needed.
            // The onPermissionResult callback handles transitions after requests.
            else -> { /* no-op */ }
        }
    }

    /**
     * Called from the Activity's permission result callback.
     *
     * @param grants Map of permission name → granted boolean from
     *               [ActivityResultContracts.RequestMultiplePermissions].
     */
    fun onPermissionResult(grants: Map<String, Boolean>) {
        val allGranted = grants.values.all { it }

        if (allGranted) {
            _permissionState.value = PermissionState.Granted
            registerHealthServicesIfNeeded()
            return
        }

        // The grants map from the callback can disagree with the actual
        // permission state on Wear OS emulators and some devices (e.g.
        // permission group mechanics granting one permission implicitly).
        // Verify ground truth before committing to Denied.
        if (HealthPermissions.hasPermissions(application)) {
            _permissionState.value = PermissionState.Granted
            registerHealthServicesIfNeeded()
            return
        }

        _permissionState.value = PermissionState.Denied
    }

    private fun registerHealthServicesIfNeeded() {
        viewModelScope.launch {
            healthServicesManager.tryRegisterPassiveDataService()
        }
    }
}


// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.permission

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthcompanion.core.health.HealthPermissions
import com.healthcompanion.core.health.HealthServicesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages runtime permission state for activity recognition (core) and heart rate (optional).
 *
 * Responsible for:
 * - Checking current permission grants on startup and resume.
 * - Processing the result of the system permission dialog.
 * - Triggering Health Services registration after a successful grant.
 *
 * ### Kotlin vs C++ Note:
 * - **`MutableStateFlow` vs `StateFlow` Encapsulation**:
 *   A `MutableStateFlow` is a thread-safe observable state container (like an atomic variable with pub-sub).
 *   Kotlin architectures use the backing property pattern:
 *   `private val _permissionState = MutableStateFlow(...)` (read-write, internal to ViewModel)
 *   `val permissionState: StateFlow = _permissionState.asStateFlow()` (read-only view for UI observers).
 *   This prevents external UI code from modifying state directly, matching C++ const-reference exposure.
 *
 * @param application Android application context used for permission querying.
 * @param healthServicesManager Manager for registering sensor listener upon permission grant.
 */
class PermissionViewModel(
    private val application: Application,
    private val healthServicesManager: HealthServicesManager
) : ViewModel() {

    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.Checking)

    /**
     * Read-only public [StateFlow] observed by the Activity / Compose UI layer.
     */
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    /** Ensures the optional background heart-rate request is offered at most once per session. */
    private var backgroundHeartRateOffered = false

    init {
        checkPermissions()
    }

    /**
     * Re-checks permission status. Call on startup and when the Activity resumes
     * (e.g. after returning from system Settings). Always re-syncs the passive registration,
     * which is a cheap no-op when the permitted sensor set is unchanged.
     */
    fun checkPermissions() {
        if (HealthPermissions.hasCorePermission(application)) {
            _permissionState.value = PermissionState.Granted
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
        syncRegistration()
    }

    /**
     * Called from the Activity's result callback for [HealthPermissions.foregroundPermissions].
     *
     * Only the core activity permission decides between full and degraded mode; heart rate is optional.
     * The grants map from the callback can disagree with the actual permission state on Wear OS
     * emulators and some devices (permission group mechanics), so ground truth is re-checked.
     *
     * @param grants Map of permission name → granted boolean from
     *               [androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions].
     */
    fun onPermissionResult(@Suppress("UNUSED_PARAMETER") grants: Map<String, Boolean>) {
        _permissionState.value = if (HealthPermissions.hasCorePermission(application)) {
            PermissionState.Granted
        } else {
            PermissionState.Denied
        }
        syncRegistration()
    }

    /**
     * Returns the background heart-rate permission to request next, or `null` if it should not be
     * requested (not applicable on this API level, foreground heart rate denied, already granted,
     * or already offered this session). Android requires this request to follow the foreground grant.
     */
    fun consumeBackgroundHeartRateRequest(): String? {
        if (backgroundHeartRateOffered || !HealthPermissions.shouldRequestBackgroundHeartRate(application)) {
            return null
        }
        backgroundHeartRateOffered = true
        return HealthPermissions.heartRateBackground
    }

    /** Called after the background heart-rate request completes, whatever the outcome. */
    fun onBackgroundHeartRateResult() {
        syncRegistration()
    }

    private fun syncRegistration() {
        viewModelScope.launch {
            try {
                healthServicesManager.ensureRegistered()
            } catch (e: Exception) {
                Log.w(TAG, "Passive registration failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "PermissionViewModel"
    }
}

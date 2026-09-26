// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material3.CircularProgressIndicator
import com.healthcompanion.core.health.HealthPermissions
import com.healthcompanion.core.ui.theme.HealthCompanionTheme
import com.healthcompanion.wear.notifications.VitalAlertNotifier
import com.healthcompanion.wear.notifications.VitalAlertWorker
import com.healthcompanion.wear.presentation.ambient.AmbientState
import com.healthcompanion.wear.presentation.permission.PermissionScreen
import com.healthcompanion.wear.presentation.permission.PermissionState
import com.healthcompanion.wear.presentation.permission.PermissionViewModel
import com.healthcompanion.wear.presentation.pet.PetPager
import com.healthcompanion.wear.presentation.pet.PetViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Main activity and single-screen host for the Wear OS companion application.
 *
 * ### Kotlin vs C++ Note:
 * - **Property Delegation (`by viewModels { factory }`)**: Kotlin's `by` keyword delegates property access
 *   to an underlying delegate instance. `viewModels` retains the [PetViewModel] across Activity recreation
 *   events (such as orientation/theme changes) by retrieving it from Android's ViewModelStore.
 * - **Anonymous Classes (`object : ViewModelProvider.Factory`)**: Kotlin's `object : Interface` syntax
 *   creates an anonymous object implementing an interface on the fly, similar to declaring a local C++
 *   struct that inherits an abstract class and instantiating it inline.
 * - **Type Casting (`as T`)**: Unchecked downcasting equivalent to `static_cast<T*>` in C++.
 * - **Reactive Activity Flow**: Observes [PermissionViewModel.permissionState] and routes between
 *   permission onboarding ([PermissionScreen]) and the pet UI ([PetPager]: pet and vitals pages).
 * - **Ambient (always-on) mode**: [AmbientLifecycleObserver] keeps the activity on screen when the watch
 *   dims, instead of returning to the watch face. Its callbacks feed [ambientState] (rendering) and
 *   [PetViewModel] (sensor release, once-a-minute refresh). See DD-44.
 */
class MainActivity : ComponentActivity() {

    /**
     * Companion game view model managing pet state, petting interaction cooldown, and habit dispatch.
     */
    private val petViewModel: PetViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val container = (application as HealthCompanionApp).container
                return PetViewModel(
                    getPetStateUseCase = container.getPetStateUseCase,
                    logHabitUseCase = container.logHabitUseCase,
                    observePetActivityUseCase = container.observePetActivityUseCase,
                    clock = container.clock
                ) as T
            }
        }
    }

    /**
     * View model managing sensor runtime permissions and Health Services registration.
     */
    private val permissionViewModel: PermissionViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as HealthCompanionApp
                return PermissionViewModel(
                    application = app,
                    healthServicesManager = app.container.healthServicesManager
                ) as T
            }
        }
    }

    /** Interactive or ambient; read by [PetPager] to switch to its low-power look. */
    private val ambientState = MutableStateFlow<AmbientState>(AmbientState.Interactive)

    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambientState.value = AmbientState.Ambient(
                    burnInProtectionRequired = ambientDetails.burnInProtectionRequired,
                    lowBitAmbient = ambientDetails.deviceHasLowBitAmbient
                )
                petViewModel.setAmbient(true)
            }

            override fun onUpdateAmbient() {
                ambientState.update { state ->
                    if (state is AmbientState.Ambient) state.copy(updateCount = state.updateCount + 1) else state
                }
                petViewModel.onAmbientUpdate()
            }

            override fun onExitAmbient() {
                ambientState.value = AmbientState.Interactive
                petViewModel.setAmbient(false)
            }
        }
    )

    /**
     * Initializes activity lifecycle, binds permission observers, and sets up Compose UI content tree.
     *
     * @param savedInstanceState Saved instance state bundle if recreating after process death.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)

        // Re-check permissions when returning from system Settings
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionViewModel.checkPermissions()
                // Restarts vital alerts if notifications were just enabled in Settings.
                VitalAlertWorker.ensureScheduled(this)
            }
        })

        setContent {
            HealthCompanionTheme {
                val permState by permissionViewModel.permissionState.collectAsState()
                val ambient by ambientState.collectAsState()

                // Optional second step: background heart-rate access must be requested separately,
                // after the foreground heart-rate permission has been granted.
                val backgroundHeartRateLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    permissionViewModel.onBackgroundHeartRateResult()
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    permissionViewModel.onPermissionResult(grants)
                    VitalAlertWorker.requestCheck(this)
                    permissionViewModel.consumeBackgroundHeartRateRequest()?.let { permission ->
                        backgroundHeartRateLauncher.launch(permission)
                    }
                }

                when (permState) {
                    is PermissionState.Checking -> {
                        CircularProgressIndicator()
                    }

                    is PermissionState.Required -> {
                        PermissionScreen(
                            onRequestPermission = {
                                // Notifications are optional and asked for in the same flow (DD-41).
                                permissionLauncher.launch(
                                    HealthPermissions.foregroundPermissions +
                                        listOfNotNull(VitalAlertNotifier.runtimePermission)
                                )
                            }
                        )
                    }

                    is PermissionState.Granted -> {
                        PetPager(
                            viewModel = petViewModel,
                            ambientState = ambient,
                            showSensorChip = false
                        )
                    }

                    is PermissionState.Denied -> {
                        PetPager(
                            viewModel = petViewModel,
                            ambientState = ambient,
                            showSensorChip = true,
                            onSensorChipClick = {
                                // Open app-specific system Settings so user can
                                // manually grant permissions after permanent denial.
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", packageName, null)
                                )
                                startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

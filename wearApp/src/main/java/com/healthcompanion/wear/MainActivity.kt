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
import androidx.wear.compose.material3.CircularProgressIndicator
import com.healthcompanion.core.health.HealthPermissions
import com.healthcompanion.core.ui.theme.HealthCompanionTheme
import com.healthcompanion.wear.presentation.permission.PermissionScreen
import com.healthcompanion.wear.presentation.permission.PermissionState
import com.healthcompanion.wear.presentation.permission.PermissionViewModel
import com.healthcompanion.wear.presentation.pet.PetScreen
import com.healthcompanion.wear.presentation.pet.PetViewModel

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
 *   permission onboarding ([PermissionScreen]) and the interactive pet UI ([PetScreen]).
 */
class MainActivity : ComponentActivity() {

    /**
     * Companion game view model managing pet state, petting interaction cooldown, and habit dispatch.
     */
    private val petViewModel: PetViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = HealthCompanionApp.instance
                return PetViewModel(
                    getPetStateUseCase = app.getPetStateUseCase,
                    logHabitUseCase = app.logHabitUseCase
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
                val app = HealthCompanionApp.instance
                return PermissionViewModel(
                    application = app,
                    healthServicesManager = app.healthServicesManager
                ) as T
            }
        }
    }

    /**
     * Initializes activity lifecycle, binds permission observers, and sets up Compose UI content tree.
     *
     * @param savedInstanceState Saved instance state bundle if recreating after process death.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Re-check permissions when returning from system Settings
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionViewModel.checkPermissions()
            }
        })

        setContent {
            HealthCompanionTheme {
                val permState by permissionViewModel.permissionState.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    permissionViewModel.onPermissionResult(grants)
                }

                when (permState) {
                    is PermissionState.Checking -> {
                        CircularProgressIndicator()
                    }

                    is PermissionState.Required -> {
                        PermissionScreen(
                            onRequestPermission = {
                                permissionLauncher.launch(HealthPermissions.REQUIRED_PERMISSIONS)
                            }
                        )
                    }

                    is PermissionState.Granted -> {
                        PetScreen(
                            viewModel = petViewModel,
                            showSensorChip = false
                        )
                    }

                    is PermissionState.Denied -> {
                        PetScreen(
                            viewModel = petViewModel,
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

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

class MainActivity : ComponentActivity() {

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

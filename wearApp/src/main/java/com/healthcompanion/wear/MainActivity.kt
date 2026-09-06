// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.healthcompanion.core.ui.theme.HealthCompanionTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthCompanionTheme {
                PetScreen(viewModel = petViewModel)
            }
        }
    }
}


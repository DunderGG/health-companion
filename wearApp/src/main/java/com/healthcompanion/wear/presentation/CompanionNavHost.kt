// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.foundation.LocalSwipeToDismissBackgroundScrimColor
import androidx.wear.compose.foundation.LocalSwipeToDismissContentScrimColor
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.healthcompanion.wear.presentation.ambient.AmbientState
import com.healthcompanion.wear.presentation.pet.PetPager
import com.healthcompanion.wear.presentation.pet.PetScreen
import com.healthcompanion.wear.presentation.pet.PetViewModel
import com.healthcompanion.wear.presentation.settings.SettingField
import com.healthcompanion.wear.presentation.settings.SettingStepperScreen
import com.healthcompanion.wear.presentation.settings.SettingsScreen
import com.healthcompanion.wear.presentation.settings.SettingsViewModel

private const val ROUTE_PET = "pet"
private const val ROUTE_SETTINGS = "settings"

/**
 * The app's screens once permissions are settled: the [PetPager], and the settings screens it opens
 * (DD-48). Swiping right or pressing back returns to the previous screen.
 *
 * In ambient mode every screen shows the ambient pet instead (DD-44), so a settings screen left open
 * never stays bright on an always-on display. The back stack is kept, so the user returns to where they
 * were.
 *
 * @param petViewModel Drives the pet pager and the ambient pet.
 * @param settingsViewModel Drives the settings screens.
 * @param ambientState Interactive, or ambient with its display details.
 * @param showSensorChip Passed to [PetPager].
 * @param onSensorChipClick Passed to [PetPager].
 */
@Composable
fun CompanionNavHost(
    petViewModel: PetViewModel,
    settingsViewModel: SettingsViewModel,
    ambientState: AmbientState,
    showSensorChip: Boolean,
    onSensorChipClick: () -> Unit = {}
) {
    val navController = rememberSwipeDismissableNavController()

    // Material 3 paints swiped screens in the theme background; keep them pure black as before, which is
    // cheapest on OLED and required in ambient mode (DD-44).
    CompositionLocalProvider(
        LocalSwipeToDismissBackgroundScrimColor provides Color.Black,
        LocalSwipeToDismissContentScrimColor provides Color.Black
    ) {
        SwipeDismissableNavHost(navController = navController, startDestination = ROUTE_PET) {
            composable(ROUTE_PET) {
                PetPager(
                    viewModel = petViewModel,
                    showSensorChip = showSensorChip,
                    onSensorChipClick = onSensorChipClick,
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    ambientState = ambientState
                )
            }

            composable(ROUTE_SETTINGS) {
                AmbientAware(petViewModel, ambientState) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onEditField = { field -> navController.navigate(field.route) }
                    )
                }
            }

            SettingField.entries.forEach { field ->
                composable(field.route) {
                    AmbientAware(petViewModel, ambientState) {
                        SettingStepperScreen(field = field, viewModel = settingsViewModel)
                    }
                }
            }
        }
    }
}

/** [content], or the ambient pet while the watch is in ambient mode. */
@Composable
private fun AmbientAware(petViewModel: PetViewModel, ambientState: AmbientState, content: @Composable () -> Unit) {
    if (ambientState.displayMode.isAmbient) {
        PetScreen(viewModel = petViewModel, ambientState = ambientState)
    } else {
        content()
    }
}

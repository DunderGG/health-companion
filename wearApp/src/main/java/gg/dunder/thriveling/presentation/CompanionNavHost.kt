// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.foundation.LocalSwipeToDismissBackgroundScrimColor
import androidx.wear.compose.foundation.LocalSwipeToDismissContentScrimColor
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import gg.dunder.thriveling.presentation.ambient.AmbientState
import gg.dunder.thriveling.presentation.pet.PetDetailsScreen
import gg.dunder.thriveling.presentation.pet.PetDetailsViewModel
import gg.dunder.thriveling.presentation.pet.PetPager
import gg.dunder.thriveling.presentation.pet.PetScreen
import gg.dunder.thriveling.presentation.pet.PetViewModel
import gg.dunder.thriveling.presentation.settings.SettingField
import gg.dunder.thriveling.presentation.settings.SettingStepperScreen
import gg.dunder.thriveling.presentation.settings.SettingsScreen
import gg.dunder.thriveling.presentation.settings.SettingsViewModel

private const val ROUTE_PET = "pet"
private const val ROUTE_PET_DETAILS = "pet_details"
private const val ROUTE_SETTINGS = "settings"

/**
 * The app's screens once permissions are settled: the [PetPager], and the pet details (DD-53) and settings
 * screens it opens (DD-48). Swiping right or pressing back returns to the previous screen.
 *
 * In ambient mode every screen shows the ambient pet instead (DD-44), so a settings screen left open
 * never stays bright on an always-on display. The back stack is kept, so the user returns to where they
 * were.
 *
 * @param petViewModel Drives the pet pager and the ambient pet.
 * @param petDetailsViewModel Drives the pet details screen.
 * @param settingsViewModel Drives the settings screens.
 * @param ambientState Interactive, or ambient with its display details.
 * @param showSensorChip Passed to [PetPager].
 * @param onSensorChipClick Passed to [PetPager].
 */
@Composable
fun CompanionNavHost(
    petViewModel: PetViewModel,
    petDetailsViewModel: PetDetailsViewModel,
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
                    onOpenDetails = { navController.navigate(ROUTE_PET_DETAILS) },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    ambientState = ambientState
                )
            }

            composable(ROUTE_PET_DETAILS) {
                AmbientAware(petViewModel, ambientState) {
                    PetDetailsScreen(viewModel = petDetailsViewModel)
                }
            }

            composable(ROUTE_SETTINGS) {
                AmbientAware(petViewModel, ambientState) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onEditField = { field -> navController.navigate(field.route) },
                        // Show the new pet: a fresh pet destination also starts the pager on its first page.
                        onStartedOver = {
                            navController.navigate(ROUTE_PET) { popUpTo(ROUTE_PET) { inclusive = true } }
                        }
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

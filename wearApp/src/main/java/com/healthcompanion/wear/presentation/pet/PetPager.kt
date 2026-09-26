// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.VerticalPagerScaffold
import com.healthcompanion.wear.haptics.PetHaptics
import com.healthcompanion.wear.presentation.ambient.AmbientState

/** Pages of [PetPager], top to bottom. */
private const val PAGE_PET = 0
private const val PAGE_VITALS = 1
private const val PAGE_COUNT = 2

/**
 * The pet screen and the vitals breakdown as a vertical pager (DD-45).
 *
 * Turning the crown snaps between the pages, with haptic ticks: `VerticalPagerScaffold`'s default rotary
 * behaviour is a pager snap. Swiping up or down does the same. A vertical page indicator shows where the
 * user is.
 *
 * In ambient mode only the ambient pet is shown, whichever page was open (DD-44). The pager state is
 * remembered here, outside that switch, so the user returns to the same page afterwards.
 *
 * @param viewModel Shared by both pages.
 * @param showSensorChip Passed to [PetScreen].
 * @param onSensorChipClick Passed to [PetScreen].
 * @param ambientState Interactive, or ambient with its display details.
 */
@Composable
fun PetPager(
    viewModel: PetViewModel,
    modifier: Modifier = Modifier,
    showSensorChip: Boolean = false,
    onSensorChipClick: () -> Unit = {},
    ambientState: AmbientState = AmbientState.Interactive
) {
    val pagerState = rememberPagerState(initialPage = PAGE_PET) { PAGE_COUNT }

    // Vibration patterns (DD-47), collected only while the pet UI is started, so nothing plays in the
    // background and nothing that happened while the app was closed is replayed on opening it.
    val context = LocalContext.current
    val haptics = remember { PetHaptics(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.hapticEvents.collect(haptics::play)
        }
    }

    if (ambientState.displayMode.isAmbient) {
        PetScreen(viewModel = viewModel, modifier = modifier, ambientState = ambientState)
        return
    }

    VerticalPagerScaffold(pagerState = pagerState, modifier = modifier) { page ->
        when (page) {
            PAGE_PET -> PetScreen(
                viewModel = viewModel,
                showSensorChip = showSensorChip,
                onSensorChipClick = onSensorChipClick
            )
            PAGE_VITALS -> VitalsScreen(viewModel = viewModel)
        }
    }
}

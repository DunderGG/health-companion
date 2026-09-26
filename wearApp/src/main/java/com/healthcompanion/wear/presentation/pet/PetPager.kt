// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.VerticalPagerScaffold
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

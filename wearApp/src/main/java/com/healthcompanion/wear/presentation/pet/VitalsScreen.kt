// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.healthcompanion.wear.R

/**
 * Second page of the pet pager: every vital with its value and a bar, reached by turning the crown
 * or swiping up from the pet (DD-45).
 *
 * The rows are a fixed column rather than a scrolling list, so the crown only ever moves between pages.
 *
 * @param viewModel The same [PetViewModel] as the pet page, so both pages show the same snapshot.
 * @param modifier Compose layout modifier applied to the root container.
 */
@Composable
fun VitalsScreen(
    viewModel: PetViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is PetUiState.Loading -> CircularProgressIndicator()
            is PetUiState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Wide side padding keeps the top and bottom rows inside a round display.
                    .padding(horizontal = 30.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.vitals_overall, state.pet.name, overallPercent(state.pet.vitals)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                vitalsBreakdown(state.pet.vitals).forEach { line -> VitalRow(line) }
            }
        }
    }
}

@Composable
private fun VitalRow(line: VitalLine) {
    val label = stringResource(line.labelRes)
    val description = stringResource(R.string.vital_row_description, label, line.percent)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${line.percent}%",
                style = MaterialTheme.typography.labelSmall,
                color = line.color
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Track at 20 % alpha under the filled part, like the arcs of the ring.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(line.color.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(line.percent / 100f)
                    .fillMaxHeight()
                    .background(line.color)
            )
        }
    }
}

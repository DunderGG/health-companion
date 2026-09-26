// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.healthcompanion.wear.R
import java.text.NumberFormat

/**
 * Third page of the pet pager: today's progress towards each daily goal (DD-49), so the goal vibration
 * always matches something on screen. The targets are the user's own (DD-48).
 *
 * Like the Vitals page, the rows are a fixed column, so the crown only ever moves between pages.
 *
 * @param viewModel The same [PetViewModel] as the other pages.
 * @param modifier Compose layout modifier applied to the root container.
 */
@Composable
fun GoalsScreen(
    viewModel: PetViewModel,
    modifier: Modifier = Modifier
) {
    val progress by viewModel.dailyProgress.collectAsStateWithLifecycle()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val current = progress
        if (current == null) {
            CircularProgressIndicator()
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Wide side padding keeps the top and bottom rows inside a round display.
                .padding(horizontal = 30.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.goals_title, current.reached.size, GOAL_COUNT),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            goalsBreakdown(current).forEach { line -> GoalRow(line) }
        }
    }
}

/** Cardio, strength and nourishment. */
private const val GOAL_COUNT = 3

@Composable
private fun GoalRow(line: GoalLine) {
    val label = stringResource(line.labelRes)
    val numbers = NumberFormat.getIntegerInstance()
    val current = numbers.format(line.current)
    val target = numbers.format(line.target)
    // A reached goal shows only today's total, so the row stays short on small screens.
    val value = when (line.unit) {
        GoalUnit.COUNT ->
            if (line.isReached) stringResource(R.string.goal_reached, current)
            else stringResource(R.string.goal_progress, current, target)
        GoalUnit.MILLILITERS ->
            if (line.isReached) stringResource(R.string.goal_reached_ml, current)
            else stringResource(R.string.goal_progress_ml, current, target)
        GoalUnit.DONE -> stringResource(if (line.isReached) R.string.goal_done else R.string.goal_not_yet)
    }

    ProgressRow(
        label = label,
        value = value,
        fraction = line.fraction,
        color = line.color,
        description = stringResource(R.string.goal_row_description, label, value)
    )
}

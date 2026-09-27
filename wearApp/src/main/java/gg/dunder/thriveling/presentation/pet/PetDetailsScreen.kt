// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.presentation.pet

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import gg.dunder.thriveling.core.domain.engine.DailyProgress
import gg.dunder.thriveling.core.domain.engine.GoalTally
import gg.dunder.thriveling.core.model.EvolutionStage
import gg.dunder.thriveling.core.model.Pet
import gg.dunder.thriveling.core.model.PetArchetype
import gg.dunder.thriveling.core.ui.theme.ElectricPurple
import gg.dunder.thriveling.core.ui.theme.HealthyGreen
import gg.dunder.thriveling.core.ui.theme.SunsetOrange
import gg.dunder.thriveling.R
import java.text.NumberFormat

/**
 * One daily focus goal on the details screen, named and coloured as on the goals page (DD-49).
 *
 * @property area The archetype the goal counts towards.
 * @property labelRes Goal name.
 * @property color Accent of the vital the goal feeds.
 */
private data class FocusGoal(val area: PetArchetype, @param:StringRes val labelRes: Int, val color: Color)

private val FOCUS_GOALS = listOf(
    FocusGoal(PetArchetype.CARDIO_RUNNER, R.string.goal_steps, HealthyGreen),
    FocusGoal(PetArchetype.IRON_BEAST, R.string.goal_strength, ElectricPurple),
    FocusGoal(PetArchetype.ZEN_SAGE, R.string.details_goal_nourishment, SunsetOrange)
)

/**
 * The pet details screen (DD-53), opened by the "i" button on the pet screen: its level, stage and XP, its archetype,
 * and how many days reached each daily focus goal, over the last week and since it was born. The crown
 * scrolls the list; swiping right returns to the pet.
 *
 * @param viewModel Provides the pet and its goal record.
 * @param modifier Compose layout modifier applied to the root container.
 */
@Composable
fun PetDetailsScreen(
    viewModel: PetDetailsViewModel,
    modifier: Modifier = Modifier
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        val current = details
        if (current == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@ScreenScaffold
        }
        val pet = current.pet
        val history = current.goalHistory

        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ListHeader {
                    Text(
                        text = pet.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.details_level_stage, pet.stage.level, stringResource(pet.stage.labelRes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            item { XpRow(pet) }

            item { ListSubHeader { Text(stringResource(R.string.details_archetype)) } }
            item { ArchetypeCard(pet) }

            if (history.sinceBirth.days == 1) {
                // A pet born today: "1 / 1 day" says little, so show how close each goal is instead. "Since
                // birth" would repeat today, so it waits until tomorrow.
                item { ListSubHeader { Text(stringResource(R.string.details_today)) } }
                items(FOCUS_GOALS) { goal -> GoalTodayRow(goal, history.today) }
            } else {
                val weekDays = history.lastWeek.days
                item { ListSubHeader { Text(pluralStringResource(R.plurals.details_last_days, weekDays, weekDays)) } }
                items(FOCUS_GOALS) { goal -> GoalDaysRow(goal, history.lastWeek) }

                item { ListSubHeader { Text(stringResource(R.string.details_since_birth)) } }
                items(FOCUS_GOALS) { goal -> GoalDaysRow(goal, history.sinceBirth) }
            }
        }
    }
}

/** XP towards the next stage, or the total at the last stage. */
@Composable
private fun XpRow(pet: Pet) {
    val numbers = NumberFormat.getIntegerInstance()
    val label = stringResource(R.string.details_xp)
    val next = EvolutionStage.entries.getOrNull(pet.stage.ordinal + 1)
    val (value, fraction) = if (next == null) {
        stringResource(R.string.details_xp_max, numbers.format(pet.experiencePoints)) to 1f
    } else {
        val stageStart = pet.stage.requiredXp
        stringResource(R.string.details_xp_progress, numbers.format(pet.experiencePoints), numbers.format(next.requiredXp)) to
            (pet.experiencePoints - stageStart).toFloat() / (next.requiredXp - stageStart)
    }

    ProgressRow(
        label = label,
        value = value,
        fraction = fraction,
        color = MaterialTheme.colorScheme.primary,
        description = stringResource(R.string.goal_row_description, label, value),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** The archetype and what it means; before the Teen stage, when it is still to be chosen. */
@Composable
private fun ArchetypeCard(pet: Pet) {
    val isChosen = pet.stage.level >= EvolutionStage.TEEN.level
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isChosen) pet.archetype.title else stringResource(R.string.details_archetype_pending),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (isChosen) pet.archetype.description else stringResource(R.string.details_archetype_pending_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** How close [goal] is to being reached today, as a percentage and a bar. */
@Composable
private fun GoalTodayRow(goal: FocusGoal, today: DailyProgress) {
    val label = stringResource(goal.labelRes)
    val fraction = today.fraction(goal.area)
    val value = NumberFormat.getPercentInstance().format(fraction)

    ProgressRow(
        label = label,
        value = value,
        fraction = fraction,
        color = goal.color,
        description = stringResource(R.string.goal_row_description, label, value),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Days in [tally] that reached [goal], as a row with a bar. */
@Composable
private fun GoalDaysRow(goal: FocusGoal, tally: GoalTally) {
    val label = stringResource(goal.labelRes)
    val reached = tally.daysReached(goal.area)
    val value = pluralStringResource(R.plurals.details_goal_days, tally.days, reached, tally.days)

    ProgressRow(
        label = label,
        value = value,
        fraction = reached.toFloat() / tally.days,
        color = goal.color,
        description = stringResource(R.string.goal_row_description, label, value),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LevelIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.healthcompanion.core.domain.settings.UserSettings
import com.healthcompanion.wear.R
import java.text.NumberFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Crown rotation, in scroll pixels, that moves a stepper by one value. */
private const val ROTARY_PIXELS_PER_STEP = 60f

private val GOAL_FIELDS = listOf(SettingField.STEPS, SettingField.WATER_ML, SettingField.HEALTHY_MEALS)
private val BEDTIME_FIELDS = listOf(SettingField.BEDTIME_START, SettingField.BEDTIME_END)

/**
 * The settings list (DD-48): daily goals, bedtime and the haptics switch. Tapping a number opens its
 * [SettingStepperScreen]. The crown scrolls the list.
 *
 * @param viewModel Holds and saves the settings.
 * @param onEditField Opens the stepper for a field.
 * @param modifier Compose layout modifier applied to the root container.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onEditField: (SettingField) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState, modifier = modifier) { contentPadding ->
        val current = settings
        if (current == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@ScreenScaffold
        }

        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item { ListHeader { Text(stringResource(R.string.settings_title)) } }

            item { ListSubHeader { Text(stringResource(R.string.settings_daily_goals)) } }
            items(GOAL_FIELDS) { field -> SettingButton(field, current, onEditField) }

            item { ListSubHeader { Text(stringResource(R.string.settings_bedtime)) } }
            items(BEDTIME_FIELDS) { field -> SettingButton(field, current, onEditField) }

            item {
                SwitchButton(
                    checked = current.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    secondaryLabel = { Text(stringResource(R.string.settings_haptics_description)) },
                    label = { Text(stringResource(R.string.settings_haptics)) }
                )
            }
        }
    }
}

@Composable
private fun SettingButton(field: SettingField, settings: UserSettings, onEditField: (SettingField) -> Unit) {
    FilledTonalButton(
        onClick = { onEditField(field) },
        modifier = Modifier.fillMaxWidth(),
        secondaryLabel = { Text(formatSetting(field, field.valueIn(settings))) },
        label = { Text(stringResource(field.labelRes)) }
    )
}

/**
 * Edits one numeric setting with − / + buttons or the crown (DD-48). Each change is saved at once; swiping
 * back returns to the list.
 *
 * @param field The setting to edit.
 * @param viewModel Holds and saves the settings.
 * @param modifier Compose layout modifier applied to the root container.
 */
@Composable
fun SettingStepperScreen(
    field: SettingField,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stored = settings ?: run {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // The screen's own copy of the position, so fast taps and crown turns never step from a value that
    // hasn't been saved yet.
    var index by remember(field) { mutableIntStateOf(field.indexOf(field.valueIn(stored))) }
    val lastIndex = field.options.lastIndex
    val setIndex = { newIndex: Int ->
        val clamped = newIndex.coerceIn(0, lastIndex)
        if (clamped != index) {
            index = clamped
            viewModel.setValue(field, field.options[clamped])
        }
    }

    val focusRequester = remember { FocusRequester() }
    var rotaryPixels by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                rotaryPixels += event.verticalScrollPixels
                val steps = (rotaryPixels / ROTARY_PIXELS_PER_STEP).toInt()
                if (steps != 0) {
                    rotaryPixels -= steps * ROTARY_PIXELS_PER_STEP
                    setIndex(index + steps)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        Stepper(
            value = index,
            onValueChange = setIndex,
            valueProgression = 0..lastIndex,
            decreaseIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_remove),
                    contentDescription = stringResource(R.string.settings_decrease)
                )
            },
            increaseIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.settings_increase)
                )
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(field.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = formatSetting(field, field.options[index]),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        LevelIndicator(
            value = { index },
            valueProgression = 0..lastIndex,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}

/** [value] of [field] as shown to the user, e.g. "6,000", "1,500 ml" or "22:00" / "10:00 PM". */
@Composable
private fun formatSetting(field: SettingField, value: Int): String = when (field) {
    SettingField.STEPS -> NumberFormat.getIntegerInstance().format(value)
    SettingField.WATER_ML ->
        stringResource(R.string.settings_value_water_ml, NumberFormat.getIntegerInstance().format(value))
    SettingField.HEALTHY_MEALS -> NumberFormat.getIntegerInstance().format(value)
    SettingField.BEDTIME_START, SettingField.BEDTIME_END -> formatHour(LocalContext.current, value)
}

/** A whole hour in the user's 12- or 24-hour format. */
private fun formatHour(context: Context, hour: Int): String {
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hma"
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
    return DateTimeFormatter.ofPattern(pattern).format(LocalTime.of(hour, 0))
}

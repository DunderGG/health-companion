// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.settings

import androidx.annotation.StringRes
import com.healthcompanion.core.domain.engine.NightWindow
import com.healthcompanion.core.domain.settings.DailyGoals
import com.healthcompanion.core.domain.settings.UserSettings
import com.healthcompanion.wear.R

/**
 * A numeric setting edited with a stepper (DD-48): which values it offers, and how to read and write it.
 *
 * The stepper moves through [options] by index, so the bedtime hours can wrap past midnight (23:00 is
 * followed by 00:00). The bedtime and wake-up options don't overlap, so the night can never be empty.
 *
 * @property route Navigation route of the field's stepper screen.
 * @property labelRes Name of the setting.
 * @property options Selectable values, in stepper order.
 */
enum class SettingField(
    val route: String,
    @StringRes val labelRes: Int,
    val options: List<Int>
) {
    STEPS("settings/steps", R.string.settings_goal_steps, DailyGoals.STEPS.toList()),
    WATER_ML("settings/water", R.string.settings_goal_water, DailyGoals.WATER_ML.toList()),
    HEALTHY_MEALS("settings/meals", R.string.settings_goal_healthy_meals, DailyGoals.HEALTHY_MEALS.toList()),
    BEDTIME_START("settings/bedtime", R.string.settings_bedtime_start, UserSettings.BEDTIME_HOURS),
    BEDTIME_END("settings/wakeup", R.string.settings_bedtime_end, UserSettings.WAKE_UP_HOURS);

    /** The field's current value in [settings]. */
    fun valueIn(settings: UserSettings): Int = when (this) {
        STEPS -> settings.dailyGoals.steps
        WATER_ML -> settings.dailyGoals.waterMl
        HEALTHY_MEALS -> settings.dailyGoals.healthyMeals
        BEDTIME_START -> settings.bedtime.startHour
        BEDTIME_END -> settings.bedtime.endHour
    }

    /**
     * [settings] with this field set to [value].
     *
     * @throws IllegalArgumentException If [value] isn't one of [options].
     */
    fun update(settings: UserSettings, value: Int): UserSettings {
        require(value in options) { "$value is not an option for $this" }
        val goals = settings.dailyGoals
        val bedtime = settings.bedtime
        return when (this) {
            STEPS -> settings.copy(dailyGoals = goals.copy(steps = value))
            WATER_ML -> settings.copy(dailyGoals = goals.copy(waterMl = value))
            HEALTHY_MEALS -> settings.copy(dailyGoals = goals.copy(healthyMeals = value))
            BEDTIME_START -> settings.copy(bedtime = NightWindow(startHour = value, endHour = bedtime.endHour))
            BEDTIME_END -> settings.copy(bedtime = NightWindow(startHour = bedtime.startHour, endHour = value))
        }
    }

    /** Position of [value] in [options], or the nearest option's when [value] isn't one. */
    fun indexOf(value: Int): Int = options.indexOf(value).takeIf { it >= 0 }
        ?: options.indices.minBy { kotlin.math.abs(options[it] - value) }
}

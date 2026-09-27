// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.engine

import gg.dunder.thriveling.core.model.HabitEvent
import gg.dunder.thriveling.core.model.HabitType

/** A care button the user taps by hand, each with its own cooldown (DD-59). */
enum class CareAction { FOOD, WATER }

/**
 * The cooldown on feeding and giving water (DD-59): each can be logged at most once per [COOLDOWN_MS],
 * so the buttons can't be spammed for vitals and XP. Food and water have separate cooldowns; a healthy
 * meal and a snack share the food one.
 *
 * Pure: the repository enforces it on every write, and the UI uses it to dim the buttons.
 */
object CareCooldown {

    /** Minimum time between two meals, or between two drinks. */
    const val COOLDOWN_MS = 60 * 60 * 1000L

    /** The care action [habit] counts as, or `null` for habits without a cooldown (sensor data, petting). */
    fun actionOf(habit: HabitType): CareAction? = when (habit) {
        is HabitType.Meal -> CareAction.FOOD
        is HabitType.Hydration -> CareAction.WATER
        else -> null
    }

    /**
     * When [action] can be logged again, given the habit history, or `null` if it can be logged at [now].
     * Events stamped after [now] (the watch's clock was set back) are ignored, so they can't block the
     * button for longer than an hour.
     */
    fun availableAt(action: CareAction, events: List<HabitEvent>, now: Long): Long? =
        events
            .filter { actionOf(it.habit) == action && it.timestampMillis <= now }
            .maxOfOrNull { it.timestampMillis + COOLDOWN_MS }
            ?.takeIf { it > now }
}

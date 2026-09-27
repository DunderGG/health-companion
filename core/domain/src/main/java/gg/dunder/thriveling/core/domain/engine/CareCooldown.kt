// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.engine

import gg.dunder.thriveling.core.model.HabitEvent
import gg.dunder.thriveling.core.model.HabitType

/** Something the user does by hand for the pet, each with its own cooldown (DD-59, DD-60). */
enum class CareAction { FOOD, WATER, PETTING }

/**
 * The cooldown on care (DD-59): feeding, giving water and petting each count at most once per
 * [COOLDOWN_MS], so they can't be spammed for vitals and XP. Each has its own cooldown; every meal,
 * healthy or not, shares the food one. Petting stays possible every few seconds for the reaction, but
 * only rewards the pet once an hour (DD-60).
 *
 * Pure: the repository enforces it on every write, and the UI uses it to dim the food and water buttons.
 */
object CareCooldown {

    /** Minimum time between two rewarded uses of the same care action. */
    const val COOLDOWN_MS = 60 * 60 * 1000L

    /** The care action [habit] counts as, or `null` for habits without a cooldown (sensor data). */
    fun actionOf(habit: HabitType): CareAction? = when (habit) {
        is HabitType.Meal -> CareAction.FOOD
        is HabitType.Hydration -> CareAction.WATER
        is HabitType.PettingInteraction -> CareAction.PETTING
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

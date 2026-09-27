// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.model

/**
 * A habit that was applied to the pet at a point in time. The persisted history of these events
 * drives consistency-based features such as archetype selection.
 *
 * @property habit The habit that was recorded.
 * @property timestampMillis Epoch milliseconds when it was applied.
 */
data class HabitEvent(
    val habit: HabitType,
    val timestampMillis: Long
)

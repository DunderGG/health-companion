// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.presentation.ambient

import gg.dunder.thriveling.core.ui.theme.DisplayMode

/**
 * Whether the activity is interactive or in ambient (always-on) mode, as reported by
 * `AmbientLifecycleObserver` (DD-44).
 *
 * ### Kotlin vs C++ Note:
 * - **`sealed interface` as Variant**: Like `std::variant<Interactive, Ambient>`; `when` over it must
 *   handle both cases.
 */
sealed interface AmbientState {

    val displayMode: DisplayMode

    data object Interactive : AmbientState {
        override val displayMode = DisplayMode.INTERACTIVE
    }

    /**
     * @property burnInProtectionRequired The display is OLED-like and static pixels must move ([BurnInShift]).
     * @property lowBitAmbient The display shows only a few colours in ambient mode.
     * @property updateCount Number of once-a-minute ambient updates since entering ambient; drives the shift.
     */
    data class Ambient(
        val burnInProtectionRequired: Boolean,
        val lowBitAmbient: Boolean,
        val updateCount: Int = 0
    ) : AmbientState {
        override val displayMode = if (lowBitAmbient) DisplayMode.AMBIENT_LOW_BIT else DisplayMode.AMBIENT
    }
}

/**
 * Burn-in protection: moves the whole ambient screen by a few dp each minute, so no pixel stays lit
 * in the same place for long.
 */
object BurnInShift {

    /** Largest shift from the centre, per axis. */
    const val MAX_SHIFT_DP = 4

    /** A loop around the centre; neighbouring positions are one step apart, so the move is never jarring. */
    private val steps = listOf(0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1)

    /**
     * Offset (x, y) in dp for the given ambient update count, or (0, 0) when the display doesn't need it.
     */
    fun offsetDp(state: AmbientState): Pair<Int, Int> {
        if (state !is AmbientState.Ambient || !state.burnInProtectionRequired) return 0 to 0
        val (x, y) = steps[state.updateCount.mod(steps.size)]
        return x * MAX_SHIFT_DP to y * MAX_SHIFT_DP
    }
}

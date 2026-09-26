// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.PetActivity

/**
 * Immutable tracker turning individual step timestamps into a live [PetActivity].
 *
 * Only the current *burst* counts: the steps since the last pause longer than [IDLE_AFTER_MS],
 * limited to the last [WINDOW_MS]. The cadence is measured across the burst's own span
 * (`(steps - 1) / span`), so three quick steps already produce a reading instead of waiting
 * for a full window to fill.
 *
 * Running uses hysteresis (enter at [RUN_ENTER_SPM], leave below [RUN_EXIT_SPM]) so that a
 * cadence hovering around the threshold does not make the animation flicker.
 *
 * All values are game-feel tuning (DD-39). Timestamps are epoch milliseconds from the
 * injected clock's timebase.
 *
 * ### Kotlin vs C++ Note:
 * - **Immutable `data class` with `copy`**: Every update returns a new value instead of mutating,
 *   like a `const` struct passed through a pure reducer. This makes it safe to use in `Flow.scan`.
 *
 * @property recentSteps Timestamps of the current burst, oldest first.
 * @property activity The activity as of the last update.
 */
data class StepCadence(
    val recentSteps: List<Long> = emptyList(),
    val activity: PetActivity = PetActivity.IDLE
) {

    /**
     * Records one detected step and re-evaluates the activity at [nowMillis].
     * Out-of-order steps are inserted in order; steps from the future are clamped to [nowMillis].
     */
    fun record(stepMillis: Long, nowMillis: Long): StepCadence {
        val step = minOf(stepMillis, nowMillis)
        val steps = (recentSteps + step).sorted()
        return StepCadence(steps, activity).advance(nowMillis)
    }

    /** Re-evaluates the activity at [nowMillis], e.g. to fall back to idle once steps stop. */
    fun advance(nowMillis: Long): StepCadence {
        val burst = currentBurst(recentSteps, nowMillis)
        return StepCadence(burst, nextActivity(burst, nowMillis))
    }

    /** Steps per minute across the current burst, or 0 when there are too few steps to tell. */
    fun cadenceSpm(): Double {
        if (recentSteps.size < MIN_BURST_STEPS) return 0.0
        val span = recentSteps.last() - recentSteps.first()
        if (span <= 0L) return 0.0
        return (recentSteps.size - 1) * 60_000.0 / span
    }

    private fun nextActivity(burst: List<Long>, nowMillis: Long): PetActivity {
        val last = burst.lastOrNull() ?: return PetActivity.IDLE
        if (nowMillis - last > IDLE_AFTER_MS || burst.size < MIN_BURST_STEPS) return PetActivity.IDLE

        val cadence = StepCadence(burst).cadenceSpm()
        val runThreshold = if (activity == PetActivity.RUNNING) RUN_EXIT_SPM else RUN_ENTER_SPM
        return if (cadence >= runThreshold) PetActivity.RUNNING else PetActivity.WALKING
    }

    companion object {
        /** Only steps this recent are considered. */
        const val WINDOW_MS = 6_000L

        /** A pause this long ends a burst; the pet goes idle. Slow walking is about one step per second. */
        const val IDLE_AFTER_MS = 2_500L

        /** Steps needed before the pet reacts, so a single shuffle does not start it walking. */
        const val MIN_BURST_STEPS = 3

        /** Cadence at which the pet starts running. Brisk walking tops out around 130 steps/min. */
        const val RUN_ENTER_SPM = 145.0

        /** Cadence below which a running pet drops back to walking. */
        const val RUN_EXIT_SPM = 130.0

        /** Upper bound on retained steps, whatever the timestamps say. */
        private const val MAX_STEPS = 64

        /**
         * The steps inside [WINDOW_MS] that follow the last pause longer than [IDLE_AFTER_MS].
         */
        private fun currentBurst(steps: List<Long>, nowMillis: Long): List<Long> {
            val windowed = steps.filter { nowMillis - it <= WINDOW_MS }.takeLast(MAX_STEPS)
            val lastGap = windowed.zipWithNext().indexOfLast { (a, b) -> b - a > IDLE_AFTER_MS }
            return if (lastGap < 0) windowed else windowed.drop(lastGap + 1)
        }
    }
}

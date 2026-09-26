// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.Vitals
import java.time.ZoneId

/**
 * A vital that can reach a critical level and warrant a notification.
 *
 * @property threshold Values strictly below this are critical (the same line as the pet's mood).
 * @property decayPerHour Linear decay rate, used to predict when the threshold will be crossed.
 */
enum class CriticalVital(
    val threshold: Float,
    val decayPerHour: Float,
    private val read: (Vitals) -> Float
) {
    HYDRATION(MoodCalculator.THIRSTY_BELOW, PetDecayEngine.HYDRATION_DECAY_PER_HOUR, Vitals::hydration),
    HUNGER(MoodCalculator.HUNGRY_BELOW, PetDecayEngine.HUNGER_DECAY_PER_HOUR, Vitals::hunger);

    /** The current value of this vital in [vitals]. */
    fun valueIn(vitals: Vitals): Float = read(vitals)
}

/**
 * Outcome of one alert evaluation.
 *
 * @property newlyCritical Vitals to notify about now.
 * @property restored Previously notified vitals that are back above their threshold (clear their notification).
 * @property notified The notified set to persist for the next evaluation.
 * @property nextCheckAtMillis When to evaluate again, or `null` if only a pet write can change anything.
 */
data class VitalAlertPlan(
    val newlyCritical: Set<CriticalVital>,
    val restored: Set<CriticalVital>,
    val notified: Set<CriticalVital>,
    val nextCheckAtMillis: Long?
)

/**
 * Pure rules for critical-vital notifications (DD-40, DD-41).
 *
 * - A vital is critical below its [CriticalVital.threshold], the same line where the pet turns
 *   `THIRSTY` or `HUNGRY`.
 * - Each vital alerts **once per episode**: it stays in the notified set until it recovers to at
 *   least its threshold, which re-arms it.
 * - No alerts during the pet's [NightWindow] (quiet hours). A vital that is or becomes critical at
 *   night is alerted at the night's end, if it is still critical then.
 * - Hydration and hunger decay linearly, so the next crossing is predicted exactly and a single
 *   check can be scheduled for it, instead of polling.
 */
object VitalAlertPlanner {

    /**
     * Scheduled checks land at least this far past the predicted crossing, so the vital is clearly
     * below the threshold by then (a check landing just short of it would reschedule immediately).
     */
    const val CROSSING_MARGIN_MS = 60_000L

    /**
     * @param vitals Vitals already decayed to [nowMillis].
     * @param nowMillis Current epoch time.
     * @param zone Time zone of the night window.
     * @param notified Vitals already alerted in their current episode.
     * @param nightWindow Quiet hours.
     */
    fun plan(
        vitals: Vitals,
        nowMillis: Long,
        zone: ZoneId,
        notified: Set<CriticalVital>,
        nightWindow: NightWindow = NightWindow.DEFAULT
    ): VitalAlertPlan {
        val newlyCritical = mutableSetOf<CriticalVital>()
        val restored = mutableSetOf<CriticalVital>()
        val checkTimes = mutableListOf<Long>()
        val quietNow = nightWindow.isNight(nowMillis, zone)

        for (vital in CriticalVital.entries) {
            val value = vital.valueIn(vitals)
            if (value >= vital.threshold) {
                if (vital in notified) restored += vital
                val hoursLeft = (value - vital.threshold) / vital.decayPerHour
                val crossingAt = nowMillis + (hoursLeft * HOUR_MS).toLong() + CROSSING_MARGIN_MS
                checkTimes += nightWindow.nextDaytime(crossingAt, zone)
            } else if (vital !in notified) {
                if (quietNow) {
                    checkTimes += nightWindow.nextDaytime(nowMillis, zone)
                } else {
                    newlyCritical += vital
                }
            }
            // Critical and already notified: nothing to do until a write restores it.
        }

        return VitalAlertPlan(
            newlyCritical = newlyCritical,
            restored = restored,
            notified = notified - restored + newlyCritical,
            nextCheckAtMillis = checkTimes.minOrNull()
        )
    }

    private const val HOUR_MS = 60f * 60f * 1000f
}

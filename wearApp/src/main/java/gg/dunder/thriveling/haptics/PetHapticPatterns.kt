// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.haptics

import android.os.VibrationEffect

/**
 * Moments the wrist should feel (DD-47, DD-50, DD-61), from least to most important.
 *
 * @property priority A pattern never cuts off one with a higher priority that is still playing.
 */
enum class PetHapticEvent(val priority: Int) {
    /** A tap on the pet was accepted, but petting is in its reward cooldown (DD-61): one click. */
    PETTING_UNREWARDED(0),

    /** A vital just reached 100 % on screen (DD-50): two light clicks, lighter than a goal. */
    VITAL_FILLED(0),

    /** A tap on the pet was accepted and earns happiness (DD-60): a purr of four clicks. */
    PETTING(1),

    /** Today's habits just reached a daily focus goal (e.g. 6,000 steps): a short success pattern. */
    GOAL_REACHED(2),

    /** The pet grew into its next stage: a longer fanfare. */
    EVOLUTION(3);

    /** A reaction to a tap on the pet, played as touch feedback. */
    val isPetting: Boolean get() = this == PETTING || this == PETTING_UNREWARDED
}

/**
 * One step of a composed pattern.
 *
 * @property primitive A `VibrationEffect.Composition.PRIMITIVE_*` id. Only primitives from API 30 are used,
 *   the app's minimum SDK.
 * @property scale Intensity, `0..1`.
 * @property delayMs Pause before this step starts.
 */
data class HapticStep(val primitive: Int, val scale: Float, val delayMs: Int = 0)

/**
 * A pattern as rich primitives, with a plain waveform for motors that can't play them.
 *
 * @property steps Composition played when the device supports all its primitives. Empty for a pattern
 *   that is always played as its waveform, e.g. a buzz longer than any primitive (DD-62).
 * @property fallbackTimings Waveform segment lengths in ms, alternating off/on and starting with "off".
 * @property fallbackAmplitudes Amplitude per segment, `0..255`; ignored without amplitude control.
 */
data class HapticPattern(
    val steps: List<HapticStep>,
    val fallbackTimings: LongArray,
    val fallbackAmplitudes: IntArray
) {
    /** Total length of the waveform fallback. */
    val fallbackDurationMs: Long get() = fallbackTimings.sum()
}

/** The five patterns. They differ in length and shape, so they can be told apart without looking. */
object PetHapticPatterns {

    /**
     * A single short buzz, the purr's first beat: "noticed", without a reward. The shortest pattern.
     * A buzz rather than a click, which can't be felt under a finger pressing the screen (DD-62).
     */
    val PETTING_UNREWARDED = HapticPattern(
        steps = emptyList(),
        fallbackTimings = longArrayOf(0, 50),
        fallbackAmplitudes = intArrayOf(0, 255)
    )

    /** Two clicks, the second firmer: "topped up". About 0.1 s, lighter than a goal. */
    val VITAL_FILLED = HapticPattern(
        steps = listOf(
            HapticStep(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f),
            HapticStep(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, delayMs = 60)
        ),
        fallbackTimings = longArrayOf(0, 30, 60, 30),
        fallbackAmplitudes = intArrayOf(0, 140, 0, 190)
    )

    /** Four short buzzes rising and fading, like a purr under the finger. About 0.3 s (DD-62). */
    val PETTING = HapticPattern(
        steps = emptyList(),
        fallbackTimings = longArrayOf(0, 40, 50, 40, 50, 40, 50, 40),
        fallbackAmplitudes = intArrayOf(0, 200, 0, 255, 0, 230, 0, 200)
    )

    /** A quick swell and two confident clicks: "done!". About 0.4 s. */
    val GOAL_REACHED = HapticPattern(
        steps = listOf(
            HapticStep(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f),
            HapticStep(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, delayMs = 60),
            HapticStep(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, delayMs = 90)
        ),
        fallbackTimings = longArrayOf(0, 120, 60, 40, 90, 40),
        fallbackAmplitudes = intArrayOf(0, 140, 0, 255, 0, 200)
    )

    /** A slow rise, then three clicks: a small fanfare for growing up. About 1 s. */
    val EVOLUTION = HapticPattern(
        steps = listOf(
            HapticStep(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.8f),
            HapticStep(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.6f),
            HapticStep(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, delayMs = 120),
            HapticStep(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, delayMs = 90),
            HapticStep(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, delayMs = 90)
        ),
        fallbackTimings = longArrayOf(0, 350, 120, 40, 90, 40, 90, 60),
        fallbackAmplitudes = intArrayOf(0, 120, 0, 255, 0, 255, 0, 255)
    )

    fun of(event: PetHapticEvent): HapticPattern = when (event) {
        PetHapticEvent.PETTING_UNREWARDED -> PETTING_UNREWARDED
        PetHapticEvent.VITAL_FILLED -> VITAL_FILLED
        PetHapticEvent.PETTING -> PETTING
        PetHapticEvent.GOAL_REACHED -> GOAL_REACHED
        PetHapticEvent.EVOLUTION -> EVOLUTION
    }
}

/**
 * Decides whether a new pattern may start: it must not cut off a more important one that is still
 * playing (e.g. a goal reached by the same write that evolved the pet). Equal or lower priority
 * patterns that are still playing are replaced.
 */
class HapticArbiter {
    private var busyUntilMs = Long.MIN_VALUE
    private var busyPriority = Int.MIN_VALUE

    /**
     * @param nowMs A monotonic clock reading.
     * @return `true` if [event] should play now; it is then recorded as playing.
     */
    fun tryStart(event: PetHapticEvent, nowMs: Long): Boolean {
        if (nowMs < busyUntilMs && event.priority < busyPriority) return false
        busyUntilMs = nowMs + PetHapticPatterns.of(event).fallbackDurationMs
        busyPriority = event.priority
        return true
    }
}

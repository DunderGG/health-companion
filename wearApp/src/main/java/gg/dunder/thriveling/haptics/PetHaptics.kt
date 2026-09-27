// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Plays [PetHapticPatterns] on the watch's vibration motor (DD-47).
 *
 * Uses composed primitives (`VibrationEffect.Composition`) when the motor supports all of a pattern's
 * primitives, and a plain waveform otherwise. Petting is played as touch feedback on API 33+, so the
 * system's touch-vibration setting applies to it.
 *
 * ### Kotlin vs C++ Note:
 * - **`Build.VERSION.SDK_INT` checks**: Like `#if` feature guards, but at run time: newer APIs are only
 *   called on devices that have them, and lint verifies each call is guarded.
 *
 * @param context Any context; only the application context is retained.
 */
class PetHaptics(context: Context) {

    private val vibrator: Vibrator = context.applicationContext.let { app ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Vibrator::class.java)
        }
    }

    private val arbiter = HapticArbiter()

    /** Plays the pattern for [event], unless a more important one is still playing. */
    fun play(event: PetHapticEvent) {
        if (!vibrator.hasVibrator()) return
        if (!arbiter.tryStart(event, SystemClock.elapsedRealtime())) return

        val effect = effectFor(PetHapticPatterns.of(event))
        if (event.isPetting && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator.vibrate(effect)
        }
    }

    // Every HapticStep.primitive is a Composition.PRIMITIVE_* constant (see PetHapticPatterns), but the
    // framework's IntDef for them is hidden, so lint can't follow the value through the data class.
    @SuppressLint("WrongConstant")
    private fun effectFor(pattern: HapticPattern): VibrationEffect {
        val primitives = pattern.steps.map { it.primitive }.distinct().toIntArray()
        if (vibrator.areAllPrimitivesSupported(*primitives)) {
            val composition = VibrationEffect.startComposition()
            pattern.steps.forEach { composition.addPrimitive(it.primitive, it.scale, it.delayMs) }
            return composition.compose()
        }
        return if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(pattern.fallbackTimings, pattern.fallbackAmplitudes, NO_REPEAT)
        } else {
            VibrationEffect.createWaveform(pattern.fallbackTimings, NO_REPEAT)
        }
    }

    private companion object {
        const val NO_REPEAT = -1
    }
}

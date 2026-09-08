// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.model

/**
 * Expression and behavioral mood of the companion.
 * Drives dynamic expressions, canvas drawing, animations, aura particles, and haptic feedback.
 *
 * Calculated dynamically from the companion's vitals by `MoodCalculator`.
 */
enum class Mood {
    /** Vitals overall health >= 80% or companion is actively petted. Triggers glowing cyan/purple aura and bursting hearts. */
    ECSTATIC,

    /** Vitals overall health >= 55%. Character displays a bright smile and gentle ear wiggles. */
    HAPPY,

    /** Baseline neutral state with balanced vitals (greenish healthy aura, smiling expression). */
    CONTENT,

    /** Energy < 20%. Character displays droopy ears and an inverted mouth arc. */
    TIRED,

    /** Hydration < 25%. Character displays an open mouth and floating water droplet particles. */
    THIRSTY,

    /** Hunger < 25%. Character displays an orange hunger aura and craving particles. */
    HUNGRY,

    /** Happiness < 30%. Character displays a reddish grumpy aura and downward tilted ears. */
    GRUMPY,

    /** Night time with energy < 40%. Character closes eyes and emits floating Zzz sleep particles. */
    SLEEPING
}


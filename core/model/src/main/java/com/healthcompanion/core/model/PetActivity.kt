// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.model

/**
 * What the companion is physically doing on screen, mirrored live from the user's own steps.
 *
 * Purely cosmetic: it drives the character's gait animation and never changes vitals
 * (steps are credited through passive daily totals instead).
 */
enum class PetActivity {
    /** The user is standing still; the pet idles with its normal breathing animation. */
    IDLE,

    /** Walking cadence; the pet trots alongside with a gentle bob and alternating paws. */
    WALKING,

    /** Running cadence; the pet leans forward and sprints with speed lines. */
    RUNNING
}

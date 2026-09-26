// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.complications

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.healthcompanion.core.model.Mood
import com.healthcompanion.wear.R

/**
 * How a [Mood] is shown on a watch dial: a monochrome face icon and a short label.
 *
 * Labels stay within the ~7 characters a short-text complication can show (e.g. "Asleep" rather
 * than "Sleeping").
 */
internal data class MoodPresentation(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val labelRes: Int
) {
    companion object {
        /** Exhaustive, so a new [Mood] fails to compile until it has an icon and a label. */
        fun of(mood: Mood): MoodPresentation = when (mood) {
            Mood.ECSTATIC -> MoodPresentation(R.drawable.ic_mood_ecstatic, R.string.mood_short_ecstatic)
            Mood.HAPPY -> MoodPresentation(R.drawable.ic_mood_happy, R.string.mood_short_happy)
            Mood.CONTENT -> MoodPresentation(R.drawable.ic_mood_content, R.string.mood_short_content)
            Mood.TIRED -> MoodPresentation(R.drawable.ic_mood_tired, R.string.mood_short_tired)
            Mood.THIRSTY -> MoodPresentation(R.drawable.ic_mood_thirsty, R.string.mood_short_thirsty)
            Mood.HUNGRY -> MoodPresentation(R.drawable.ic_mood_hungry, R.string.mood_short_hungry)
            Mood.GRUMPY -> MoodPresentation(R.drawable.ic_mood_grumpy, R.string.mood_short_grumpy)
            Mood.SLEEPING -> MoodPresentation(R.drawable.ic_mood_sleeping, R.string.mood_short_sleeping)
        }
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * How the screen is currently shown, so components can switch to a low-power look (DD-44).
 *
 * In ambient (always-on) mode the display is refreshed about once a minute and most pixels must stay
 * black: components drop animations, gradients and fills, and draw thin outlines in [ambientColor].
 */
enum class DisplayMode {
    /** Normal, fully coloured and animated. */
    INTERACTIVE,

    /** Always-on: static grey outlines on black. */
    AMBIENT,

    /**
     * Always-on on a low-bit display, which shows only a few colours. Grey could be quantized to black
     * and vanish, so outlines are drawn in pure white.
     */
    AMBIENT_LOW_BIT;

    val isAmbient: Boolean get() = this != INTERACTIVE

    /** Outline colour for ambient drawing. */
    val ambientColor: Color get() = if (this == AMBIENT_LOW_BIT) Color.White else AmbientGray
}

/** Mid grey for ambient outlines: readable, but dimmer (fewer lit sub-pixels) than white. */
val AmbientGray = Color(0xFFB0B0B0)

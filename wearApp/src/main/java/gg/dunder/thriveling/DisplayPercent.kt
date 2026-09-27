// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling

import kotlin.math.roundToInt

/**
 * A vital (or overall health) as the whole percentage shown on every surface: app, tile and complication.
 *
 * Rounded, not truncated: a vital that was just filled to 100 decays to 99.99 within seconds, and
 * truncating would show 99 % right after the user topped it up (DD-46). `NaN` shows as 0, matching
 * the vital clamp (DD-21); `roundToInt` would throw on it.
 */
fun Float.toDisplayPercent(): Int = if (isNaN()) 0 else roundToInt().coerceIn(0, 100)

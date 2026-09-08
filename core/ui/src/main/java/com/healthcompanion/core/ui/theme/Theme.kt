// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Color palette and theme definition optimized for OLED smartwatch displays.
 *
 * ### Wear OS Power & Battery Optimization:
 * Pure black and ultra-dark tones ([DarkBackground], [SurfaceDark]) allow OLED/AMOLED pixels
 * on watch displays to remain completely unpowered, drastically cutting power draw while
 * high-contrast vibrant neon accents maintain readability outdoors under direct sunlight.
 */

// Vibrant neon accent tokens
val NeonCyan = Color(0xFF00E5FF)
val BrightAqua = Color(0xFF00B0FF)
val ElectricPurple = Color(0xFF7C4DFF)
val SunsetOrange = Color(0xFFFF9100)
val HealthyGreen = Color(0xFF00E676)
val SoftPink = Color(0xFFFF4081)

// Ultra-dark power-saving surface backgrounds
val DarkBackground = Color(0xFF0A0E14)
val SurfaceDark = Color(0xFF161C24)
val SurfaceBright = Color(0xFF242E3D)

/**
 * Wear OS Material 3 color scheme mapping semantic tokens (primary, surface, background)
 * to our high-contrast companion palette.
 */
val ModernColorScheme = ColorScheme(
    primary = NeonCyan,
    primaryDim = BrightAqua,
    secondary = ElectricPurple,
    background = DarkBackground,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceBright,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

/**
 * Root theme composable wrapping child UI components in the [ModernColorScheme].
 *
 * ### Kotlin vs C++ Note:
 * - **`@Composable`**: Marks this function as part of the Jetpack Compose reactive UI tree.
 *   The Kotlin compiler plugin rewrites `@Composable` functions to track dependencies; when inputs
 *   change, only the affected nodes in the call tree are re-executed (**recomposition**).
 * - **Slot API Pattern (`content: @Composable () -> Unit`)**: Passing a composable lambda
 *   parameter is Kotlin's standard idiom for structural containment (similar to passing a functor
 *   or children widget in C++ GUI libraries like Qt or ImGui).
 *
 * @param content The nested Composable hierarchy to be themed.
 */
@Composable
fun HealthCompanionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ModernColorScheme,
        content = content
    )
}


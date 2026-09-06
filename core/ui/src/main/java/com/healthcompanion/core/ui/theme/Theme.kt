// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

// Modern Vibrant Palette for OLED Smartwatches
val NeonCyan = Color(0xFF00E5FF)
val BrightAqua = Color(0xFF00B0FF)
val ElectricPurple = Color(0xFF7C4DFF)
val SunsetOrange = Color(0xFFFF9100)
val HealthyGreen = Color(0xFF00E676)
val SoftPink = Color(0xFFFF4081)
val DarkBackground = Color(0xFF0A0E14)
val SurfaceDark = Color(0xFF161C24)
val SurfaceBright = Color(0xFF242E3D)

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

@Composable
fun HealthCompanionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ModernColorScheme,
        content = content
    )
}


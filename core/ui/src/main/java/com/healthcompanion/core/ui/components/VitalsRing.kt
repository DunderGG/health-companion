// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthcompanion.core.model.Vitals
import com.healthcompanion.core.ui.theme.BrightAqua
import com.healthcompanion.core.ui.theme.ElectricPurple
import com.healthcompanion.core.ui.theme.HealthyGreen
import com.healthcompanion.core.ui.theme.SunsetOrange

/**
 * Circular multi-vital progress arcs fitted specifically around the bezel of round Wear OS displays.
 *
 * ### Wear OS Circular Geometry:
 * Instead of taking up central screen estate, vitals are mapped to four quadrant arcs along the outer watch edge:
 * 1. **Top-Right (280° - 350°)**: Fitness / Steps ([HealthyGreen]).
 * 2. **Bottom-Right (10° - 80°)**: Hydration ([BrightAqua]).
 * 3. **Bottom-Left (100° - 170°)**: Hunger / Nutrition ([SunsetOrange]).
 * 4. **Top-Left (190° - 260°)**: Energy / Sleep ([ElectricPurple]).
 *
 * ### Kotlin vs C++ Note:
 * - **`Modifier` Chaining**: The `Modifier` argument is Compose's standard decoration pattern,
 *   analogous to a fluent builder pattern in C++ (`Modifier().fillMaxSize().padding(...)`).
 * - **Canvas**: An immediate-mode 2D drawing surface executed inside Compose (similar to an HTML5 Canvas
 *   or an ImGui draw list).
 *
 * @param vitals Current companion vitals ([Vitals]).
 * @param modifier Compose layout modifier applied to the outer container.
 * @param strokeWidth Thickness of the gauge arcs in density-independent pixels ([Dp], default 5.dp).
 * @param content Nested composable slot rendered in the center of the ring (e.g. companion sprite and actions).
 */
@Composable
fun VitalsRing(
    vitals: Vitals,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 5.dp,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            // Segment 1: Steps / Fitness (Top-Right: 280 deg to 350 deg)
            drawVitalArc(
                startAngle = 280f,
                sweepTotal = 70f,
                progress = vitals.fitness / 100f,
                color = HealthyGreen,
                topLeft = topLeft,
                size = arcSize,
                strokePx = strokePx
            )

            // Segment 2: Hydration (Bottom-Right: 10 deg to 80 deg)
            drawVitalArc(
                startAngle = 10f,
                sweepTotal = 70f,
                progress = vitals.hydration / 100f,
                color = BrightAqua,
                topLeft = topLeft,
                size = arcSize,
                strokePx = strokePx
            )

            // Segment 3: Hunger / Nutrition (Bottom-Left: 100 deg to 170 deg)
            drawVitalArc(
                startAngle = 100f,
                sweepTotal = 70f,
                progress = vitals.hunger / 100f,
                color = SunsetOrange,
                topLeft = topLeft,
                size = arcSize,
                strokePx = strokePx
            )

            // Segment 4: Energy (Top-Left: 190 deg to 260 deg)
            drawVitalArc(
                startAngle = 190f,
                sweepTotal = 70f,
                progress = vitals.energy / 100f,
                color = ElectricPurple,
                topLeft = topLeft,
                size = arcSize,
                strokePx = strokePx
            )
        }

        content()
    }
}

/**
 * Helper extension function on [androidx.compose.ui.graphics.drawscope.DrawScope] to draw a two-pass arc:
 * first a semi-transparent background track (alpha 0.2), then a solid progress arc with rounded caps.
 *
 * ### Kotlin vs C++ Note:
 * - **Extension Functions**: `fun DrawScope.drawVitalArc(...)` extends `DrawScope` with a new method
 *   without subclassing or modifying its original source code. Under the hood, the compiler emits a static
 *   free function whose first parameter is `DrawScope self` (identical to `void drawVitalArc(DrawScope& self, ...)`).
 *
 * @param startAngle Angle in degrees where the arc begins (0° = 3 o'clock).
 * @param sweepTotal Total span in degrees of the arc segment.
 * @param progress Normalized fraction in range `[0.0, 1.0]`.
 * @param color Primary accent color for the vital indicator.
 * @param topLeft Top-left coordinate offset of the bounding ellipse.
 * @param size Dimensions of the bounding ellipse.
 * @param strokePx Stroke width in physical screen pixels.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVitalArc(
    startAngle: Float,
    sweepTotal: Float,
    progress: Float,
    color: Color,
    topLeft: Offset,
    size: Size,
    strokePx: Float
) {
    // Background track
    drawArc(
        color = color.copy(alpha = 0.2f),
        startAngle = startAngle,
        sweepAngle = sweepTotal,
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokePx, cap = StrokeCap.Round)
    )

    // Filled progress arc
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepTotal * progress.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokePx, cap = StrokeCap.Round)
    )
}


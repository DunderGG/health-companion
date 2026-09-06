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
 * Circular multi-vital progress arcs fitted specifically for round Wear OS displays.
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


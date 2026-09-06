// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthcompanion.core.model.Mood
import com.healthcompanion.core.ui.theme.ElectricPurple
import com.healthcompanion.core.ui.theme.HealthyGreen
import com.healthcompanion.core.ui.theme.NeonCyan
import com.healthcompanion.core.ui.theme.SoftPink
import com.healthcompanion.core.ui.theme.SunsetOrange

/**
 * Modern vector-rendered virtual companion.
 * Features organic breathing physics, dynamic mood expressions, and gentle gradients.
 */
@Composable
fun ModernPetCanvas(
    mood: Mood,
    modifier: Modifier = Modifier,
    canvasSize: Dp = 140.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pet_animations")

    // Gentle breathing vertical bounce
    val breathBounce by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathBounce"
    )

    // Periodic eye blinking
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkProgress"
    )

    Box(
        modifier = modifier.size(canvasSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, (size.height / 2f) + breathBounce)
            val bodyRadius = size.minDimension * 0.38f

            // Dynamic companion body gradient based on mood
            val bodyBrush = when (mood) {
                Mood.ECSTATIC, Mood.HAPPY -> Brush.radialGradient(
                    colors = listOf(NeonCyan, ElectricPurple),
                    center = centerOffset,
                    radius = bodyRadius * 1.2f
                )
                Mood.THIRSTY -> Brush.radialGradient(
                    colors = listOf(Color(0xFF80D8FF), Color(0xFF0091EA)),
                    center = centerOffset,
                    radius = bodyRadius * 1.2f
                )
                Mood.HUNGRY -> Brush.radialGradient(
                    colors = listOf(SunsetOrange, Color(0xFFD84315)),
                    center = centerOffset,
                    radius = bodyRadius * 1.2f
                )
                Mood.TIRED, Mood.SLEEPING -> Brush.radialGradient(
                    colors = listOf(Color(0xFF9FA8DA), Color(0xFF3949AB)),
                    center = centerOffset,
                    radius = bodyRadius * 1.2f
                )
                Mood.GRUMPY -> Brush.radialGradient(
                    colors = listOf(Color(0xFFEF9A9A), Color(0xFFC62828)),
                    center = centerOffset,
                    radius = bodyRadius * 1.2f
                )
                Mood.CONTENT -> Brush.radialGradient(
                    colors = listOf(HealthyGreen, Color(0xFF00897B)),
                    center = centerOffset,
                    radius = bodyRadius * 1.2f
                )
            }

            // Draw organic soft body
            drawCircle(
                brush = bodyBrush,
                radius = bodyRadius,
                center = centerOffset
            )

            // Outer soft aura glow ring
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = bodyRadius + 4f,
                center = centerOffset,
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw cute blushing cheeks
            val cheekY = centerOffset.y + 10f
            val cheekSpacing = bodyRadius * 0.58f
            val cheekRadius = 6f
            drawCircle(
                color = SoftPink.copy(alpha = 0.45f),
                radius = cheekRadius,
                center = Offset(centerOffset.x - cheekSpacing, cheekY)
            )
            drawCircle(
                color = SoftPink.copy(alpha = 0.45f),
                radius = cheekRadius,
                center = Offset(centerOffset.x + cheekSpacing, cheekY)
            )

            // Draw expressive eyes according to mood
            drawEyes(
                mood = mood,
                center = centerOffset,
                eyeSpacing = bodyRadius * 0.38f,
                eyeYOffset = -6f,
                blinkFactor = if (mood == Mood.SLEEPING) 0f else blinkProgress
            )

            // Draw mouth expression
            drawMouth(mood = mood, center = centerOffset)
        }
    }
}

private fun DrawScope.drawEyes(
    mood: Mood,
    center: Offset,
    eyeSpacing: Float,
    eyeYOffset: Float,
    blinkFactor: Float
) {
    val leftEyeCenter = Offset(center.x - eyeSpacing, center.y + eyeYOffset)
    val rightEyeCenter = Offset(center.x + eyeSpacing, center.y + eyeYOffset)

    when (mood) {
        Mood.SLEEPING -> {
            // Sleeping closed curved arcs
            val arcSize = Size(16f, 8f)
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(leftEyeCenter.x - 8f, leftEyeCenter.y),
                size = arcSize,
                style = Stroke(width = 3.dp.toPx())
            )
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(rightEyeCenter.x - 8f, rightEyeCenter.y),
                size = arcSize,
                style = Stroke(width = 3.dp.toPx())
            )
        }
        Mood.ECSTATIC -> {
            // Joyful upward crescents (happy inverted arcs)
            val arcSize = Size(16f, 10f)
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(leftEyeCenter.x - 8f, leftEyeCenter.y - 5f),
                size = arcSize,
                style = Stroke(width = 3.5.dp.toPx())
            )
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(rightEyeCenter.x - 8f, rightEyeCenter.y - 5f),
                size = arcSize,
                style = Stroke(width = 3.5.dp.toPx())
            )
        }
        else -> {
            // Modern rounded expressive pupil eyes with blinking scale
            val eyeHeight = 14f * blinkFactor
            val eyeWidth = 10f

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(leftEyeCenter.x - (eyeWidth / 2f), leftEyeCenter.y - (eyeHeight / 2f)),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(eyeWidth / 2f, eyeWidth / 2f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(rightEyeCenter.x - (eyeWidth / 2f), rightEyeCenter.y - (eyeHeight / 2f)),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(eyeWidth / 2f, eyeWidth / 2f)
            )

            // Catchlight sparkles in eyes if not fully blinking
            if (blinkFactor > 0.4f) {
                drawCircle(
                    color = Color.Black,
                    radius = 3.5f,
                    center = Offset(leftEyeCenter.x, leftEyeCenter.y + 1f)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 3.5f,
                    center = Offset(rightEyeCenter.x, rightEyeCenter.y + 1f)
                )
            }
        }
    }
}

private fun DrawScope.drawMouth(mood: Mood, center: Offset) {
    val mouthCenter = Offset(center.x, center.y + 12f)

    when (mood) {
        Mood.HAPPY, Mood.ECSTATIC, Mood.CONTENT -> {
            // Gentle smile arc
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x - 8f, mouthCenter.y - 4f),
                size = Size(16f, 10f),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        Mood.HUNGRY, Mood.THIRSTY -> {
            // Small open "O" mouth
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = mouthCenter,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        Mood.GRUMPY, Mood.TIRED -> {
            // Inverted subtle frown arc
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x - 7f, mouthCenter.y),
                size = Size(14f, 8f),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        Mood.SLEEPING -> {
            // Peaceful small resting dot/line
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(mouthCenter.x - 4f, mouthCenter.y),
                end = Offset(mouthCenter.x + 4f, mouthCenter.y),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}


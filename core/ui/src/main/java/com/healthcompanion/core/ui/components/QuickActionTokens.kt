// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthcompanion.core.ui.theme.BrightAqua
import com.healthcompanion.core.ui.theme.DarkBackground
import com.healthcompanion.core.ui.theme.SunsetOrange
import com.healthcompanion.core.ui.theme.SurfaceDark

/**
 * Modern glassmorphic quick-action button designed for round Wear OS displays.
 * Features a dark OLED-friendly translucent container, glowing accent gradient border,
 * tactile spring press animation, and haptic feedback.
 */
@Composable
fun QuickActionToken(
    accentColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    icon: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "action_token_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.25f),
                        SurfaceDark.copy(alpha = 0.85f),
                        DarkBackground
                    )
                )
            )
            .border(
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.75f),
                            accentColor.copy(alpha = 0.20f)
                        )
                    )
                ),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/**
 * Quick meal action token with a custom vector salad/nourishment bowl icon.
 */
@Composable
fun MealActionToken(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    QuickActionToken(
        accentColor = SunsetOrange,
        contentDescription = "Log Healthy Meal",
        onClick = onClick,
        modifier = modifier,
        size = size
    ) {
        MealBowlVector(
            color = SunsetOrange,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Quick water action token with a custom vector teardrop icon.
 */
@Composable
fun WaterActionToken(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    QuickActionToken(
        accentColor = BrightAqua,
        contentDescription = "Log Water 250ml",
        onClick = onClick,
        modifier = modifier,
        size = size
    ) {
        WaterDropletVector(
            color = BrightAqua,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Procedural vector water droplet with a curved teardrop body and a glossy specular highlight.
 */
@Composable
fun WaterDropletVector(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Teardrop path
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.10f)
            // Left curve down to bulb
            cubicTo(
                w * 0.28f, h * 0.38f,
                w * 0.15f, h * 0.60f,
                w * 0.15f, h * 0.72f
            )
            // Bottom rounded bulb
            cubicTo(
                w * 0.15f, h * 0.92f,
                w * 0.31f, h * 0.95f,
                w * 0.50f, h * 0.95f
            )
            // Bottom right rounded bulb
            cubicTo(
                w * 0.69f, h * 0.95f,
                w * 0.85f, h * 0.92f,
                w * 0.85f, h * 0.72f
            )
            // Right curve back to top point
            cubicTo(
                w * 0.85f, h * 0.60f,
                w * 0.72f, h * 0.38f,
                w * 0.50f, h * 0.10f
            )
            close()
        }

        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.9f),
                    color
                )
            ),
            style = Fill
        )

        // Glassy curved catchlight on top-left
        val highlightPath = Path().apply {
            moveTo(w * 0.35f, h * 0.45f)
            cubicTo(
                w * 0.28f, h * 0.55f,
                w * 0.25f, h * 0.68f,
                w * 0.28f, h * 0.78f
            )
        }
        drawPath(
            path = highlightPath,
            color = Color.White.copy(alpha = 0.55f),
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

/**
 * Procedural vector healthy meal/bowl with fresh leaves rising from the top.
 */
@Composable
fun MealBowlVector(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Semicircular bowl body
        val bowlPath = Path().apply {
            moveTo(w * 0.16f, h * 0.48f)
            lineTo(w * 0.84f, h * 0.48f)
            cubicTo(
                w * 0.84f, h * 0.78f,
                w * 0.68f, h * 0.90f,
                w * 0.50f, h * 0.90f
            )
            cubicTo(
                w * 0.32f, h * 0.90f,
                w * 0.16f, h * 0.78f,
                w * 0.16f, h * 0.48f
            )
            close()
        }

        drawPath(
            path = bowlPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = 0.85f),
                    color
                )
            ),
            style = Fill
        )

        // Subtle bowl rim highlight
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = Offset(w * 0.20f, h * 0.48f),
            end = Offset(w * 0.80f, h * 0.48f),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Left leaf sprouting upward
        val leftLeafPath = Path().apply {
            moveTo(w * 0.50f, h * 0.46f)
            cubicTo(
                w * 0.36f, h * 0.38f,
                w * 0.28f, h * 0.26f,
                w * 0.34f, h * 0.14f
            )
            cubicTo(
                w * 0.48f, h * 0.16f,
                w * 0.50f, h * 0.32f,
                w * 0.50f, h * 0.46f
            )
            close()
        }
        drawPath(
            path = leftLeafPath,
            color = color,
            style = Fill
        )

        // Right leaf sprouting upward
        val rightLeafPath = Path().apply {
            moveTo(w * 0.50f, h * 0.46f)
            cubicTo(
                w * 0.56f, h * 0.34f,
                w * 0.64f, h * 0.20f,
                w * 0.72f, h * 0.16f
            )
            cubicTo(
                w * 0.74f, h * 0.30f,
                w * 0.62f, h * 0.40f,
                w * 0.50f, h * 0.46f
            )
            close()
        }
        drawPath(
            path = rightLeafPath,
            color = color.copy(alpha = 0.85f),
            style = Fill
        )
    }
}


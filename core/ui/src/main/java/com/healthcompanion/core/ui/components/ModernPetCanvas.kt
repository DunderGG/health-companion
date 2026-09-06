// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.sin
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthcompanion.core.model.Mood
import com.healthcompanion.core.ui.theme.ElectricPurple
import com.healthcompanion.core.ui.theme.HealthyGreen
import com.healthcompanion.core.ui.theme.NeonCyan
import com.healthcompanion.core.ui.theme.SoftPink
import com.healthcompanion.core.ui.theme.SunsetOrange

/**
 * Procedural vector virtual companion character.
 * Features organic breathing physics, animated twitching ears, a wagging tail,
 * cute front paws, expressive anime catchlight eyes, dynamic mood coloring,
 * and contextual floating aura particles (sparkles, Zzz, water droplets).
 */
@Composable
fun ModernPetCanvas(
    mood: Mood,
    modifier: Modifier = Modifier,
    isPetting: Boolean = false,
    canvasSize: Dp = 140.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pet_character_animations")

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

    // Ear twitch / wobble
    val earWiggle by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "earWiggle"
    )

    // Tail wagging animation
    val tailWag by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == Mood.ECSTATIC) 600 else 1300,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tailWag"
    )

    // Aura sparkle twinkle and floating offset
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleAlpha"
    )

    val sparkleFloat by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleFloat"
    )

    // Dedicated upward burst animation for petting hearts
    val heartProgress by animateFloatAsState(
        targetValue = if (isPetting) 1f else 0f,
        animationSpec = if (isPetting) {
            tween(durationMillis = 1400, easing = FastOutSlowInEasing)
        } else {
            snap()
        },
        label = "petting_heart_progress"
    )

    // Joyful hop when petted
    val pettingHop by animateFloatAsState(
        targetValue = if (isPetting) -6f else 0f,
        animationSpec = if (isPetting) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "petting_hop"
    )

    Box(
        modifier = modifier.size(canvasSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, (size.height / 2f) + breathBounce + pettingHop)
            val bodyRadius = size.minDimension * 0.33f

            val effectiveMood = if (isPetting) Mood.ECSTATIC else mood

            // Dynamic companion body gradient based on mood
            val bodyBrush = when (effectiveMood) {
                Mood.ECSTATIC, Mood.HAPPY -> Brush.radialGradient(
                    colors = listOf(NeonCyan, ElectricPurple),
                    center = centerOffset,
                    radius = bodyRadius * 1.3f
                )
                Mood.THIRSTY -> Brush.radialGradient(
                    colors = listOf(Color(0xFF80D8FF), Color(0xFF0091EA)),
                    center = centerOffset,
                    radius = bodyRadius * 1.3f
                )
                Mood.HUNGRY -> Brush.radialGradient(
                    colors = listOf(SunsetOrange, Color(0xFFD84315)),
                    center = centerOffset,
                    radius = bodyRadius * 1.3f
                )
                Mood.TIRED, Mood.SLEEPING -> Brush.radialGradient(
                    colors = listOf(Color(0xFF9FA8DA), Color(0xFF3949AB)),
                    center = centerOffset,
                    radius = bodyRadius * 1.3f
                )
                Mood.GRUMPY -> Brush.radialGradient(
                    colors = listOf(Color(0xFFEF9A9A), Color(0xFFC62828)),
                    center = centerOffset,
                    radius = bodyRadius * 1.3f
                )
                Mood.CONTENT -> Brush.radialGradient(
                    colors = listOf(HealthyGreen, Color(0xFF00897B)),
                    center = centerOffset,
                    radius = bodyRadius * 1.3f
                )
            }

            // 1. Draw Tail (Behind body)
            drawTail(
                center = centerOffset,
                bodyRadius = bodyRadius,
                tailAngle = if (effectiveMood == Mood.SLEEPING) 4f else tailWag,
                bodyBrush = bodyBrush
            )

            // 2. Draw Ears (Behind body)
            drawEars(
                mood = effectiveMood,
                center = centerOffset,
                bodyRadius = bodyRadius,
                earWiggle = earWiggle,
                bodyBrush = bodyBrush
            )

            // 3. Draw Organic Soft Body
            drawCircle(
                brush = bodyBrush,
                radius = bodyRadius,
                center = centerOffset
            )

            // 4. Outer Soft Aura Glow Ring
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = bodyRadius + 3f,
                center = centerOffset,
                style = Stroke(width = 2.dp.toPx())
            )

            // 5. Draw Cute Front Paws (Bottom of body)
            drawPaws(
                center = centerOffset,
                bodyRadius = bodyRadius,
                bodyBrush = bodyBrush
            )

            // 6. Draw Blushing Cheeks
            val cheekY = centerOffset.y + (bodyRadius * 0.22f)
            val cheekSpacing = bodyRadius * 0.58f
            val cheekRadius = 6.5f
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

            // 7. Draw Cute Tiny Nose
            drawCircle(
                color = SoftPink.copy(alpha = 0.85f),
                radius = 2.4f,
                center = Offset(centerOffset.x, centerOffset.y + 2f)
            )

            // 8. Draw Expressive Eyes with Anime Catchlights
            drawEyes(
                mood = effectiveMood,
                center = centerOffset,
                eyeSpacing = bodyRadius * 0.38f,
                eyeYOffset = -bodyRadius * 0.12f,
                blinkFactor = if (effectiveMood == Mood.SLEEPING) 0f else blinkProgress
            )

            // 9. Draw Mouth Expression
            drawMouth(mood = effectiveMood, center = centerOffset)

            // 10. Draw Contextual Floating Aura Particles (Ambient particles always active)
            drawAuraParticles(
                mood = effectiveMood,
                center = centerOffset,
                bodyRadius = bodyRadius,
                sparkleAlpha = sparkleAlpha,
                sparkleFloat = sparkleFloat
            )

            // 11. Dedicated Bursting Petting Hearts (Visible and floating upward on pet)
            if (heartProgress > 0.01f) {
                drawPettingHearts(
                    progress = heartProgress,
                    center = centerOffset,
                    bodyRadius = bodyRadius
                )
            }
        }
    }
}

/**
 * Animated cute creature tail with a fluffy curved silhouette.
 */
private fun DrawScope.drawTail(
    center: Offset,
    bodyRadius: Float,
    tailAngle: Float,
    bodyBrush: Brush
) {
    val pivot = Offset(center.x + (bodyRadius * 0.65f), center.y + (bodyRadius * 0.45f))

    withTransform({
        translate(pivot.x, pivot.y)
        rotate(degrees = tailAngle, pivot = Offset.Zero)
    }) {
        val tailPath = Path().apply {
            moveTo(0f, 0f)
            cubicTo(
                bodyRadius * 0.35f, -bodyRadius * 0.10f,
                bodyRadius * 0.65f, -bodyRadius * 0.40f,
                bodyRadius * 0.55f, -bodyRadius * 0.62f
            )
            cubicTo(
                bodyRadius * 0.35f, -bodyRadius * 0.68f,
                bodyRadius * 0.20f, -bodyRadius * 0.42f,
                -bodyRadius * 0.08f, -bodyRadius * 0.15f
            )
            close()
        }

        drawPath(path = tailPath, brush = bodyBrush)
        drawPath(
            path = tailPath,
            color = Color.White.copy(alpha = 0.20f),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

/**
 * Animated creature ears that tilt, droop, or perk up according to mood.
 */
private fun DrawScope.drawEars(
    mood: Mood,
    center: Offset,
    bodyRadius: Float,
    earWiggle: Float,
    bodyBrush: Brush
) {
    val (baseEarAngle, earDroop) = when (mood) {
        Mood.SLEEPING -> Pair(32f, 5f)
        Mood.TIRED -> Pair(26f, 4f)
        Mood.GRUMPY -> Pair(38f, 2f)
        Mood.ECSTATIC -> Pair(10f, -3f)
        Mood.HAPPY -> Pair(16f, -1f)
        Mood.HUNGRY, Mood.THIRSTY -> Pair(22f, 2f)
        Mood.CONTENT -> Pair(18f, 0f)
    }

    val leftPivot = Offset(center.x - (bodyRadius * 0.52f), center.y - (bodyRadius * 0.65f) + earDroop)
    val rightPivot = Offset(center.x + (bodyRadius * 0.52f), center.y - (bodyRadius * 0.65f) + earDroop)

    // Left Ear
    withTransform({
        translate(leftPivot.x, leftPivot.y)
        rotate(degrees = -baseEarAngle + earWiggle, pivot = Offset.Zero)
    }) {
        val earPath = Path().apply {
            moveTo(-bodyRadius * 0.22f, 0f)
            cubicTo(
                -bodyRadius * 0.26f, -bodyRadius * 0.35f,
                -bodyRadius * 0.20f, -bodyRadius * 0.68f,
                0f, -bodyRadius * 0.72f
            )
            cubicTo(
                bodyRadius * 0.12f, -bodyRadius * 0.58f,
                bodyRadius * 0.22f, -bodyRadius * 0.30f,
                bodyRadius * 0.16f, 0f
            )
            close()
        }
        drawPath(path = earPath, brush = bodyBrush)

        // Inner soft pink ear patch
        val innerEarPath = Path().apply {
            moveTo(-bodyRadius * 0.14f, 0f)
            cubicTo(
                -bodyRadius * 0.16f, -bodyRadius * 0.28f,
                -bodyRadius * 0.10f, -bodyRadius * 0.52f,
                0f, -bodyRadius * 0.56f
            )
            cubicTo(
                bodyRadius * 0.08f, -bodyRadius * 0.44f,
                bodyRadius * 0.14f, -bodyRadius * 0.24f,
                bodyRadius * 0.10f, 0f
            )
            close()
        }
        drawPath(path = innerEarPath, color = SoftPink.copy(alpha = 0.50f))
    }

    // Right Ear
    withTransform({
        translate(rightPivot.x, rightPivot.y)
        rotate(degrees = baseEarAngle - earWiggle, pivot = Offset.Zero)
    }) {
        val earPath = Path().apply {
            moveTo(-bodyRadius * 0.16f, 0f)
            cubicTo(
                -bodyRadius * 0.22f, -bodyRadius * 0.30f,
                -bodyRadius * 0.12f, -bodyRadius * 0.58f,
                0f, -bodyRadius * 0.72f
            )
            cubicTo(
                bodyRadius * 0.20f, -bodyRadius * 0.68f,
                bodyRadius * 0.26f, -bodyRadius * 0.35f,
                bodyRadius * 0.22f, 0f
            )
            close()
        }
        drawPath(path = earPath, brush = bodyBrush)

        // Inner soft pink ear patch
        val innerEarPath = Path().apply {
            moveTo(-bodyRadius * 0.10f, 0f)
            cubicTo(
                -bodyRadius * 0.14f, -bodyRadius * 0.24f,
                -bodyRadius * 0.08f, -bodyRadius * 0.44f,
                0f, -bodyRadius * 0.56f
            )
            cubicTo(
                bodyRadius * 0.10f, -bodyRadius * 0.52f,
                bodyRadius * 0.16f, -bodyRadius * 0.28f,
                bodyRadius * 0.14f, 0f
            )
            close()
        }
        drawPath(path = innerEarPath, color = SoftPink.copy(alpha = 0.50f))
    }
}

/**
 * Cute resting front paws at the bottom of the body.
 */
private fun DrawScope.drawPaws(
    center: Offset,
    bodyRadius: Float,
    bodyBrush: Brush
) {
    val pawY = center.y + (bodyRadius * 0.74f)
    val pawSpacing = bodyRadius * 0.36f
    val pawWidth = bodyRadius * 0.32f
    val pawHeight = bodyRadius * 0.20f

    // Left paw
    val leftPawTopLeft = Offset(center.x - pawSpacing - (pawWidth / 2f), pawY)
    drawRoundRect(
        brush = bodyBrush,
        topLeft = leftPawTopLeft,
        size = Size(pawWidth, pawHeight),
        cornerRadius = CornerRadius(pawHeight / 2f, pawHeight / 2f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.20f),
        topLeft = leftPawTopLeft,
        size = Size(pawWidth, pawHeight),
        cornerRadius = CornerRadius(pawHeight / 2f, pawHeight / 2f),
        style = Stroke(width = 1.2.dp.toPx())
    )

    // Right paw
    val rightPawTopLeft = Offset(center.x + pawSpacing - (pawWidth / 2f), pawY)
    drawRoundRect(
        brush = bodyBrush,
        topLeft = rightPawTopLeft,
        size = Size(pawWidth, pawHeight),
        cornerRadius = CornerRadius(pawHeight / 2f, pawHeight / 2f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.20f),
        topLeft = rightPawTopLeft,
        size = Size(pawWidth, pawHeight),
        cornerRadius = CornerRadius(pawHeight / 2f, pawHeight / 2f),
        style = Stroke(width = 1.2.dp.toPx())
    )
}

/**
 * Expressive character eyes with anime catchlight sparkles.
 */
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
            // Sleeping closed curved arcs with tiny eyelashes
            val arcSize = Size(18f, 10f)
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(leftEyeCenter.x - 9f, leftEyeCenter.y),
                size = arcSize,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(rightEyeCenter.x - 9f, rightEyeCenter.y),
                size = arcSize,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Mood.ECSTATIC -> {
            // Joyful upward crescents (inverted arcs)
            val arcSize = Size(18f, 11f)
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(leftEyeCenter.x - 9f, leftEyeCenter.y - 6f),
                size = arcSize,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(rightEyeCenter.x - 9f, rightEyeCenter.y - 6f),
                size = arcSize,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        else -> {
            // Expressive eyes with dynamic blinking and dual catchlights
            val eyeHeight = 15f * blinkFactor
            val eyeWidth = 11f

            // White eye base
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

            // Dark pupil with anime sparkle highlights (visible when not blinking)
            if (blinkFactor > 0.45f) {
                // Pupils
                drawCircle(
                    color = Color(0xFF1A1C24),
                    radius = 4.2f,
                    center = Offset(leftEyeCenter.x, leftEyeCenter.y + 1f)
                )
                drawCircle(
                    color = Color(0xFF1A1C24),
                    radius = 4.2f,
                    center = Offset(rightEyeCenter.x, rightEyeCenter.y + 1f)
                )

                // Major catchlight highlight (top-left)
                drawCircle(
                    color = Color.White,
                    radius = 1.8f,
                    center = Offset(leftEyeCenter.x - 1.5f, leftEyeCenter.y - 0.8f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.8f,
                    center = Offset(rightEyeCenter.x - 1.5f, rightEyeCenter.y - 0.8f)
                )

                // Minor secondary catchlight (bottom-right)
                drawCircle(
                    color = Color.White.copy(alpha = 0.80f),
                    radius = 1.0f,
                    center = Offset(leftEyeCenter.x + 1.8f, leftEyeCenter.y + 2.2f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.80f),
                    radius = 1.0f,
                    center = Offset(rightEyeCenter.x + 1.8f, rightEyeCenter.y + 2.2f)
                )
            }
        }
    }
}

/**
 * Expressive mouth shapes matching mood states.
 */
private fun DrawScope.drawMouth(mood: Mood, center: Offset) {
    val mouthCenter = Offset(center.x, center.y + 12f)

    when (mood) {
        Mood.HAPPY, Mood.ECSTATIC, Mood.CONTENT -> {
            // Gentle upward smile arc
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x - 8f, mouthCenter.y - 4f),
                size = Size(16f, 10f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Mood.HUNGRY, Mood.THIRSTY -> {
            // Small open "O" mouth
            drawCircle(
                color = Color.White,
                radius = 4.5f,
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
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Mood.SLEEPING -> {
            // Peaceful small resting line
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(mouthCenter.x - 4f, mouthCenter.y),
                end = Offset(mouthCenter.x + 4f, mouthCenter.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Contextual floating particles: sparkling stars for happy states, Zzz for sleeping,
 * and droplets/sweat for hungry/thirsty. Always preserved independently.
 */
private fun DrawScope.drawAuraParticles(
    mood: Mood,
    center: Offset,
    bodyRadius: Float,
    sparkleAlpha: Float,
    sparkleFloat: Float
) {
    when (mood) {
        Mood.ECSTATIC, Mood.HAPPY -> {
            // Twinkling diamond stars
            val starColor = Color.White.copy(alpha = sparkleAlpha)
            drawDiamondStar(
                center = Offset(center.x - (bodyRadius * 1.05f), center.y - (bodyRadius * 0.70f) + sparkleFloat),
                radius = 6f,
                color = starColor
            )
            drawDiamondStar(
                center = Offset(center.x + (bodyRadius * 0.96f), center.y - (bodyRadius * 0.82f) - sparkleFloat),
                radius = 7.5f,
                color = starColor
            )
            drawDiamondStar(
                center = Offset(center.x - (bodyRadius * 0.95f), center.y + (bodyRadius * 0.35f) - (sparkleFloat * 0.5f)),
                radius = 4.5f,
                color = starColor.copy(alpha = sparkleAlpha * 0.7f)
            )
        }
        Mood.SLEEPING -> {
            // Floating peaceful Zzz symbols
            val zColor = Color.White.copy(alpha = sparkleAlpha)
            drawZSymbol(
                topLeft = Offset(center.x + (bodyRadius * 0.72f), center.y - (bodyRadius * 0.60f) + sparkleFloat),
                size = 9f,
                color = zColor
            )
            drawZSymbol(
                topLeft = Offset(center.x + (bodyRadius * 0.94f), center.y - (bodyRadius * 0.95f) + (sparkleFloat * 1.2f)),
                size = 6.5f,
                color = zColor.copy(alpha = sparkleAlpha * 0.75f)
            )
        }
        Mood.THIRSTY -> {
            // Small floating water droplet near head
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = sparkleAlpha),
                radius = 3.5f,
                center = Offset(center.x + (bodyRadius * 0.88f), center.y - (bodyRadius * 0.70f) + sparkleFloat)
            )
        }
        Mood.HUNGRY -> {
            // Small hungry bubble near head
            drawCircle(
                color = SunsetOrange.copy(alpha = sparkleAlpha),
                radius = 3.5f,
                center = Offset(center.x + (bodyRadius * 0.88f), center.y - (bodyRadius * 0.70f) + sparkleFloat),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        else -> {
            // Subtle gentle ambient sparkle
            drawCircle(
                color = Color.White.copy(alpha = sparkleAlpha * 0.35f),
                radius = 2f,
                center = Offset(center.x + (bodyRadius * 0.95f), center.y - (bodyRadius * 0.80f) + sparkleFloat)
            )
        }
    }
}

/**
 * 4-pointed sparkle diamond star.
 */
private fun DrawScope.drawDiamondStar(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path = path, color = color, style = Fill)
}

/**
 * Minimalist vector "Z" symbol for sleeping state.
 */
private fun DrawScope.drawZSymbol(topLeft: Offset, size: Float, color: Color) {
    val strokeWidth = 1.8.dp.toPx()
    // Top horizontal bar
    drawLine(
        color = color,
        start = topLeft,
        end = Offset(topLeft.x + size, topLeft.y),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    // Diagonal stroke
    drawLine(
        color = color,
        start = Offset(topLeft.x + size, topLeft.y),
        end = Offset(topLeft.x, topLeft.y + size),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    // Bottom horizontal bar
    drawLine(
        color = color,
        start = Offset(topLeft.x, topLeft.y + size),
        end = Offset(topLeft.x + size, topLeft.y + size),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

/**
 * Dedicated upward bursting hearts that erupt when the companion is petted.
 */
private fun DrawScope.drawPettingHearts(
    progress: Float,
    center: Offset,
    bodyRadius: Float
) {
    val popScale = sin((progress * PI).toFloat()).coerceIn(0f, 1f)
    val alpha = (1f - (progress * progress)).coerceIn(0f, 1f)

    if (popScale <= 0.01f || alpha <= 0.01f) return

    // Heart 1 (Center-Top - Largest, floating high above head)
    val h1Center = Offset(
        x = center.x + (sin(progress * PI * 2f).toFloat() * 4f),
        y = center.y - (bodyRadius * 0.90f) - (progress * 42f)
    )
    drawHeart(
        center = h1Center,
        size = 20f * popScale,
        color = Color(0xFFFF2D6F).copy(alpha = alpha) // Radiant vibrant ruby-pink
    )

    // Heart 2 (Left - Drifting up and left)
    val h2Progress = (progress * 1.15f).coerceIn(0f, 1f)
    val h2Scale = sin((h2Progress * PI).toFloat()).coerceIn(0f, 1f)
    val h2Alpha = (1f - (h2Progress * h2Progress)).coerceIn(0f, 1f)
    if (h2Scale > 0.01f && h2Alpha > 0.01f) {
        val h2Center = Offset(
            x = center.x - (bodyRadius * 0.72f) - (progress * 18f),
            y = center.y - (bodyRadius * 0.55f) - (progress * 34f)
        )
        drawHeart(
            center = h2Center,
            size = 14f * h2Scale,
            color = SoftPink.copy(alpha = h2Alpha)
        )
    }

    // Heart 3 (Right - Drifting up and right)
    val h3Progress = (progress * 1.10f).coerceIn(0f, 1f)
    val h3Scale = sin((h3Progress * PI).toFloat()).coerceIn(0f, 1f)
    val h3Alpha = (1f - (h3Progress * h3Progress)).coerceIn(0f, 1f)
    if (h3Scale > 0.01f && h3Alpha > 0.01f) {
        val h3Center = Offset(
            x = center.x + (bodyRadius * 0.72f) + (progress * 18f),
            y = center.y - (bodyRadius * 0.60f) - (progress * 38f)
        )
        drawHeart(
            center = h3Center,
            size = 15f * h3Scale,
            color = Color(0xFFFF6584).copy(alpha = h3Alpha)
        )
    }
}

/**
 * Vector heart icon for petting feedback.
 */
private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val r = size / 2f
    val path = Path().apply {
        moveTo(center.x, center.y + (r * 0.8f))
        cubicTo(
            center.x - (r * 1.2f), center.y + (r * 0.1f),
            center.x - (r * 1.2f), center.y - (r * 0.8f),
            center.x - (r * 0.5f), center.y - (r * 0.8f)
        )
        cubicTo(
            center.x - (r * 0.1f), center.y - (r * 0.8f),
            center.x, center.y - (r * 0.4f),
            center.x, center.y - (r * 0.3f)
        )
        cubicTo(
            center.x, center.y - (r * 0.4f),
            center.x + (r * 0.1f), center.y - (r * 0.8f),
            center.x + (r * 0.5f), center.y - (r * 0.8f)
        )
        cubicTo(
            center.x + (r * 1.2f), center.y - (r * 0.8f),
            center.x + (r * 1.2f), center.y + (r * 0.1f),
            center.x, center.y + (r * 0.8f)
        )
        close()
    }
    drawPath(path = path, color = color, style = Fill)
}


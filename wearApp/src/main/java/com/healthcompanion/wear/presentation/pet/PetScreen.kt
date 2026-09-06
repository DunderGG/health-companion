// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.healthcompanion.core.ui.components.MealActionToken
import com.healthcompanion.core.ui.components.ModernPetCanvas
import com.healthcompanion.core.ui.components.VitalsRing
import com.healthcompanion.core.ui.components.WaterActionToken

@Composable
fun PetScreen(
    viewModel: PetViewModel,
    modifier: Modifier = Modifier,
    showSensorChip: Boolean = false,
    onSensorChipClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is PetUiState.Loading -> {
                CircularProgressIndicator()
            }
            is PetUiState.Success -> {
                val pet = state.pet
                val mood = state.mood

                // Vitals Ring wrapping the circular watch screen
                VitalsRing(vitals = pet.vitals) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Degraded-mode sensor chip (shown when permissions are denied)
                        if (showSensorChip) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .clickable { onSensorChipClick() }
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⚠ Enable sensors",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        // Companion Name and Stage
                        Text(
                            text = pet.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "${pet.stage.name} • ${pet.archetype.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Modern Animated Companion Sprite (Clickable for Petting)
                        val haptic = LocalHapticFeedback.current
                        val petInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = petInteractionSource,
                                    indication = null
                                ) {
                                    if (viewModel.petCompanion()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            ModernPetCanvas(
                                mood = mood,
                                isPetting = state.isPettingFeedbackActive,
                                canvasSize = 110.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Micro Quick Action Buttons (Meal & Water)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MealActionToken(
                                onClick = { viewModel.logMeal(isHealthy = true) }
                            )

                            WaterActionToken(
                                onClick = { viewModel.logWater(250) }
                            )
                        }
                    }
                }
            }
        }
    }
}


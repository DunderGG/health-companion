// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.healthcompanion.core.model.Vitals
import com.healthcompanion.core.ui.theme.BrightAqua
import com.healthcompanion.core.ui.theme.ElectricPurple
import com.healthcompanion.core.ui.theme.HealthyGreen
import com.healthcompanion.core.ui.theme.SoftPink
import com.healthcompanion.core.ui.theme.SunsetOrange
import com.healthcompanion.wear.R

/**
 * One row of the Vitals page.
 *
 * @property labelRes Vital name.
 * @property percent Value shown as a whole percentage, `0..100`.
 * @property color Same accent colour as the vital's arc on the pet screen's ring.
 */
data class VitalLine(
    @param:StringRes val labelRes: Int,
    val percent: Int,
    val color: Color
)

/**
 * All five vitals in display order, including happiness, which has no arc on the ring (DD-45).
 * Percentages are truncated like on the tile and complication, so all surfaces show the same number.
 */
fun vitalsBreakdown(vitals: Vitals): List<VitalLine> = listOf(
    VitalLine(R.string.vital_energy, vitals.energy.asPercent(), ElectricPurple),
    VitalLine(R.string.vital_hunger, vitals.hunger.asPercent(), SunsetOrange),
    VitalLine(R.string.vital_hydration, vitals.hydration.asPercent(), BrightAqua),
    VitalLine(R.string.vital_fitness, vitals.fitness.asPercent(), HealthyGreen),
    VitalLine(R.string.vital_happiness, vitals.happiness.asPercent(), SoftPink)
)

/** Overall health as a whole percentage, as on the tile and complication. */
fun overallPercent(vitals: Vitals): Int = vitals.overallHealth.asPercent()

private fun Float.asPercent(): Int = toInt().coerceIn(0, 100)

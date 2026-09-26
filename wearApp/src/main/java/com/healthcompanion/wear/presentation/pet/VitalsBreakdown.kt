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
import com.healthcompanion.wear.toDisplayPercent

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
 * Percentages are rounded the same way as on the tile and complication ([toDisplayPercent]), so all surfaces agree.
 */
fun vitalsBreakdown(vitals: Vitals): List<VitalLine> = listOf(
    VitalLine(R.string.vital_energy, vitals.energy.toDisplayPercent(), ElectricPurple),
    VitalLine(R.string.vital_hunger, vitals.hunger.toDisplayPercent(), SunsetOrange),
    VitalLine(R.string.vital_hydration, vitals.hydration.toDisplayPercent(), BrightAqua),
    VitalLine(R.string.vital_fitness, vitals.fitness.toDisplayPercent(), HealthyGreen),
    VitalLine(R.string.vital_happiness, vitals.happiness.toDisplayPercent(), SoftPink)
)

/** Overall health as a whole percentage, as on the tile and complication. */
fun overallPercent(vitals: Vitals): Int = vitals.overallHealth.toDisplayPercent()

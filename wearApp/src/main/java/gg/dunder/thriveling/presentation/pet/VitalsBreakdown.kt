// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.presentation.pet

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import gg.dunder.thriveling.core.model.Vitals
import gg.dunder.thriveling.core.ui.theme.BrightAqua
import gg.dunder.thriveling.core.ui.theme.ElectricPurple
import gg.dunder.thriveling.core.ui.theme.HealthyGreen
import gg.dunder.thriveling.core.ui.theme.SoftPink
import gg.dunder.thriveling.core.ui.theme.SunsetOrange
import gg.dunder.thriveling.R
import gg.dunder.thriveling.toDisplayPercent

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

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import com.healthcompanion.core.model.Vitals
import com.healthcompanion.core.ui.theme.BrightAqua
import com.healthcompanion.wear.R
import org.junit.Assert.assertEquals
import org.junit.Test

class VitalsBreakdownTest {

    private val vitals = Vitals(
        energy = 72.9f,
        hunger = 64f,
        hydration = 41.5f,
        fitness = 80f,
        happiness = 100f,
        lastUpdatedTimestamp = 0L
    )

    @Test
    fun `all five vitals are listed in a fixed order, including happiness`() {
        assertEquals(
            listOf(R.string.vital_energy, R.string.vital_hunger, R.string.vital_hydration, R.string.vital_fitness, R.string.vital_happiness),
            vitalsBreakdown(vitals).map { it.labelRes }
        )
    }

    @Test
    fun `values are truncated to whole percentages, like the tile and complication`() {
        assertEquals(listOf(72, 64, 41, 80, 100), vitalsBreakdown(vitals).map { it.percent })
        // (72.9 + 64 + 41.5 + 80 + 100) / 5 = 71.68
        assertEquals(71, overallPercent(vitals))
    }

    @Test
    fun `each row uses its ring colour`() {
        assertEquals(BrightAqua, vitalsBreakdown(vitals).single { it.labelRes == R.string.vital_hydration }.color)
    }
}

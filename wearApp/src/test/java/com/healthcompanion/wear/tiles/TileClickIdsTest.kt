// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.tiles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileClickIdsTest {

    @Test
    fun `a tap on a freshly rendered water chip is new`() {
        assertTrue(TileClickIds.isNewWaterClick(TileClickIds.logWater(2_000), lastHandledId = TileClickIds.logWater(1_000)))
    }

    @Test
    fun `the first tap ever is new`() {
        assertTrue(TileClickIds.isNewWaterClick(TileClickIds.logWater(1_000), lastHandledId = null))
    }

    @Test
    fun `a later request repeating the handled id is not logged again`() {
        val id = TileClickIds.logWater(1_000)
        assertFalse(TileClickIds.isNewWaterClick(id, lastHandledId = id))
    }

    @Test
    fun `requests without a click are ignored`() {
        assertFalse(TileClickIds.isNewWaterClick("", lastHandledId = null))
    }

    @Test
    fun `other clickables are ignored`() {
        assertFalse(TileClickIds.isNewWaterClick("open_app", lastHandledId = null))
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.ambient

import com.healthcompanion.core.ui.theme.DisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BurnInShiftTest {

    private fun ambient(updateCount: Int, burnIn: Boolean = true) =
        AmbientState.Ambient(burnInProtectionRequired = burnIn, lowBitAmbient = false, updateCount = updateCount)

    @Test
    fun `interactive screens are never shifted`() {
        assertEquals(0 to 0, BurnInShift.offsetDp(AmbientState.Interactive))
    }

    @Test
    fun `displays without burn-in protection are never shifted`() {
        (0..20).forEach { assertEquals(0 to 0, BurnInShift.offsetDp(ambient(it, burnIn = false))) }
    }

    @Test
    fun `each update moves the screen, one step at a time, within the bound`() {
        (0..20).forEach { count ->
            val current = BurnInShift.offsetDp(ambient(count))
            val next = BurnInShift.offsetDp(ambient(count + 1))
            assertNotEquals(current, next)
            assertTrue(abs(current.first) <= BurnInShift.MAX_SHIFT_DP && abs(current.second) <= BurnInShift.MAX_SHIFT_DP)
            assertTrue(abs(next.first - current.first) <= BurnInShift.MAX_SHIFT_DP)
            assertTrue(abs(next.second - current.second) <= BurnInShift.MAX_SHIFT_DP)
        }
    }

    @Test
    fun `the first ambient frame is centred`() {
        assertEquals(0 to 0, BurnInShift.offsetDp(ambient(0)))
    }

    @Test
    fun `low-bit displays get the white ambient style`() {
        val lowBit = AmbientState.Ambient(burnInProtectionRequired = false, lowBitAmbient = true)
        assertEquals(DisplayMode.AMBIENT_LOW_BIT, lowBit.displayMode)
        assertEquals(DisplayMode.AMBIENT, ambient(0).displayMode)
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayPercentTest {

    @Test
    fun `a vital just filled to the top shows 100, not 99`() {
        assertEquals(100, 99.99f.toDisplayPercent())
    }

    @Test
    fun `values round to the nearest whole percent`() {
        assertEquals(41, 41.49f.toDisplayPercent())
        assertEquals(42, 41.5f.toDisplayPercent())
    }

    @Test
    fun `out-of-range and NaN values are clamped`() {
        assertEquals(0, (-3f).toDisplayPercent())
        assertEquals(100, 120f.toDisplayPercent())
        assertEquals(0, Float.NaN.toDisplayPercent())
    }
}

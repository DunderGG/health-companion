// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import org.junit.Assert.assertEquals
import org.junit.Test

class StepCounterSpreaderTest {

    @Test
    fun `first reading only sets the baseline`() {
        val spreader = StepCounterSpreader()

        assertEquals(emptyList<Long>(), spreader.onCount(count = 12_345, eventMillis = 10_000))
    }

    @Test
    fun `an increase is spread evenly up to the current reading`() {
        val spreader = StepCounterSpreader()
        spreader.onCount(count = 100, eventMillis = 10_000)

        val steps = spreader.onCount(count = 104, eventMillis = 12_000)

        assertEquals(listOf(10_500L, 11_000L, 11_500L, 12_000L), steps)
    }

    @Test
    fun `no change or a counter reset produces no steps`() {
        val spreader = StepCounterSpreader()
        spreader.onCount(count = 100, eventMillis = 10_000)

        assertEquals(emptyList<Long>(), spreader.onCount(count = 100, eventMillis = 11_000))
        assertEquals(emptyList<Long>(), spreader.onCount(count = 3, eventMillis = 12_000))
        // The reset reading becomes the new baseline.
        assertEquals(listOf(13_000L), spreader.onCount(count = 4, eventMillis = 13_000))
    }

    @Test
    fun `large jumps keep only the most recent steps at the real cadence`() {
        val spreader = StepCounterSpreader()
        spreader.onCount(count = 0, eventMillis = 0)

        // 80 steps in 30 s = 160 steps/min (running), delivered late in one reading
        val steps = spreader.onCount(count = 80, eventMillis = 30_000)

        assertEquals(StepCounterSpreader.MAX_STEPS_PER_EVENT, steps.size)
        assertEquals(30_000L, steps.last())
        assertEquals(375L, steps.last() - steps[steps.size - 2])
    }
}

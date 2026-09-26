// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTotalTrackerTest {

    private val day = 20_000L

    @Test
    fun `first reading consumes everything since midnight in whole units`() {
        val update = DailyTotalTracker.consume(null, day, total = 1_050.0, granularity = 200.0)

        assertEquals(1_000.0, update.consumed, 0.0)
        assertEquals(DailyTotalBaseline(day, 1_000.0), update.baseline)
    }

    @Test
    fun `same total delivered again consumes nothing`() {
        val first = DailyTotalTracker.consume(null, day, total = 8_000.0, granularity = 200.0)
        val second = DailyTotalTracker.consume(first.baseline, day, total = 8_000.0, granularity = 200.0)

        assertEquals(8_000.0, first.consumed, 0.0)
        assertEquals(0.0, second.consumed, 0.0)
    }

    @Test
    fun `remainder below granularity carries over to later readings`() {
        val baseline = DailyTotalBaseline(day, 1_000.0)

        val small = DailyTotalTracker.consume(baseline, day, total = 1_150.0, granularity = 200.0)
        assertEquals(0.0, small.consumed, 0.0)
        assertEquals(baseline, small.baseline)

        val later = DailyTotalTracker.consume(small.baseline, day, total = 1_250.0, granularity = 200.0)
        assertEquals(200.0, later.consumed, 0.0)
        assertEquals(DailyTotalBaseline(day, 1_200.0), later.baseline)
    }

    @Test
    fun `new day starts counting from zero`() {
        val yesterday = DailyTotalBaseline(day, 9_800.0)

        val update = DailyTotalTracker.consume(yesterday, day + 1, total = 450.0, granularity = 200.0)

        assertEquals(400.0, update.consumed, 0.0)
        assertEquals(DailyTotalBaseline(day + 1, 400.0), update.baseline)
    }

    @Test
    fun `stale reading from an earlier day is ignored`() {
        val today = DailyTotalBaseline(day + 1, 400.0)

        val update = DailyTotalTracker.consume(today, day, total = 12_000.0, granularity = 200.0)

        assertEquals(0.0, update.consumed, 0.0)
        assertEquals(today, update.baseline)
    }

    @Test
    fun `out-of-order older total is ignored and never causes double counting`() {
        val baseline = DailyTotalBaseline(day, 5_000.0)

        val stale = DailyTotalTracker.consume(baseline, day, total = 3_000.0, granularity = 200.0)
        assertEquals(0.0, stale.consumed, 0.0)
        assertEquals(baseline, stale.baseline)

        val newer = DailyTotalTracker.consume(stale.baseline, day, total = 5_400.0, granularity = 200.0)
        assertEquals(400.0, newer.consumed, 0.0)
    }
}

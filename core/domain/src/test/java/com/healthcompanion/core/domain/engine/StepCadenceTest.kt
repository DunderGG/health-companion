// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.engine

import com.healthcompanion.core.model.PetActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class StepCadenceTest {

    private val start = 1_000_000L

    /** Records [count] steps [intervalMillis] apart starting at [from]; "now" is each step's own time. */
    private fun StepCadence.steps(count: Int, intervalMillis: Long, from: Long = start): StepCadence {
        var cadence = this
        repeat(count) { i ->
            val t = from + i * intervalMillis
            cadence = cadence.record(t, t)
        }
        return cadence
    }

    @Test
    fun `fewer than three steps keep the pet idle`() {
        val cadence = StepCadence().steps(count = 2, intervalMillis = 500)

        assertEquals(PetActivity.IDLE, cadence.activity)
    }

    @Test
    fun `walking cadence makes the pet walk after three steps`() {
        // 500 ms per step = 120 steps/min
        val cadence = StepCadence().steps(count = 3, intervalMillis = 500)

        assertEquals(PetActivity.WALKING, cadence.activity)
        assertEquals(120.0, cadence.cadenceSpm(), 0.01)
    }

    @Test
    fun `running cadence makes the pet run`() {
        // 375 ms per step = 160 steps/min
        val cadence = StepCadence().steps(count = 6, intervalMillis = 375)

        assertEquals(PetActivity.RUNNING, cadence.activity)
    }

    @Test
    fun `running needs the enter threshold but is kept down to the exit threshold`() {
        // 435 ms per step ≈ 138 steps/min: between exit (130) and enter (145)
        val walker = StepCadence().steps(count = 6, intervalMillis = 435)
        assertEquals(PetActivity.WALKING, walker.activity)

        val runner = StepCadence().steps(count = 6, intervalMillis = 375)
        val slowing = runner.steps(count = 12, intervalMillis = 435, from = start + 6 * 375)
        assertEquals(PetActivity.RUNNING, slowing.activity)

        // 500 ms per step = 120 steps/min: below the exit threshold
        val walking = slowing.steps(count = 14, intervalMillis = 500, from = start + 6 * 375 + 12 * 435)
        assertEquals(PetActivity.WALKING, walking.activity)
    }

    @Test
    fun `pet goes idle once steps stop for longer than the idle timeout`() {
        val walking = StepCadence().steps(count = 5, intervalMillis = 500)
        val lastStep = start + 4 * 500

        assertEquals(PetActivity.WALKING, walking.advance(lastStep + StepCadence.IDLE_AFTER_MS).activity)
        assertEquals(PetActivity.IDLE, walking.advance(lastStep + StepCadence.IDLE_AFTER_MS + 1).activity)
    }

    @Test
    fun `a pause starts a new burst, so one step after it does not resume walking`() {
        val walking = StepCadence().steps(count = 5, intervalMillis = 500)
        val afterPause = start + 4 * 500 + 3_000

        val resumed = walking.record(afterPause, afterPause)

        assertEquals(PetActivity.IDLE, resumed.activity)
        assertEquals(listOf(afterPause), resumed.recentSteps)
    }

    @Test
    fun `steps older than the window are dropped`() {
        val cadence = StepCadence().steps(count = 30, intervalMillis = 500)
        val now = start + 29 * 500

        cadence.recentSteps.forEach { assert(now - it <= StepCadence.WINDOW_MS) }
        assertEquals(13, cadence.recentSteps.size)
    }

    @Test
    fun `out-of-order steps are sorted and future steps clamped to now`() {
        val cadence = StepCadence()
            .record(start + 1_000, start + 1_000)
            .record(start + 500, start + 1_000)
            .record(start + 9_999, start + 1_500)

        assertEquals(listOf(start + 500, start + 1_000, start + 1_500), cadence.recentSteps)
        assertEquals(PetActivity.WALKING, cadence.activity)
    }

    @Test
    fun `simultaneous steps give no cadence instead of dividing by zero`() {
        val cadence = StepCadence().steps(count = 3, intervalMillis = 0)

        assertEquals(0.0, cadence.cadenceSpm(), 0.0)
        assertEquals(PetActivity.WALKING, cadence.activity)
    }
}

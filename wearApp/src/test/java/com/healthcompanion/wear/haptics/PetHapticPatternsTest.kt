// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetHapticPatternsTest {

    private val all = PetHapticEvent.entries.map { PetHapticPatterns.of(it) }

    @Test
    fun `fallback waveforms are well formed`() {
        all.forEach { pattern ->
            assertEquals(pattern.fallbackTimings.size, pattern.fallbackAmplitudes.size)
            assertEquals(0L, pattern.fallbackTimings.first())
            assertTrue(pattern.fallbackAmplitudes.all { it in 0..255 })
            assertTrue(pattern.steps.all { it.scale in 0f..1f && it.delayMs >= 0 })
        }
    }

    @Test
    fun `patterns get longer with importance, and stay short`() {
        val durations = PetHapticEvent.entries.sortedBy { it.priority }.map { PetHapticPatterns.of(it).fallbackDurationMs }
        assertEquals(durations.sorted(), durations)
        assertTrue(durations.last() <= 1_000)
    }

    @Test
    fun `a goal does not cut off an evolution that is still playing`() {
        val arbiter = HapticArbiter()
        assertTrue(arbiter.tryStart(PetHapticEvent.EVOLUTION, nowMs = 0))
        assertFalse(arbiter.tryStart(PetHapticEvent.GOAL_REACHED, nowMs = 100))
    }

    @Test
    fun `a more important pattern replaces a less important one`() {
        val arbiter = HapticArbiter()
        assertTrue(arbiter.tryStart(PetHapticEvent.GOAL_REACHED, nowMs = 0))
        assertTrue(arbiter.tryStart(PetHapticEvent.EVOLUTION, nowMs = 50))
    }

    @Test
    fun `anything may play once the previous pattern has finished`() {
        val arbiter = HapticArbiter()
        assertTrue(arbiter.tryStart(PetHapticEvent.EVOLUTION, nowMs = 0))
        assertTrue(arbiter.tryStart(PetHapticEvent.PETTING, nowMs = PetHapticPatterns.EVOLUTION.fallbackDurationMs))
    }

    @Test
    fun `a vital filling up does not cut off a purr, but a purr replaces it`() {
        val arbiter = HapticArbiter()
        assertTrue(arbiter.tryStart(PetHapticEvent.PETTING, nowMs = 0))
        assertFalse(arbiter.tryStart(PetHapticEvent.VITAL_FILLED, nowMs = 50))
        assertTrue(arbiter.tryStart(PetHapticEvent.VITAL_FILLED, nowMs = 1_000))
        assertTrue(arbiter.tryStart(PetHapticEvent.PETTING, nowMs = 1_050))
    }
}

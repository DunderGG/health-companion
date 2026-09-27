// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class NightWindowTest {

    private val utc = ZoneOffset.UTC
    private val window = NightWindow(startHour = 22, endHour = 7)

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `window wrapping midnight covers late evening and early morning`() {
        assertTrue(window.isNight(at("2026-01-10T23:30:00Z"), utc))
        assertTrue(window.isNight(at("2026-01-11T06:59:00Z"), utc))
        assertFalse(window.isNight(at("2026-01-11T07:00:00Z"), utc))
        assertFalse(window.isNight(at("2026-01-11T21:59:00Z"), utc))
    }

    @Test
    fun `next daytime is now by day and the night's end at night`() {
        assertEquals(at("2026-01-10T12:00:00Z"), window.nextDaytime(at("2026-01-10T12:00:00Z"), utc))
        assertEquals(at("2026-01-11T07:00:00Z"), window.nextDaytime(at("2026-01-10T22:00:00Z"), utc))
        assertEquals(at("2026-01-11T07:00:00Z"), window.nextDaytime(at("2026-01-11T03:15:00Z"), utc))
    }

    @Test
    fun `night is evaluated in the given zone`() {
        val instant = at("2026-01-10T20:00:00Z")

        assertFalse(window.isNight(instant, utc))
        assertTrue(window.isNight(instant, ZoneId.of("Asia/Tokyo"))) // 05:00 local
    }

    @Test
    fun `segments split a full day into day and night parts that cover the interval exactly`() {
        val from = at("2026-01-10T12:00:00Z")
        val to = at("2026-01-11T12:00:00Z")

        val segments = window.segments(from, to, utc)

        assertEquals(
            listOf(
                NightWindow.Segment(from, at("2026-01-10T22:00:00Z"), isNight = false),
                NightWindow.Segment(at("2026-01-10T22:00:00Z"), at("2026-01-11T07:00:00Z"), isNight = true),
                NightWindow.Segment(at("2026-01-11T07:00:00Z"), to, isNight = false)
            ),
            segments
        )
    }

    @Test
    fun `interval inside a single period yields one segment`() {
        val segments = window.segments(at("2026-01-10T23:00:00Z"), at("2026-01-11T01:00:00Z"), utc)

        assertEquals(1, segments.size)
        assertTrue(segments.single().isNight)
    }
}

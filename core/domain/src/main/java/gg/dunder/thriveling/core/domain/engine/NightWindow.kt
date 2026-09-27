// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.engine

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The companion's nightly rest period in local time, e.g. 22:00–07:00.
 *
 * During the window the pet sleeps: energy recovers instead of decaying and the mood is
 * [gg.dunder.thriveling.core.model.Mood.SLEEPING]. Windows may wrap past midnight (`startHour > endHour`).
 *
 * @property startHour Local hour (0–23) the night starts.
 * @property endHour Local hour (0–23) the night ends (exclusive).
 */
data class NightWindow(
    val startHour: Int = 22,
    val endHour: Int = 7
) {
    init {
        require(startHour in 0..23 && endHour in 0..23) { "Hours must be in 0..23" }
        require(startHour != endHour) { "Night window must not be empty" }
    }

    /** A contiguous stretch of time that is entirely night or entirely day. */
    data class Segment(val startMillis: Long, val endMillis: Long, val isNight: Boolean) {
        val durationMillis: Long get() = endMillis - startMillis
    }

    /** Whether [epochMillis] falls inside the window in [zone]. */
    fun isNight(epochMillis: Long, zone: ZoneId): Boolean {
        val hour = Instant.ofEpochMilli(epochMillis).atZone(zone).hour
        return if (startHour > endHour) {
            hour >= startHour || hour < endHour
        } else {
            hour in startHour until endHour
        }
    }

    /**
     * Splits `[fromMillis, toMillis)` into chronological day/night segments, so rates that differ
     * between day and night can be applied piecewise (with clamping per segment).
     */
    fun segments(fromMillis: Long, toMillis: Long, zone: ZoneId): List<Segment> {
        val result = mutableListOf<Segment>()
        var cursor = fromMillis
        while (cursor < toMillis) {
            val boundary = nextBoundaryAfter(cursor, zone)
            val end = minOf(boundary, toMillis)
            result += Segment(cursor, end, isNight(cursor, zone))
            cursor = end
        }
        return result
    }

    /** The first daytime instant at or after [epochMillis]: [epochMillis] itself by day, otherwise the night's end. */
    fun nextDaytime(epochMillis: Long, zone: ZoneId): Long {
        if (!isNight(epochMillis, zone)) return epochMillis
        return segments(epochMillis, epochMillis + TWO_DAYS_MS, zone).first { !it.isNight }.startMillis
    }

    /** First instant strictly after [epochMillis] at which the local hour becomes [startHour] or [endHour]. */
    private fun nextBoundaryAfter(epochMillis: Long, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = now.toLocalDate()
        return listOf(today, today.plusDays(1))
            .flatMap { date -> listOf(startHour, endHour).map { hour -> ZonedDateTime.of(date, java.time.LocalTime.of(hour, 0), zone) } }
            .map { it.toInstant().toEpochMilli() }
            .filter { it > epochMillis }
            .min()
    }

    companion object {
        /** Default companion bedtime: 22:00–07:00 local time. */
        val DEFAULT = NightWindow()

        private const val TWO_DAYS_MS = 48L * 60 * 60 * 1000
    }
}

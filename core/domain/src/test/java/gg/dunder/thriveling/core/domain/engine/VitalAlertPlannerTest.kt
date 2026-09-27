// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.engine

import gg.dunder.thriveling.core.model.Vitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class VitalAlertPlannerTest {

    private val zone = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val noon = Instant.parse("2026-01-10T12:00:00Z").toEpochMilli()
    private val lateEvening = Instant.parse("2026-01-10T23:00:00Z").toEpochMilli()
    private val nextMorning = Instant.parse("2026-01-11T07:00:00Z").toEpochMilli()

    private fun vitals(hydration: Float = 100f, hunger: Float = 100f) =
        Vitals(hydration = hydration, hunger = hunger, lastUpdatedTimestamp = noon)

    @Test
    fun `healthy vitals schedule a check at the earliest predicted crossing`() {
        // Hydration 40 → 25 at 3/h = 5 h. Hunger 100 → 25 at 2.5/h = 30 h.
        val plan = VitalAlertPlanner.plan(vitals(hydration = 40f), noon, zone, notified = emptySet())

        assertEquals(emptySet<CriticalVital>(), plan.newlyCritical)
        assertEquals(noon + 5 * hour + VitalAlertPlanner.CROSSING_MARGIN_MS, plan.nextCheckAtMillis)
    }

    @Test
    fun `a critical vital is alerted once and then stays quiet`() {
        val first = VitalAlertPlanner.plan(vitals(hydration = 20f), noon, zone, notified = emptySet())
        assertEquals(setOf(CriticalVital.HYDRATION), first.newlyCritical)
        assertEquals(setOf(CriticalVital.HYDRATION), first.notified)

        val second = VitalAlertPlanner.plan(vitals(hydration = 15f), noon + hour, zone, first.notified)
        assertEquals(emptySet<CriticalVital>(), second.newlyCritical)
        assertEquals(setOf(CriticalVital.HYDRATION), second.notified)
    }

    @Test
    fun `recovering re-arms the vital and reports it as restored`() {
        val plan = VitalAlertPlanner.plan(
            vitals(hydration = 45f),
            noon,
            zone,
            notified = setOf(CriticalVital.HYDRATION)
        )

        assertEquals(setOf(CriticalVital.HYDRATION), plan.restored)
        assertEquals(emptySet<CriticalVital>(), plan.notified)
    }

    @Test
    fun `no alerts at night, a critical vital is checked again when the night ends`() {
        val plan = VitalAlertPlanner.plan(vitals(hunger = 10f), lateEvening, zone, notified = emptySet())

        assertEquals(emptySet<CriticalVital>(), plan.newlyCritical)
        assertEquals(emptySet<CriticalVital>(), plan.notified)
        assertEquals(nextMorning, plan.nextCheckAtMillis)
    }

    @Test
    fun `a crossing predicted during the night is deferred to its end`() {
        // Hydration 55 at 20:00 crosses 25 after 10 h, at 06:00 (inside the night).
        val evening = Instant.parse("2026-01-10T20:00:00Z").toEpochMilli()
        val plan = VitalAlertPlanner.plan(vitals(hydration = 55f), evening, zone, notified = emptySet())

        assertEquals(nextMorning, plan.nextCheckAtMillis)
    }

    @Test
    fun `nothing left to watch schedules no check`() {
        val plan = VitalAlertPlanner.plan(
            vitals(hydration = 5f, hunger = 5f),
            noon,
            zone,
            notified = setOf(CriticalVital.HYDRATION, CriticalVital.HUNGER)
        )

        assertNull(plan.nextCheckAtMillis)
    }

    @Test
    fun `both vitals can become critical in the same check`() {
        val plan = VitalAlertPlanner.plan(vitals(hydration = 10f, hunger = 10f), noon, zone, notified = emptySet())

        assertEquals(setOf(CriticalVital.HYDRATION, CriticalVital.HUNGER), plan.newlyCritical)
    }

    @Test
    fun `thresholds match the thirsty and hungry moods`() {
        assertEquals(MoodCalculator.THIRSTY_BELOW, CriticalVital.HYDRATION.threshold)
        assertEquals(MoodCalculator.HUNGRY_BELOW, CriticalVital.HUNGER.threshold)
    }
}

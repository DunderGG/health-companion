// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.engine

import gg.dunder.thriveling.core.model.HabitEvent
import gg.dunder.thriveling.core.model.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareCooldownTest {

    private val now = 1_000_000_000L
    private val minute = 60_000L

    @Test
    fun `only care by hand has a cooldown, snacks share the food one`() {
        assertEquals(CareAction.FOOD, CareCooldown.actionOf(HabitType.Meal(isHealthy = true)))
        assertEquals(CareAction.FOOD, CareCooldown.actionOf(HabitType.Meal(isHealthy = false)))
        assertEquals(CareAction.WATER, CareCooldown.actionOf(HabitType.Hydration(250)))
        assertEquals(CareAction.PETTING, CareCooldown.actionOf(HabitType.PettingInteraction(1.0f)))
        assertNull(CareCooldown.actionOf(HabitType.Steps(1_000)))
        assertNull(CareCooldown.actionOf(HabitType.HeartRate(bpm = 70f)))
    }

    @Test
    fun `an action is available an hour after its latest use`() {
        val events = listOf(
            HabitEvent(HabitType.Hydration(250), now - 50 * minute),
            HabitEvent(HabitType.Hydration(250), now - 20 * minute),
            HabitEvent(HabitType.Steps(500), now - 5 * minute)
        )

        assertEquals(now + 40 * minute, CareCooldown.availableAt(CareAction.WATER, events, now))
        assertNull(CareCooldown.availableAt(CareAction.FOOD, events, now))
        assertNull(CareCooldown.availableAt(CareAction.WATER, events, now + 40 * minute))
    }

    @Test
    fun `an event after now, from a clock set back, does not block the action`() {
        val events = listOf(HabitEvent(HabitType.Meal(isHealthy = true), now + 3 * 60 * minute))

        assertNull(CareCooldown.availableAt(CareAction.FOOD, events, now))
    }
}

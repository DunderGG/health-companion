// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PassiveSensorPlannerTest {

    @Test
    fun `activity permission alone still registers steps and floors`() {
        val sensors = PassiveSensorPlanner.permittedSensors(
            activityRecognitionGranted = true,
            heartRateReadableInBackground = false
        )

        assertEquals(setOf(PassiveSensor.STEPS, PassiveSensor.FLOORS), sensors)
    }

    @Test
    fun `heart rate alone registers only heart rate`() {
        val sensors = PassiveSensorPlanner.permittedSensors(
            activityRecognitionGranted = false,
            heartRateReadableInBackground = true
        )

        assertEquals(setOf(PassiveSensor.HEART_RATE), sensors)
    }

    @Test
    fun `nothing granted registers nothing`() {
        assertEquals(emptySet<PassiveSensor>(), PassiveSensorPlanner.permittedSensors(false, false))
    }

    @Test
    fun `registration key is order independent`() {
        val a = PassiveSensorPlanner.registrationKey(setOf(PassiveSensor.FLOORS, PassiveSensor.STEPS), bootCount = 7)
        val b = PassiveSensorPlanner.registrationKey(setOf(PassiveSensor.STEPS, PassiveSensor.FLOORS), bootCount = 7)

        assertEquals(a, b)
    }

    @Test
    fun `registration key changes after reboot or permission change`() {
        val base = PassiveSensorPlanner.registrationKey(setOf(PassiveSensor.STEPS), bootCount = 7)

        assertNotEquals(base, PassiveSensorPlanner.registrationKey(setOf(PassiveSensor.STEPS), bootCount = 8))
        assertNotEquals(
            base,
            PassiveSensorPlanner.registrationKey(setOf(PassiveSensor.STEPS, PassiveSensor.HEART_RATE), bootCount = 7)
        )
    }
}

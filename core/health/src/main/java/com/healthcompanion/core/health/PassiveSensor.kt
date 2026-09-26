// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

/**
 * Passive sensor streams the companion can consume, independent of Health Services types
 * so the permission and registration rules stay pure and unit-testable.
 */
enum class PassiveSensor {
    /** `STEPS_DAILY` — requires `ACTIVITY_RECOGNITION`. */
    STEPS,

    /** `FLOORS_DAILY` — requires `ACTIVITY_RECOGNITION`. */
    FLOORS,

    /** `HEART_RATE_BPM` — requires the heart-rate permission *and*, on API 33+, background body-sensor access. */
    HEART_RATE
}

/**
 * Pure rules deciding which [PassiveSensor]s may be registered and when a registration is stale.
 */
object PassiveSensorPlanner {

    /**
     * @param activityRecognitionGranted Whether `ACTIVITY_RECOGNITION` is granted.
     * @param heartRateReadableInBackground Whether heart rate may be read passively (foreground + background permission).
     * @return The sensors the app is allowed to register, before device capability filtering.
     */
    fun permittedSensors(
        activityRecognitionGranted: Boolean,
        heartRateReadableInBackground: Boolean
    ): Set<PassiveSensor> = buildSet {
        if (activityRecognitionGranted) {
            add(PassiveSensor.STEPS)
            add(PassiveSensor.FLOORS)
        }
        if (heartRateReadableInBackground) {
            add(PassiveSensor.HEART_RATE)
        }
    }

    /**
     * Identifies a registration. Health Services forgets registrations on reboot, so the boot count
     * is part of the key: a new boot or a changed permission set both produce a different key.
     */
    fun registrationKey(sensors: Set<PassiveSensor>, bootCount: Int): String {
        return sensors.map { it.name }.sorted().joinToString(",") + "@boot" + bootCount
    }
}

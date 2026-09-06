// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.model

/**
 * The core vital stats of the virtual companion (ranging 0.0 to 100.0).
 *
 * @property energy Influenced by real-world sleep and rest.
 * @property hunger Restored by logging healthy meals or snacks.
 * @property hydration Restored by logging water intake (e.g. +250ml quick-tap).
 * @property fitness Driven by daily steps and active workout sessions.
 * @property happiness Composite score reflecting overall vitals and petting interactions.
 * @property lastUpdatedTimestamp Epoch timestamp in millis when vitals were last calculated.
 */
data class Vitals(
    val energy: Float = 100f,
    val hunger: Float = 100f,
    val hydration: Float = 100f,
    val fitness: Float = 100f,
    val happiness: Float = 100f,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    init {
        require(energy in 0f..100f) { "Energy must be between 0 and 100" }
        require(hunger in 0f..100f) { "Hunger must be between 0 and 100" }
        require(hydration in 0f..100f) { "Hydration must be between 0 and 100" }
        require(fitness in 0f..100f) { "Fitness must be between 0 and 100" }
        require(happiness in 0f..100f) { "Happiness must be between 0 and 100" }
    }

    /**
     * Average overall health percentage.
     */
    val overallHealth: Float
        get() = (energy + hunger + hydration + fitness + happiness) / 5f

    companion object {
        val DEFAULT = Vitals()
    }
}


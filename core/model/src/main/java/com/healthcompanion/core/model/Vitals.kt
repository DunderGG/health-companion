// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.model

/**
 * The core vital stats of the virtual companion (ranging 0.0 to 100.0).
 *
 * ### Kotlin vs C++ Note:
 * - **Constructor & `init` block**: In Kotlin, the class header declares primary constructor parameters
 *   and properties simultaneously. The `init` block runs immediately upon instantiation, analogous to
 *   the body of a C++ constructor.
 * - **Contracts**: `require(condition)` throws `IllegalArgumentException` on failure, equivalent to
 *   `if (!condition) throw std::invalid_argument(...)` in C++.
 * - **Computed properties**: `val overallHealth: Float get() = ...` defines a getter without a backing
 *   memory field, identical to an inline const member function `float overallHealth() const` in C++.
 * - **`companion object`**: Kotlin does not have a `static` keyword for class members. Instead, a
 *   `companion object` acts as a scoped singleton containing class-level static factories and constants.
 *
 * @property energy Influenced by real-world sleep and rest. Range: `[0.0, 100.0]`.
 * @property hunger Restored by logging healthy meals or snacks. Range: `[0.0, 100.0]`.
 * @property hydration Restored by logging water intake (e.g. +250ml quick-tap). Range: `[0.0, 100.0]`.
 * @property fitness Driven by daily steps and active workout sessions. Range: `[0.0, 100.0]`.
 * @property happiness Composite score reflecting overall vitals and petting interactions. Range: `[0.0, 100.0]`.
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
        // Enforce invariants on instantiation (throws IllegalArgumentException if violated)
        require(energy in 0f..100f) { "Energy must be between 0 and 100" }
        require(hunger in 0f..100f) { "Hunger must be between 0 and 100" }
        require(hydration in 0f..100f) { "Hydration must be between 0 and 100" }
        require(fitness in 0f..100f) { "Fitness must be between 0 and 100" }
        require(happiness in 0f..100f) { "Happiness must be between 0 and 100" }
    }

    /**
     * Average overall health percentage computed across all 5 vital metrics.
     *
     * @return Float value in range `[0.0, 100.0]` representing the composite arithmetic mean.
     */
    val overallHealth: Float
        get() = (energy + hunger + hydration + fitness + happiness) / 5f

    companion object {
        /** Default baseline vitals (all stats maxed out at 100%). */
        val DEFAULT = Vitals()
    }
}


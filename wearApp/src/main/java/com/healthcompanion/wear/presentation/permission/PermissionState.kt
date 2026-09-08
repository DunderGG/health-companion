// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.permission

/**
 * Represents the finite states of health-sensor runtime permissions.
 *
 * ### Kotlin vs C++ Note:
 * - **`data object`**: In Kotlin, a `data object` defines a named singleton instance with value-oriented
 *   `toString()`, `equals()`, and `hashCode()`. Combined with `sealed interface`, it is the idiomatic
 *   pattern for modeling stateless variants in a Finite State Machine (FSM), equivalent to an enum class
 *   or `std::variant<std::monostate, ...>` in C++.
 */
sealed interface PermissionState {
    /** Initial sensor permission verification is in progress. */
    data object Checking : PermissionState

    /** All required permissions (BODY_SENSORS, ACTIVITY_RECOGNITION) are granted. */
    data object Granted : PermissionState

    /** Permissions have not been requested yet — onboarding permission prompt ([PermissionScreen]) is displayed. */
    data object Required : PermissionState

    /**
     * User has denied permissions (possibly permanently).
     * App enters degraded mode; PetScreen shows an "Enable sensors" chip linking to system Settings.
     */
    data object Denied : PermissionState
}


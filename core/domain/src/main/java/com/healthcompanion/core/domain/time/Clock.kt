// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.time

/**
 * Source of the current wall-clock time, injected wherever game logic depends on "now".
 *
 * Keeps use cases and repositories deterministic under test (a fake clock can be advanced
 * explicitly) instead of each call site reading `System.currentTimeMillis()` directly.
 *
 * ### Kotlin vs C++ Note:
 * - **`fun interface`**: A single-method interface that can be implemented with a lambda
 *   (`Clock { 42L }`), comparable to passing a `std::function<int64_t()>`.
 */
fun interface Clock {

    /** Current epoch time in milliseconds. */
    fun nowMillis(): Long

    companion object {
        /** The real system wall clock. */
        val SYSTEM: Clock = Clock { System.currentTimeMillis() }
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.model

/**
 * Top-level domain entity representing the virtual companion.
 *
 * ### Kotlin vs C++ Note:
 * In Kotlin, a `data class` is analogous to a C++ struct or class with compiler-generated
 * value semantics:
 * - `operator==` and `hashCode()` perform member-wise value equality (like C++20 default `operator==`).
 * - `toString()` produces a human-readable string representation of all fields.
 * - `.copy(...)` allows non-destructive mutation by creating a shallow copy with selective overrides,
 *   a fundamental idiom for immutability in functional/Kotlin architectures.
 *
 * @property id Unique identifier for the companion instance (defaults to `"companion_primary"`).
 * @property name User-facing display name of the companion.
 * @property stage Current life cycle stage (e.g. [EvolutionStage.HATCHLING], [EvolutionStage.ADULT]).
 * @property archetype Branching personality archetype driven by primary user habits (e.g. [PetArchetype.CARDIO_RUNNER]).
 * @property vitals Current core statistics (energy, hunger, hydration, fitness, happiness).
 * @property experiencePoints Cumulative progression points used to determine stage evolution.
 * @property bornTimestamp Epoch timestamp in milliseconds indicating when the companion was created.
 */
data class Pet(
    val id: String = "companion_primary",
    val name: String = "Aura",
    val stage: EvolutionStage = EvolutionStage.HATCHLING,
    val archetype: PetArchetype = PetArchetype.BALANCED,
    val vitals: Vitals = Vitals(),
    val experiencePoints: Int = 100,
    val bornTimestamp: Long = System.currentTimeMillis()
)


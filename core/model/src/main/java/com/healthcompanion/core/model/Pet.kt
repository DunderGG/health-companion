// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.model

/**
 * Top-level domain entity representing the virtual companion.
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


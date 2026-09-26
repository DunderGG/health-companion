// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.db.entity

import com.healthcompanion.core.model.EvolutionStage
import com.healthcompanion.core.model.Pet
import com.healthcompanion.core.model.PetArchetype
import org.junit.Assert.assertEquals
import org.junit.Test

class PetEntityTest {

    private fun entity(
        stage: String = "CHILD",
        archetype: String = "BALANCED",
        energy: Float = 50f,
        hunger: Float = 50f,
        experiencePoints: Int = 400
    ) = PetEntity(
        id = "companion_primary",
        name = "Aura",
        stage = stage,
        archetype = archetype,
        energy = energy,
        hunger = hunger,
        hydration = 50f,
        fitness = 50f,
        happiness = 50f,
        lastUpdatedTimestamp = 1_000L,
        experiencePoints = experiencePoints,
        bornTimestamp = 500L
    )

    @Test
    fun `valid row round-trips unchanged`() {
        val pet = Pet(stage = EvolutionStage.TEEN, archetype = PetArchetype.ZEN_SAGE, experiencePoints = 900)

        assertEquals(pet, PetEntity.fromDomain(pet).toDomain())
    }

    @Test
    fun `out-of-range and NaN vitals are clamped instead of crashing`() {
        val pet = entity(energy = 140f, hunger = Float.NaN).toDomain()

        assertEquals(100f, pet.vitals.energy, 0f)
        assertEquals(0f, pet.vitals.hunger, 0f)
    }

    @Test
    fun `unknown stage is re-derived from XP and unknown archetype falls back to balanced`() {
        val pet = entity(stage = "RENAMED_STAGE", archetype = "REMOVED_ARCHETYPE", experiencePoints = 800).toDomain()

        assertEquals(EvolutionStage.TEEN, pet.stage)
        assertEquals(PetArchetype.BALANCED, pet.archetype)
    }

    @Test
    fun `negative XP is clamped to zero`() {
        val pet = entity(stage = "UNKNOWN", experiencePoints = -50).toDomain()

        assertEquals(0, pet.experiencePoints)
        assertEquals(EvolutionStage.EGG, pet.stage)
    }
}

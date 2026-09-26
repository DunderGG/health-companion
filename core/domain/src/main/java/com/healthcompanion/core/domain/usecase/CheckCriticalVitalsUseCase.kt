// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.PetDecayEngine
import com.healthcompanion.core.domain.engine.VitalAlertPlan
import com.healthcompanion.core.domain.engine.VitalAlertPlanner
import com.healthcompanion.core.domain.repository.PetRepository
import com.healthcompanion.core.domain.repository.VitalAlertStateRepository
import com.healthcompanion.core.domain.time.Clock

/**
 * Result of a critical-vital check.
 *
 * @property petName Name to use in notification texts.
 * @property plan What to notify, what to clear, and when to check again.
 */
data class CriticalVitalsCheck(
    val petName: String,
    val plan: VitalAlertPlan
)

/**
 * Evaluates the pet's hydration and hunger against their critical thresholds (see [VitalAlertPlanner])
 * and records which vitals have now been alerted.
 *
 * Read-only for the pet: vitals are decayed to "now" in memory, never written, so a check can never
 * trigger another check through the repository's change callback.
 *
 * Callers must not run two checks concurrently (the platform scheduler runs them as unique work);
 * the alert state is a plain read followed by a write.
 *
 * @property petRepository Source of the stored pet.
 * @property alertStateRepository Persisted notified set.
 * @property clock Source of "now" and the local time zone.
 */
class CheckCriticalVitalsUseCase(
    private val petRepository: PetRepository,
    private val alertStateRepository: VitalAlertStateRepository,
    private val clock: Clock
) {

    suspend fun execute(): CriticalVitalsCheck {
        val pet = petRepository.getPet()
        val now = clock.nowMillis()
        val zone = clock.zone()
        val vitals = PetDecayEngine.calculateDecay(pet.vitals, now, zone)

        val plan = VitalAlertPlanner.plan(vitals, now, zone, alertStateRepository.notifiedVitals())
        alertStateRepository.setNotifiedVitals(plan.notified)
        return CriticalVitalsCheck(pet.name, plan)
    }
}

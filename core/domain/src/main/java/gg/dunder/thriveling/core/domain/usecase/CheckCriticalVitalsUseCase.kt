// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.usecase

import gg.dunder.thriveling.core.domain.engine.PetDecayEngine
import gg.dunder.thriveling.core.domain.engine.VitalAlertPlan
import gg.dunder.thriveling.core.domain.engine.VitalAlertPlanner
import gg.dunder.thriveling.core.domain.repository.PetRepository
import gg.dunder.thriveling.core.domain.repository.SettingsRepository
import gg.dunder.thriveling.core.domain.repository.VitalAlertStateRepository
import gg.dunder.thriveling.core.domain.time.Clock

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
 * @property settingsRepository Source of the user's bedtime, which is also the alerts' quiet hours (DD-48).
 * @property clock Source of "now" and the local time zone.
 */
class CheckCriticalVitalsUseCase(
    private val petRepository: PetRepository,
    private val alertStateRepository: VitalAlertStateRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock
) {

    suspend fun execute(): CriticalVitalsCheck {
        val pet = petRepository.getPet()
        val now = clock.nowMillis()
        val zone = clock.zone()
        val nightWindow = settingsRepository.getSettings().bedtime
        val vitals = PetDecayEngine.calculateDecay(pet.vitals, now, zone, nightWindow)

        val plan = VitalAlertPlanner.plan(vitals, now, zone, alertStateRepository.notifiedVitals(), nightWindow)
        alertStateRepository.setNotifiedVitals(plan.notified)
        return CriticalVitalsCheck(pet.name, plan)
    }
}

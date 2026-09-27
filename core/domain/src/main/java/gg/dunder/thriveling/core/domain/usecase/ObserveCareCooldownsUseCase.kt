// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.usecase

import gg.dunder.thriveling.core.domain.engine.CareAction
import gg.dunder.thriveling.core.domain.engine.CareCooldown
import gg.dunder.thriveling.core.domain.repository.PetRepository
import gg.dunder.thriveling.core.domain.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

/**
 * Observes which care buttons are in their [CareCooldown] (DD-59), so the UI can dim them. The repository
 * enforces the cooldown on its own; this only mirrors it.
 *
 * @property repository Source of the habit history.
 * @property clock Source of "now".
 */
class ObserveCareCooldownsUseCase(
    private val repository: PetRepository,
    private val clock: Clock
) {

    /**
     * @return Cold [Flow] of the care actions currently in their cooldown. Emits again when a care action
     *   is logged, and when a cooldown ends.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun execute(): Flow<Set<CareAction>> =
        flow { emitAll(repository.habitEventsSinceFlow(clock.nowMillis() - CareCooldown.COOLDOWN_MS)) }
            .transformLatest { events ->
                while (true) {
                    val now = clock.nowMillis()
                    val availableAt = CareAction.entries
                        .mapNotNull { action -> CareCooldown.availableAt(action, events, now)?.let { action to it } }
                        .toMap()
                    emit(availableAt.keys)
                    // Wake up when the first cooldown ends; a newer history cancels the wait.
                    val nextEnd = availableAt.values.minOrNull() ?: break
                    delay(nextEnd - now)
                }
            }
            .distinctUntilChanged()
}

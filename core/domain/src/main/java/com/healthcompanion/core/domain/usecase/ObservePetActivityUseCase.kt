// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.domain.usecase

import com.healthcompanion.core.domain.engine.StepCadence
import com.healthcompanion.core.domain.sensor.LiveStepSource
import com.healthcompanion.core.domain.time.Clock
import com.healthcompanion.core.model.PetActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan

/**
 * Mirrors the user's live steps as a [PetActivity], so the pet walks or runs alongside them.
 *
 * Steps and a short ticker are merged into one sequential [scan] over [StepCadence]: steps push the
 * pet into walking or running, and the ticker lets it fall back to idle once the steps stop. Both
 * only run while the stream is collected, i.e. while the pet screen is visible.
 *
 * The result is **cosmetic only**. Live steps are never written to the pet, because the same steps
 * arrive later as passive daily totals and are credited there (DD-38).
 *
 * @property stepSource Live step detector.
 * @property clock Source of "now"; must share the timebase of [LiveStepSource.steps].
 * @property tickMillis How often the activity is re-evaluated between steps.
 */
class ObservePetActivityUseCase(
    private val stepSource: LiveStepSource,
    private val clock: Clock,
    private val tickMillis: Long = DEFAULT_TICK_MS
) {

    /**
     * @return A stream starting with [PetActivity.IDLE] and emitting on every activity change.
     */
    fun execute(): Flow<PetActivity> {
        val events: Flow<Long?> = merge(stepSource.steps(), ticker())
        return events
            .scan(StepCadence()) { cadence, step ->
                val now = clock.nowMillis()
                if (step == null) cadence.advance(now) else cadence.record(step, now)
            }
            .map { it.activity }
            .distinctUntilChanged()
    }

    /** Emits `null` ticks, distinguishing them from step timestamps. */
    private fun ticker(): Flow<Long?> = flow {
        while (true) {
            delay(tickMillis)
            emit(null)
        }
    }

    companion object {
        /** Fine enough that the pet stops within about a second of the user. */
        const val DEFAULT_TICK_MS = 1_000L
    }
}

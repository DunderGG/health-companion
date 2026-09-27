// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.repository

import gg.dunder.thriveling.core.domain.engine.CriticalVital

/**
 * Remembers which vitals have already been alerted in their current critical episode,
 * so each episode produces at most one notification.
 */
interface VitalAlertStateRepository {

    /** Vitals notified and not yet recovered. */
    suspend fun notifiedVitals(): Set<CriticalVital>

    /** Replaces the notified set. */
    suspend fun setNotifiedVitals(vitals: Set<CriticalVital>)
}

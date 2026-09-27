// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.domain.sensor

import kotlinx.coroutines.flow.Flow

/**
 * Low-latency stream of individual steps, used only for live, cosmetic pet reactions.
 *
 * Unlike passive daily totals, this is a foreground signal: implementations should hold the
 * hardware sensor only while the flow is collected, and complete without emitting when the
 * sensor or the permission is unavailable.
 */
fun interface LiveStepSource {

    /** Epoch-millisecond timestamps of detected steps, in the same timebase as the injected clock. */
    fun steps(): Flow<Long>
}

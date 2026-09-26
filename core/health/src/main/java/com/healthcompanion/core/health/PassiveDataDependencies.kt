// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import com.healthcompanion.core.domain.usecase.IngestPassiveDataUseCase

/**
 * Dependencies [PassiveDataService] needs, provided by the application's composition root.
 *
 * The `Application` class implements this interface, so `:core:health` depends only on the
 * domain layer and never constructs repositories or databases itself.
 */
interface PassiveDataDependencies {

    /** Use case that turns sensor batches into pet habits. */
    val ingestPassiveDataUseCase: IngestPassiveDataUseCase
}

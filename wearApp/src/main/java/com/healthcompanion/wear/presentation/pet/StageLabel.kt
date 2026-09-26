// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.presentation.pet

import androidx.annotation.StringRes
import com.healthcompanion.core.model.EvolutionStage
import com.healthcompanion.wear.R

/** User-facing name of the stage, e.g. "Hatchling" rather than the enum's "HATCHLING" (DD-53). */
@get:StringRes
val EvolutionStage.labelRes: Int
    get() = when (this) {
        EvolutionStage.EGG -> R.string.stage_egg
        EvolutionStage.HATCHLING -> R.string.stage_hatchling
        EvolutionStage.CHILD -> R.string.stage_child
        EvolutionStage.TEEN -> R.string.stage_teen
        EvolutionStage.ADULT -> R.string.stage_adult
        EvolutionStage.ANCIENT_SAGE -> R.string.stage_ancient_sage
    }

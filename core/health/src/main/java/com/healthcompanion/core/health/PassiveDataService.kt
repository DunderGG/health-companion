// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.IntervalDataPoint
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.repository.PetRepositoryImpl
import com.healthcompanion.core.model.HabitType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wear OS system-managed passive listener service.
 * Receives batched health data (such as daily steps) from Health Services.
 */
class PassiveDataService : PassiveListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val stepDataPoints = dataPoints.getData(DataType.STEPS_DAILY)
        if (stepDataPoints.isNotEmpty()) {
            val latestTotalSteps = stepDataPoints.last().value

            serviceScope.launch {
                val db = CompanionDatabase.getInstance(applicationContext)
                val repository = PetRepositoryImpl(db.petDao())
                repository.recordHabit(HabitType.Steps(latestTotalSteps.toInt()))
            }
        }
    }
}


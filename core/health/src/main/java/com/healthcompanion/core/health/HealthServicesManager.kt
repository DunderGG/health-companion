// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import kotlinx.coroutines.guava.await

/**
 * Manages interaction with Wear OS Health Services.
 * Subscribes to passive daily step counts without active battery drain.
 */
class HealthServicesManager(private val context: Context) {

    private val passiveMonitoringClient: PassiveMonitoringClient by lazy {
        HealthServices.getClient(context).passiveMonitoringClient
    }

    suspend fun registerPassiveDataService() {
        if (!HealthPermissions.hasPermissions(context)) {
            return
        }

        val config = PassiveListenerConfig.builder()
            .setDataTypes(setOf(DataType.STEPS_DAILY))
            .build()

        // Register passive listener service
        passiveMonitoringClient.setPassiveListenerServiceAsync(
            PassiveDataService::class.java,
            config
        ).await()
    }

    suspend fun unregisterPassiveDataService() {
        passiveMonitoringClient.clearPassiveListenerServiceAsync().await()
    }
}


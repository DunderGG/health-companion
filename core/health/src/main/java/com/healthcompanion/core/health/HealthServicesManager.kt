// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.health

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import kotlinx.coroutines.guava.await

/**
 * Manages interaction with Wear OS Health Services.
 *
 * Subscribes to passive health data (steps, heart rate, calories, distance, floors)
 * using [PassiveMonitoringClient], which delegates sensor polling to the OS hardware
 * hub for near-zero extra battery drain.
 *
 * On registration, queries device capabilities to discover which [DataType]s the
 * watch hardware supports, and subscribes only to the intersection of desired and
 * available types. This ensures graceful degradation on watches without specific
 * sensors (e.g. no heart rate sensor on some models).
 */
class HealthServicesManager(private val context: Context) {

    private val passiveMonitoringClient: PassiveMonitoringClient by lazy {
        HealthServices.getClient(context).passiveMonitoringClient
    }

    /**
     * The full set of passive data types we want to consume.
     * At registration time, this is intersected with device capabilities.
     */
    private val desiredDataTypes: Set<DataType<*, *>> = setOf(
        DataType.STEPS_DAILY,
        DataType.HEART_RATE_BPM,
        DataType.CALORIES_DAILY,
        DataType.DISTANCE_DAILY,
        DataType.FLOORS_DAILY
    )

    suspend fun registerPassiveDataService() {
        tryRegisterPassiveDataService()
    }

    /**
     * Queries device capabilities and registers the passive listener service
     * for all supported data types.
     *
     * @return `true` if registration succeeded (permissions granted and at least
     *         one data type is supported), `false` otherwise.
     */
    suspend fun tryRegisterPassiveDataService(): Boolean {
        if (!HealthPermissions.hasPermissions(context)) {
            return false
        }

        val supportedTypes = getSupportedPassiveDataTypes()
        val registrationTypes = desiredDataTypes.intersect(supportedTypes)

        if (registrationTypes.isEmpty()) {
            Log.w(TAG, "No desired passive data types are supported on this device.")
            return false
        }

        Log.d(TAG, "Registering passive listener for: ${registrationTypes.map { it.name }}")

        val config = PassiveListenerConfig.builder()
            .setDataTypes(registrationTypes)
            .build()

        passiveMonitoringClient.setPassiveListenerServiceAsync(
            PassiveDataService::class.java,
            config
        ).await()

        return true
    }

    /**
     * Queries the device for supported passive monitoring data types.
     *
     * @return Set of [DataType]s the watch hardware can passively provide,
     *         or an empty set if capabilities cannot be determined.
     */
    suspend fun getSupportedPassiveDataTypes(): Set<DataType<*, *>> {
        return try {
            val capabilities = passiveMonitoringClient.getCapabilitiesAsync().await()
            capabilities.supportedDataTypesPassiveMonitoring
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query passive monitoring capabilities", e)
            emptySet()
        }
    }

    /**
     * Whether the device supports passive heart rate monitoring.
     * Useful for UI to decide whether to show HR-related features.
     */
    suspend fun hasHeartRateCapability(): Boolean {
        return DataType.HEART_RATE_BPM in getSupportedPassiveDataTypes()
    }

    suspend fun unregisterPassiveDataService() {
        passiveMonitoringClient.clearPassiveListenerServiceAsync().await()
    }

    companion object {
        private const val TAG = "HealthServicesManager"
    }
}

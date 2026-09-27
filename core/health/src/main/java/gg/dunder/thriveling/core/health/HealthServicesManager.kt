// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.health

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages interaction with Wear OS Health Services.
 *
 * Subscribes to passive health data (steps, floors, heart rate) using [PassiveMonitoringClient],
 * which delegates sensor polling to the OS hardware hub for near-zero extra battery drain.
 *
 * Registration is **per permission and idempotent** ([ensureRegistered]):
 * - Only sensors whose permissions are granted are registered (partial degradation), intersected
 *   with what the watch hardware supports.
 * - Health Services forgets registrations on reboot, so the last successful registration is stored
 *   together with the device boot count. Calls with an unchanged permission set on the same boot are
 *   no-ops; a new boot or a permission change triggers re-registration.
 *
 * ### Kotlin vs C++ Note:
 * - **Property Delegation (`by lazy`)**: Initializes [passiveMonitoringClient] on first access
 *   with thread-safe synchronization, equivalent to a local static variable initialized once or
 *   `std::call_once` in C++.
 * - **Future-to-Coroutine Bridging (`.await()`)**: The underlying Google Play Services API returns
 *   Guava `ListenableFuture<T>`. Calling `.await()` suspends the current coroutine until the future
 *   completes without blocking the calling thread, analogous to awaiting a `std::future` via `co_await`.
 * - **`Mutex.withLock { }`**: A suspending, non-reentrant lock (like `std::mutex` + `std::lock_guard`,
 *   but waiting coroutines release their thread instead of blocking it).
 *
 * @param context Android context used to obtain Health Services client handles.
 */
class HealthServicesManager(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Lazy client handle to Wear OS PassiveMonitoringClient.
     */
    private val passiveMonitoringClient: PassiveMonitoringClient by lazy {
        HealthServices.getClient(appContext).passiveMonitoringClient
    }

    private val registrationPrefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Makes sure the passive listener is registered for exactly the currently permitted and supported
     * sensors. Cheap when nothing changed (no Health Services call).
     *
     * `DISTANCE_DAILY` and `CALORIES_DAILY` are intentionally never registered (see DD-13).
     *
     * @param force Re-register even if the stored registration looks current (e.g. after boot).
     * @return `true` if a listener is registered for at least one sensor after the call.
     */
    suspend fun ensureRegistered(force: Boolean = false): Boolean = registrationMutex.withLock {
        val permitted = HealthPermissions.permittedSensors(appContext)
        val key = PassiveSensorPlanner.registrationKey(permitted, currentBootCount())

        if (!force && registrationPrefs.getString(KEY_REGISTRATION, null) == key) {
            return@withLock permitted.isNotEmpty()
        }

        val supported = getSupportedPassiveDataTypes()
        val dataTypes = permitted.map { it.dataType }.filter { it in supported }.toSet()

        if (dataTypes.isEmpty()) {
            Log.i(TAG, "No permitted and supported passive sensors (permitted=$permitted); clearing listener.")
            passiveMonitoringClient.clearPassiveListenerServiceAsync().await()
        } else {
            Log.d(TAG, "Registering passive listener for: ${dataTypes.map { it.name }}")
            val config = PassiveListenerConfig.builder()
                .setDataTypes(dataTypes)
                .build()
            passiveMonitoringClient.setPassiveListenerServiceAsync(PassiveDataService::class.java, config).await()
        }

        registrationPrefs.edit().putString(KEY_REGISTRATION, key).apply()
        dataTypes.isNotEmpty()
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
     * Unregisters the passive background listener service, stopping further sensor event delivery.
     */
    suspend fun unregisterPassiveDataService() = registrationMutex.withLock {
        passiveMonitoringClient.clearPassiveListenerServiceAsync().await()
        registrationPrefs.edit().remove(KEY_REGISTRATION).apply()
    }

    private fun currentBootCount(): Int {
        return Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }

    private val PassiveSensor.dataType: DataType<*, *>
        get() = when (this) {
            PassiveSensor.STEPS -> DataType.STEPS_DAILY
            PassiveSensor.FLOORS -> DataType.FLOORS_DAILY
            PassiveSensor.HEART_RATE -> DataType.HEART_RATE_BPM
        }

    companion object {
        private const val TAG = "HealthServicesManager"
        private const val PREFS_NAME = "passive_registration"
        private const val KEY_REGISTRATION = "registration_key"

        /** Process-wide lock: the app, permission flow and boot worker may register concurrently. */
        private val registrationMutex = Mutex()
    }
}

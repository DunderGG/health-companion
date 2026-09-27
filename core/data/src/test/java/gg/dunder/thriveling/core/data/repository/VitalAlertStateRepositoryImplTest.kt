// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import gg.dunder.thriveling.core.domain.engine.CriticalVital
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VitalAlertStateRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            tempFolder.newFile("vital_alerts_test.preferences_pb").apply { delete() }
        }
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun `notified set round-trips and starts empty`() = runBlocking {
        val repository = VitalAlertStateRepositoryImpl(dataStore)
        assertEquals(emptySet<CriticalVital>(), repository.notifiedVitals())

        repository.setNotifiedVitals(setOf(CriticalVital.HUNGER))
        assertEquals(setOf(CriticalVital.HUNGER), repository.notifiedVitals())

        repository.setNotifiedVitals(emptySet())
        assertEquals(emptySet<CriticalVital>(), repository.notifiedVitals())
    }

    @Test
    fun `unknown stored names are ignored`() = runBlocking {
        dataStore.edit { it[stringSetPreferencesKey("notified_vitals")] = setOf("HYDRATION", "BOREDOM") }

        assertEquals(setOf(CriticalVital.HYDRATION), VitalAlertStateRepositoryImpl(dataStore).notifiedVitals())
    }
}

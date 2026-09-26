// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PassiveSyncRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: PassiveSyncRepositoryImpl

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            tempFolder.newFile("passive_sync_test.preferences_pb").apply { delete() }
        }
        repository = PassiveSyncRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun `concurrent deliveries of the same growing total never double count`() = runBlocking {
        // 50 batches report totals 200, 400, ..., 10000 in arbitrary order.
        val consumed = (1..50).map { i ->
            async(Dispatchers.Default) {
                repository.consumeDailyTotal("steps_daily", epochDay = 1L, total = i * 200.0, granularity = 200.0)
            }
        }.awaitAll()

        assertEquals(10_000.0, consumed.sum(), 0.0)
    }

    @Test
    fun `baseline survives across calls and resets on a new day`() = runBlocking {
        assertEquals(800.0, repository.consumeDailyTotal("steps_daily", 1L, 850.0, 200.0), 0.0)
        assertEquals(0.0, repository.consumeDailyTotal("steps_daily", 1L, 850.0, 200.0), 0.0)
        assertEquals(200.0, repository.consumeDailyTotal("steps_daily", 2L, 250.0, 200.0), 0.0)
    }

    @Test
    fun `data types keep independent baselines`() = runBlocking {
        assertEquals(400.0, repository.consumeDailyTotal("steps_daily", 1L, 400.0, 200.0), 0.0)
        assertEquals(10.0, repository.consumeDailyTotal("floors_daily", 1L, 12.0, 10.0), 0.0)
    }

    @Test
    fun `heart rate award is rate limited`() = runBlocking {
        assertTrue(repository.tryClaimHeartRateAward(nowMillis = 1_000L, minIntervalMillis = 500L))
        assertFalse(repository.tryClaimHeartRateAward(nowMillis = 1_400L, minIntervalMillis = 500L))
        assertTrue(repository.tryClaimHeartRateAward(nowMillis = 1_500L, minIntervalMillis = 500L))
    }
}

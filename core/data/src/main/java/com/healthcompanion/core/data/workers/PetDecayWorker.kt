// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.repository.PetRepositoryImpl
import com.healthcompanion.core.domain.engine.PetDecayEngine

/**
 * Background worker executing battery-efficient periodic decay computation.
 */
class PetDecayWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = CompanionDatabase.getInstance(applicationContext)
            val repository = PetRepositoryImpl(db.petDao())
            val pet = repository.getPet()

            val decayedVitals = PetDecayEngine.calculateDecay(pet.vitals)
            repository.updatePet(pet.copy(vitals = decayedVitals))

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}


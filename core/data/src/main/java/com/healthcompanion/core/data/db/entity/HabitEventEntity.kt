// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.healthcompanion.core.model.HabitEvent
import com.healthcompanion.core.model.HabitType

/**
 * Room entity for one applied habit (`habit_events` table, schema v2+).
 *
 * Habits are flattened into a stable type name plus up to two numeric values:
 *
 * | [type]      | [amount]              | [detail]      |
 * | :---        | :---                  | :---          |
 * | `STEPS`     | step count            | —             |
 * | `HYDRATION` | millilitres           | —             |
 * | `MEAL`      | 1 = healthy, 0 = not  | —             |
 * | `WORKOUT`   | duration (minutes)    | calories      |
 * | `SLEEP`     | duration (minutes)    | quality score |
 * | `PETTING`   | intensity             | —             |
 * | `HEART_RATE`| bpm                   | —             |
 *
 * Type names are persisted and must never be renamed; unknown names are skipped on load.
 *
 * @property id Auto-generated row id.
 * @property type Stable habit type name (see table above).
 * @property amount Primary numeric value.
 * @property detail Optional secondary numeric value.
 * @property timestampMillis Epoch milliseconds when the habit was applied (indexed for range queries).
 */
@Entity(
    tableName = "habit_events",
    indices = [Index(value = ["timestampMillis"])]
)
data class HabitEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Double,
    val detail: Double? = null,
    val timestampMillis: Long
) {

    /** Converts back to a domain [HabitEvent], or `null` for an unknown [type]. */
    fun toDomain(): HabitEvent? {
        val habit = when (type) {
            STEPS -> HabitType.Steps(amount.toInt())
            HYDRATION -> HabitType.Hydration(amount.toInt())
            MEAL -> HabitType.Meal(isHealthy = amount >= 1.0)
            WORKOUT -> HabitType.Workout(durationMinutes = amount.toInt(), calories = detail?.toInt() ?: 0)
            SLEEP -> HabitType.Sleep(durationMinutes = amount.toInt(), qualityScore = detail?.toFloat() ?: 0.8f)
            PETTING -> HabitType.PettingInteraction(intensity = amount.toFloat())
            HEART_RATE -> HabitType.HeartRate(bpm = amount.toFloat())
            else -> return null
        }
        return HabitEvent(habit, timestampMillis)
    }

    companion object {
        const val STEPS = "STEPS"
        const val HYDRATION = "HYDRATION"
        const val MEAL = "MEAL"
        const val WORKOUT = "WORKOUT"
        const val SLEEP = "SLEEP"
        const val PETTING = "PETTING"
        const val HEART_RATE = "HEART_RATE"

        /** Flattens a habit applied at [timestampMillis] into a table row. */
        fun fromDomain(habit: HabitType, timestampMillis: Long): HabitEventEntity = when (habit) {
            is HabitType.Steps -> HabitEventEntity(type = STEPS, amount = habit.stepCount.toDouble(), timestampMillis = timestampMillis)
            is HabitType.Hydration -> HabitEventEntity(type = HYDRATION, amount = habit.milliliters.toDouble(), timestampMillis = timestampMillis)
            is HabitType.Meal -> HabitEventEntity(type = MEAL, amount = if (habit.isHealthy) 1.0 else 0.0, timestampMillis = timestampMillis)
            is HabitType.Workout -> HabitEventEntity(
                type = WORKOUT,
                amount = habit.durationMinutes.toDouble(),
                detail = habit.calories.toDouble(),
                timestampMillis = timestampMillis
            )
            is HabitType.Sleep -> HabitEventEntity(
                type = SLEEP,
                amount = habit.durationMinutes.toDouble(),
                detail = habit.qualityScore.toDouble(),
                timestampMillis = timestampMillis
            )
            is HabitType.PettingInteraction -> HabitEventEntity(type = PETTING, amount = habit.intensity.toDouble(), timestampMillis = timestampMillis)
            is HabitType.HeartRate -> HabitEventEntity(type = HEART_RATE, amount = habit.bpm.toDouble(), timestampMillis = timestampMillis)
        }
    }
}

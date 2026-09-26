// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.complications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.icu.text.CompactDecimalFormat
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.healthcompanion.wear.HealthCompanionApp
import com.healthcompanion.wear.MainActivity
import com.healthcompanion.wear.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Watch face complication showing today's steps against the user's daily step goal (DD-51), next to the
 * separate Pet Mood complication (DD-43).
 *
 * Supported types:
 * - **Ranged value**: a ring filled up to the step goal, with the steps in compact form ("4.2K") and a
 *   walking icon. Steps past the goal show a full ring.
 * - **Short text**: the steps ("4,200") with the walking icon.
 *
 * Tapping either opens the app. The steps are the same total as on the goals page (DD-49): today's step
 * history, floor bonus steps included.
 *
 * Pull-based like the Pet Mood complication: `AppContainer` asks for a refresh after every pet write and
 * settings change, and the manifest's `UPDATE_PERIOD_SECONDS` lets a new day start from 0 without a write.
 */
class StepGoalComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val progress = (application as HealthCompanionApp).container.observeDailyProgressUseCase.current()
        return build(request.complicationType, progress.steps, progress.goals.steps)
    }

    /** Sample shown in the watch face editor's picker. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, steps = 4_200, goal = 6_000)

    private fun build(type: ComplicationType, steps: Int, goal: Int): ComplicationData? {
        val numbers = NumberFormat.getIntegerInstance()
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_steps)).build()
        val description = text(getString(R.string.step_complication_description, numbers.format(steps), numbers.format(goal)))

        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = steps.coerceAtMost(goal).toFloat(),
                    min = 0f,
                    max = goal.toFloat(),
                    contentDescription = description
                )
                    .setMonochromaticImage(image)
                    .setText(text(compact(steps)))
                    .setTapAction(openAppIntent())
                    .build()

            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(numbers.format(steps)), description)
                    .setMonochromaticImage(image)
                    .setTapAction(openAppIntent())
                    .build()

            else -> null // Not declared in the manifest, so the system should never ask for it.
        }
    }

    /** "850", "4.2K", "12K": short enough for the middle of a ring, in the user's locale. */
    private fun compact(steps: Int): String =
        CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT).format(steps)

    private fun text(value: CharSequence): ComplicationText = PlainComplicationText.Builder(value).build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        /** Asks every watch face showing this complication to request fresh data. */
        fun requestRefresh(context: Context) {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, StepGoalComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}

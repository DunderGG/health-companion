// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.complications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.healthcompanion.core.model.Mood
import com.healthcompanion.wear.ThrivelingApp
import com.healthcompanion.wear.MainActivity
import com.healthcompanion.wear.R
import com.healthcompanion.wear.toDisplayPercent

/**
 * Watch face complication showing the pet's mood, and its overall health as a ring.
 *
 * Supported types (DD-43):
 * - **Short text**: mood face icon + short mood label ("Happy").
 * - **Ranged value**: overall health 0–100 (the same number as the in-app vitals ring), with the
 *   mood face in the middle and the percentage as text.
 * - **Monochromatic image**: the mood face alone, for the smallest slots.
 *
 * Tapping any of them opens the app.
 *
 * ### Kotlin vs C++ Note:
 * - **`SuspendingComplicationDataSourceService`**: The system binds to this service and calls the
 *   `suspend` [onComplicationRequest] from a coroutine, so the Room read does not block its thread
 *   (the complication equivalent of the tile's `serviceScope.future { }`).
 *
 * Like the tile, complications are pull-based: they refresh every `UPDATE_PERIOD_SECONDS` (manifest)
 * and whenever `AppContainer` calls [requestRefresh] after a pet write.
 */
class PetMoodComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val (pet, mood) = (application as ThrivelingApp).container.getPetStateUseCase.current()
        return build(request.complicationType, pet.name, mood, pet.vitals.overallHealth)
    }

    /** Sample shown in the watch face editor's picker. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, getString(R.string.pet_name_default), Mood.HAPPY, health = 80f)

    private fun build(type: ComplicationType, petName: String, mood: Mood, health: Float): ComplicationData? {
        val presentation = MoodPresentation.of(mood)
        val label = getString(presentation.labelRes)
        val healthPercent = health.toDisplayPercent()
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, presentation.iconRes)).build()
        val description = text(getString(R.string.complication_description, petName, label, healthPercent))

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(label), description)
                    .setMonochromaticImage(image)
                    .setTapAction(openAppIntent())
                    .build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = healthPercent.toFloat(),
                    min = 0f,
                    max = 100f,
                    contentDescription = description
                )
                    .setMonochromaticImage(image)
                    .setText(text("$healthPercent%"))
                    .setTapAction(openAppIntent())
                    .build()

            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(image, description)
                    .setTapAction(openAppIntent())
                    .build()

            else -> null // Not declared in the manifest, so the system should never ask for it.
        }
    }

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
                .create(context, ComponentName(context, PetMoodComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}

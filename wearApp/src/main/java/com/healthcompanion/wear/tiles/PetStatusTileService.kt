// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.tiles

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.healthcompanion.core.data.db.CompanionDatabase
import com.healthcompanion.core.data.repository.PetRepositoryImpl
import com.healthcompanion.core.domain.engine.MoodCalculator
import com.healthcompanion.core.domain.engine.PetDecayEngine
import kotlinx.coroutines.runBlocking

/**
 * Wear OS Carousel Tile providing instant glanceable pet vitals from the watch face.
 */
class PetStatusTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val future = ResolvableFuture.create<TileBuilders.Tile>()

        val db = CompanionDatabase.getInstance(applicationContext)
        val repository = PetRepositoryImpl(db.petDao())

        val pet = runBlocking { repository.getPet() }
        val decayedVitals = PetDecayEngine.calculateDecay(pet.vitals)
        val mood = MoodCalculator.calculateMood(decayedVitals)

        val rootLayout = LayoutElementBuilders.Column.Builder()
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("${pet.name} (${mood.name})")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(16f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(dp(4f))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("Health: ${decayedVitals.overallHealth.toInt()}%")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(13f))
                            .setColor(argb(0xFF00E5FF.toInt()))
                            .build()
                    )
                    .build()
            )
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(rootLayout)
                            .build()
                    )
                    .build()
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(600_000) // 10 minutes cache
            .build()

        future.set(tile)
        return future
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion("1")
            .build()
        future.set(resources)
        return future
    }
}


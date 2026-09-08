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
 * Wear OS Carousel Tile providing instant glanceable pet vitals directly from the watch face carousel.
 *
 * ### Kotlin vs C++ Note:
 * - **ProtoLayout**: Wear OS Tiles do not use Jetpack Compose directly. Instead, they construct a
 *   declarative ProtoLayout schema builder tree that is serialized into protocol buffers and transmitted
 *   via IPC to the system Watch Face UI process for rendering.
 * - **`runBlocking { ... }`**: Bridges the asynchronous Kotlin Coroutines world to synchronous/future APIs.
 *   Unlike `launch`, `runBlocking` blocks the worker thread until the coroutine completes (identical to
 *   calling `future.get()` on a `std::future` in C++). Used here because `onTileRequest` expects a
 *   `ListenableFuture<Tile>` return value.
 * - **ResolvableFuture**: Functions like a `std::promise` in C++, allowing asynchronous completion of a future.
 */
class PetStatusTileService : TileService() {

    /**
     * Constructs and returns the Tile layout whenever Wear OS requests a tile render or update.
     *
     * Queries current pet vitals from SQLite, calculates decay and mood, builds the ProtoLayout
     * text column, and sets a 10-minute cache freshness interval to conserve battery.
     *
     * @param requestParams Parameters including screen dimensions, device density, and tile state.
     * @return [ListenableFuture] completing with the rendered [TileBuilders.Tile].
     */
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

    /**
     * Supplies static graphical resources (images, icons) referenced by the ProtoLayout tree.
     *
     * @param requestParams Parameters including requested resource version and device capabilities.
     * @return [ListenableFuture] delivering the populated [ResourceBuilders.Resources] bundle.
     */
    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion("1")
            .build()
        future.set(resources)
        return future
    }
}


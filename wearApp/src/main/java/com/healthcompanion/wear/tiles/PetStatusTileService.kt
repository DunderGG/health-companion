// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.tiles

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
import com.healthcompanion.core.domain.engine.MoodCalculator
import com.healthcompanion.core.domain.engine.NightWindow
import com.healthcompanion.core.domain.engine.PetDecayEngine
import com.healthcompanion.wear.HealthCompanionApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * Wear OS Carousel Tile providing instant glanceable pet vitals directly from the watch face carousel.
 *
 * ### Kotlin vs C++ Note:
 * - **ProtoLayout**: Wear OS Tiles do not use Jetpack Compose directly. Instead, they construct a
 *   declarative ProtoLayout schema builder tree that is serialized into protocol buffers and transmitted
 *   via IPC to the system Watch Face UI process for rendering.
 * - **`serviceScope.future { ... }`** (kotlinx-coroutines-guava): Runs a coroutine and exposes its result as
 *   the `ListenableFuture` the Tiles API expects, without blocking the calling thread (like returning a
 *   `std::future` fed by an async task). The scope is cancelled in [onDestroy].
 *
 * The tile is pull-based: it is re-rendered when the system asks, when its freshness interval expires,
 * and when the app requests an update after every pet write (see `AppContainer.petRepository`).
 */
class PetStatusTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Constructs and returns the Tile layout whenever Wear OS requests a tile render or update.
     *
     * Queries current pet vitals from SQLite, calculates decay and mood, builds the ProtoLayout
     * text column, and sets a 10-minute cache freshness interval to conserve battery.
     *
     * @param requestParams Parameters including screen dimensions, device density, and tile state.
     * @return [ListenableFuture] completing with the rendered [TileBuilders.Tile].
     */
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future { buildTile() }

    private suspend fun buildTile(): TileBuilders.Tile {
        val container = (application as HealthCompanionApp).container

        val pet = container.petRepository.getPet()
        val now = container.clock.nowMillis()
        val zone = container.clock.zone()
        val decayedVitals = PetDecayEngine.calculateDecay(pet.vitals, now, zone)
        val mood = MoodCalculator.calculateMood(decayedVitals, isNightTime = NightWindow.DEFAULT.isNight(now, zone))

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

        return TileBuilders.Tile.Builder()
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(600_000) // 10 minutes cache; writes also request an update
            .build()
    }

    /**
     * Supplies static graphical resources (images, icons) referenced by the ProtoLayout tree.
     *
     * @param requestParams Parameters including requested resource version and device capabilities.
     * @return [ListenableFuture] delivering the populated [ResourceBuilders.Resources] bundle.
     */
    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        serviceScope.future {
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}


// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package com.healthcompanion.wear.tiles

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.healthcompanion.core.model.HabitType
import com.healthcompanion.wear.ThrivelingApp
import com.healthcompanion.wear.MainActivity
import com.healthcompanion.wear.R
import com.healthcompanion.wear.toDisplayPercent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext

/**
 * Wear OS Carousel Tile with glanceable pet vitals and a 1-tap "+250ml Water" button.
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
 *
 * ### Water button (DD-42):
 * The chip uses a `LoadAction`, so a tap makes the system call [onTileRequest] again with the chip's id
 * as `lastClickableId`. The request logs the water once (guarded by [TileClickLedger]) and renders the
 * new hydration value, which is the tap's only feedback. Tapping the vitals opens the app.
 */
class PetStatusTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Handles a pending water tap, then constructs the Tile layout.
     *
     * Reads the decayed pet and its mood (`GetPetStateUseCase.current()`), builds the ProtoLayout
     * tree, and sets a 10-minute cache freshness interval to conserve battery.
     *
     * @param requestParams Parameters including screen dimensions, device density, and the last clicked id.
     * @return [ListenableFuture] completing with the rendered [TileBuilders.Tile].
     */
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            handleClick(requestParams.currentState.lastClickableId)
            buildTile(requestParams.deviceConfiguration)
        }

    private suspend fun handleClick(lastClickableId: String) {
        val container = (application as ThrivelingApp).container
        val isNewTap = withContext(Dispatchers.IO) { container.tileClickLedger.claimWaterClick(lastClickableId) }
        if (isNewTap) {
            container.logHabitUseCase.execute(HabitType.Hydration(WATER_ML))
        }
    }

    private suspend fun buildTile(deviceParameters: DeviceParameters): TileBuilders.Tile {
        val container = (application as ThrivelingApp).container

        val (pet, mood) = container.getPetStateUseCase.current()
        val decayedVitals = pet.vitals
        val now = container.clock.nowMillis()

        val openApp = ModifiersBuilders.Clickable.Builder()
            .setId(ID_OPEN_APP)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        // A fresh id per render, so a later request repeating this tap's id is not logged twice.
        val logWater = ModifiersBuilders.Clickable.Builder()
            .setId(TileClickIds.logWater(token = now))
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val vitals = LayoutElementBuilders.Column.Builder()
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp).build())
            .addContent(
                Text.Builder(this, "Health ${decayedVitals.overallHealth.toDisplayPercent()}%")
                    .setTypography(Typography.TYPOGRAPHY_TITLE2)
                    .setColor(argb(HEALTH_COLOR))
                    .build()
            )
            .addContent(
                Text.Builder(this, "Hydration ${decayedVitals.hydration.toDisplayPercent()}%")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(WATER_COLOR))
                    .build()
            )
            .build()

        val waterChip = CompactChip.Builder(this, getString(R.string.action_drink_water), logWater, deviceParameters)
            .setIconContent(ID_WATER_DROP)
            .setChipColors(ChipColors(WATER_COLOR, ON_WATER_COLOR))
            .setContentDescription(getString(R.string.tile_log_water_description))
            .build()

        val rootLayout = PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "${pet.name} (${mood.name})")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(LABEL_COLOR))
                    .build()
            )
            .setContent(vitals)
            .setPrimaryChipContent(waterChip)
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
            .setResourcesVersion(RESOURCES_VERSION)
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
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    ID_WATER_DROP,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_tile_water_drop)
                                .build()
                        )
                        .build()
                )
                .build()
        }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        /** Same amount as the in-app water token. */
        const val WATER_ML = 250

        /** Bump whenever the image mapping in [onTileResourcesRequest] changes. */
        const val RESOURCES_VERSION = "2"
        const val ID_WATER_DROP = "water_drop"
        const val ID_OPEN_APP = "open_app"

        const val HEALTH_COLOR = 0xFF00E5FF.toInt()
        const val WATER_COLOR = 0xFF00B0FF.toInt() // BrightAqua, as on the in-app water token
        const val ON_WATER_COLOR = 0xFF0A0E14.toInt()
        const val LABEL_COLOR = 0xFFB0BEC5.toInt()
    }
}

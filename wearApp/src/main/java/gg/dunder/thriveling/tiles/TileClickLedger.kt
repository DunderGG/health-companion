// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.tiles

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Click ids for the tile's `LoadAction` buttons.
 *
 * Every render stamps its buttons with a fresh token (`log_water:<token>`). A tap on a button
 * therefore produces an id that no earlier render used, and any later request that still reports
 * the same `lastClickableId` (a freshness refresh or the update requested by the write itself)
 * can be recognized as already handled.
 */
internal object TileClickIds {
    private const val LOG_WATER_PREFIX = "log_water:"

    /** Id for the "+250ml Water" button of the render stamped with [token]. */
    fun logWater(token: Long): String = "$LOG_WATER_PREFIX$token"

    /**
     * Whether [lastClickableId] is a water tap that has not been handled yet.
     *
     * @param lastClickableId Id reported by the tile request; empty when no element was clicked.
     * @param lastHandledId Id of the most recent click that was already acted on, if any.
     */
    fun isNewWaterClick(lastClickableId: String, lastHandledId: String?): Boolean =
        lastClickableId.startsWith(LOG_WATER_PREFIX) && lastClickableId != lastHandledId
}

/**
 * Remembers the last tile click that was acted on, so each tap logs at most once.
 *
 * Backed by a small `SharedPreferences` file rather than memory, because the system may destroy and
 * recreate the tile service between the tap and a later request that still reports the same id.
 *
 * ### Kotlin vs C++ Note:
 * - **`@Synchronized`**: Guards the whole method with the instance's monitor (like a `std::mutex`
 *   locked for the function body), so two concurrent tile requests cannot both claim the same click.
 */
class TileClickLedger(private val prefs: SharedPreferences) {

    /**
     * Claims [lastClickableId] if it is a new water tap. The claim is committed *before* the caller
     * logs the water, so a failed write loses one tap rather than risking a double log.
     *
     * @return `true` if the caller should log the water now.
     */
    @Synchronized
    fun claimWaterClick(lastClickableId: String): Boolean {
        val lastHandled = prefs.getString(KEY_LAST_HANDLED, null)
        if (!TileClickIds.isNewWaterClick(lastClickableId, lastHandled)) return false
        // Synchronous on purpose: the claim must be on disk before the water is logged.
        prefs.edit(commit = true) { putString(KEY_LAST_HANDLED, lastClickableId) }
        return true
    }

    companion object {
        private const val PREFS_NAME = "tile_clicks"
        private const val KEY_LAST_HANDLED = "last_handled_click_id"

        fun create(context: Context): TileClickLedger =
            TileClickLedger(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
}

// Copyright 2026 DunderGG
// SPDX-License-Identifier: Apache-2.0

package gg.dunder.thriveling.presentation.pet

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.curvedText
import gg.dunder.thriveling.core.domain.engine.CareAction
import gg.dunder.thriveling.core.ui.components.MealActionToken
import gg.dunder.thriveling.core.ui.components.ModernPetCanvas
import gg.dunder.thriveling.core.ui.components.VitalsRing
import gg.dunder.thriveling.core.ui.components.WaterActionToken
import gg.dunder.thriveling.R
import gg.dunder.thriveling.presentation.ambient.AmbientState
import gg.dunder.thriveling.presentation.ambient.BurnInShift
import kotlin.math.sqrt

/**
 * Widest angle the curved name may span, centred on 12 o'clock (DD-53): up to 60° on either side, well above
 * the details button at 9 o'clock and the page indicator at 3 o'clock.
 */
private const val NAME_MAX_SWEEP_DEGREES = 120f

/** Space between the curved name and the ring's arcs. */
private val NAME_RING_GAP = 2.dp

/** The ambient ring's inset plus its thin stroke (see [VitalsRing]), which the name curves inside. */
private val AMBIENT_RING_INNER_INSET = 24.dp

/**
 * The pet's canvas, as a share of the screen width: as large as fits between the name and the meal and
 * water buttons (DD-53). It was a fixed 110 dp before, about half of a large screen.
 */
private const val PET_SIZE_FRACTION = 0.6f

/** The details and sensor buttons: small enough for the 20° gaps between the ring's arcs (DD-53). */
private val EDGE_BUTTON_SIZE = 22.dp

/** Their touch area, the recommended minimum on Wear OS; it reaches inwards, over nothing else tappable. */
private val EDGE_BUTTON_TOUCH_SIZE = 48.dp

/** The meal and water buttons, a little smaller than their 40 dp default to fit beside the pet (DD-53). */
private val ACTION_TOKEN_SIZE = 32.dp

/** From the screen edge to the inside of the ring's arcs: the ring's 8 dp inset plus its 5 dp stroke. */
private val RING_INNER_INSET = 13.dp

/** Space between an action button and its arc. */
private val ACTION_TOKEN_GAP = 4.dp

/**
 * Horizontal and vertical distance from the screen centre to an action button's centre, placing it just
 * inside the ring at 45° or 135°, the middle of the hydration and hunger arcs.
 */
private fun actionTokenOffset(screenWidth: Dp): Dp {
    val distance = screenWidth / 2 - RING_INNER_INSET - ACTION_TOKEN_GAP - ACTION_TOKEN_SIZE / 2
    return distance * sqrt(0.5f)
}

/**
 * Primary interactive Wear OS screen displaying the virtual companion character,
 * bezel vitals ring, and quick-action buttons.
 *
 * ### Kotlin vs C++ Note:
 * - **`collectAsStateWithLifecycle()` with `by` Delegation**:
 *   `val uiState by viewModel.uiState.collectAsStateWithLifecycle()` connects Kotlin Coroutines' reactive `StateFlow`
 *   to Compose's reactive runtime. The `by` keyword delegates read access, automatically unwrapping
 *   `State<T>.value` (similar to dereferencing a smart pointer). Whenever `uiState` updates, Compose
 *   automatically recomposes this function.
 * - **Smart Casting**: `when (val state = uiState)` with `is PetUiState.Success`. Once the type check succeeds,
 *   the compiler automatically casts `state` to [PetUiState.Success] for that branch, eliminating the need
 *   for manual `dynamic_cast` or `std::get<T>` calls common in C++.
 *
 * @param viewModel The [PetViewModel] managing companion state and actions.
 * @param modifier Compose layout modifier applied to the root container.
 * @param showSensorChip When `true`, shows a warning button at 6 o'clock: health sensors are disabled.
 * @param onSensorChipClick Callback triggered when the warning button is tapped (opens system Settings).
 * @param onOpenDetails Opens the pet details screen, from the "i" button (DD-53).
 * @param ambientState In ambient (always-on) mode the screen shows only the time, the name, a static outline
 *                     pet and a thin ring, shifted each minute on burn-in-prone displays (DD-44).
 */
@Composable
fun PetScreen(
    viewModel: PetViewModel,
    modifier: Modifier = Modifier,
    showSensorChip: Boolean = false,
    onSensorChipClick: () -> Unit = {},
    onOpenDetails: () -> Unit = {},
    ambientState: AmbientState = AmbientState.Interactive
) {
    // Lifecycle-aware: collection (and with it the live step sensor and the decay ticker)
    // stops when the activity is no longer visible, e.g. when the screen turns off. In ambient mode the
    // activity stays visible; the view model releases the step sensor itself (DD-44).
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayMode = ambientState.displayMode
    val isAmbient = displayMode.isAmbient
    val (shiftX, shiftY) = BurnInShift.offsetDp(ambientState)

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset(shiftX.dp, shiftY.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is PetUiState.Loading -> {
                CircularProgressIndicator()
            }
            is PetUiState.Success -> {
                val pet = state.pet
                val mood = state.mood

                // Vitals Ring wrapping the circular watch screen
                VitalsRing(vitals = pet.vitals, displayMode = displayMode) {
                    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                    val petSize = screenWidth * PET_SIZE_FRACTION
                    Box(modifier = Modifier.fillMaxSize()) {
                        // The name curves along the inside of the ring at the top, like the system clock, so it
                        // runs alongside the arcs rather than into them (DD-53). A long name ends in "…" at
                        // NAME_MAX_SWEEP_DEGREES, before it reaches the details button and the page indicator.
                        // The stage is on the pet details screen.
                        val ringInset = if (isAmbient) AMBIENT_RING_INNER_INSET else RING_INNER_INSET
                        val nameStyle = CurvedTextStyle(MaterialTheme.typography.arcMedium)
                        val nameColor = if (isAmbient) displayMode.ambientColor else Color.White
                        CurvedLayout(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(ringInset + NAME_RING_GAP),
                            anchor = 270f
                        ) {
                            curvedText(
                                text = pet.name,
                                maxSweepAngle = NAME_MAX_SWEEP_DEGREES,
                                style = nameStyle,
                                color = nameColor,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Modern Animated Companion Sprite (Clickable for Petting). An accepted pet plays
                        // the purr pattern through PetViewModel.hapticEvents (DD-47).
                        val petInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = petInteractionSource,
                                    indication = null
                                ) {
                                    viewModel.petCompanion()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            ModernPetCanvas(
                                mood = mood,
                                isPetting = state.isPettingFeedbackActive,
                                activity = state.activity,
                                canvasSize = petSize,
                                displayMode = displayMode
                            )
                        }
                    }
                }

                // Meal and water buttons, each just inside the middle of the arc it fills: hunger at 135°
                // (lower left) and hydration at 45° (lower right). Hidden in ambient, where the screen isn't
                // interactive (DD-53).
                // Each is dimmed for an hour after use (DD-59).
                if (!isAmbient) {
                    val offset = actionTokenOffset(LocalConfiguration.current.screenWidthDp.dp)
                    val cooldowns by viewModel.careCooldowns.collectAsStateWithLifecycle()
                    MealActionToken(
                        onClick = { viewModel.logMeal(isHealthy = true) },
                        size = ACTION_TOKEN_SIZE,
                        enabled = CareAction.FOOD !in cooldowns,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = -offset, y = offset)
                    )
                    WaterActionToken(
                        onClick = { viewModel.logWater(250) },
                        size = ACTION_TOKEN_SIZE,
                        enabled = CareAction.WATER !in cooldowns,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = offset, y = offset)
                    )
                }

                if (!isAmbient) {
                    // "i" button into the pet details (DD-53), at 9 o'clock in the gap between the hunger and
                    // energy arcs, opposite the page indicator.
                    EdgeButton(
                        iconRes = R.drawable.ic_info,
                        label = stringResource(R.string.details_open),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onOpenDetails,
                        alignment = Alignment.CenterStart
                    )

                    // Degraded mode (sensors denied): a warning button at 6 o'clock, in the gap between the
                    // hydration and hunger arcs, that opens the system settings. The curved name took the
                    // space above the pet where a text chip used to be (DD-53).
                    if (showSensorChip) {
                        EdgeButton(
                            iconRes = R.drawable.ic_warning,
                            label = stringResource(R.string.sensors_off),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            iconColor = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = onSensorChipClick,
                            alignment = Alignment.BottomCenter
                        )
                    }
                }
            }
        }

        // The watch face (and its clock) is hidden while the app is always-on, so show the time instead.
        // Plain text rather than Material's TimeText, which draws a filled pill behind the time even in ambient.
        if (isAmbient) {
            val timeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat())
            Text(
                text = timeSource.currentTime(),
                style = MaterialTheme.typography.labelMedium,
                color = displayMode.ambientColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
            )
        }
    }
}


/**
 * A small round button in one of the 20° gaps between the ring's arcs, at the screen edge given by
 * [alignment] (DD-53). The touch area is larger than the icon and reaches inwards from the edge.
 *
 * @param iconRes Drawable of the icon.
 * @param label What the button does, for screen readers.
 * @param containerColor Colour of the circle.
 * @param iconColor Colour of the icon.
 * @param onClick Called when tapped.
 * @param alignment Edge of the screen: [Alignment.CenterStart] for 9 o'clock, [Alignment.BottomCenter] for 6.
 */
@Composable
private fun BoxScope.EdgeButton(
    @DrawableRes iconRes: Int,
    label: String,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    alignment: Alignment
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .size(EDGE_BUTTON_TOUCH_SIZE)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .size(EDGE_BUTTON_SIZE)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

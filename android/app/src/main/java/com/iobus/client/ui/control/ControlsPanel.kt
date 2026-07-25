package com.iobus.client.ui.control

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.iobus.client.network.ConnectionManager
import com.iobus.client.protocol.KeyCodes
import com.iobus.client.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Adaptive Controls panel — detects orientation and renders appropriately.
 *
 * Portrait layout (vertical sliders, side-by-side):
 *  1. Row: Brightness + Volume gradient stacks side by side (with Max/Min buttons)
 *  2. Media controls row (Previous · Play/Pause · Next)
 *
 * Landscape layout (horizontal sliders, stacked vertically):
 *  1. Column: Brightness + Volume gradient bars stacked (with Min/Max buttons)
 *  2. Media controls row (Previous · Play/Pause · Next)
 *
 * All hardware controls send key events via [connectionManager].
 */
@Composable
fun ControlsPanel(
    connectionManager: ConnectionManager,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Observe system state pushed by the server
    val systemState by connectionManager.systemState.collectAsState()
    val brightnessSync = systemState?.let { it.brightness / 100f }
    val volumeSync = systemState?.let { if (it.isMuted) 0f else it.volume / 100f }
    val muteSync = systemState?.isMuted ?: false

    if (isLandscape) {
        // Landscape: horizontal bars stacked vertically with media controls below
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top: brightness slider
            HorizontalGradientSliderControl(
                minIconRes = LucideRes.SunDim,
                maxIconRes = LucideRes.Sun,
                onIncrement = {
                    connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_UP, ACTION_DOWN)
                    connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_UP, ACTION_UP)
                },
                onDecrement = {
                    connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_DOWN, ACTION_DOWN)
                    connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_DOWN, ACTION_UP)
                },
                systemFraction = brightnessSync,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            // Middle: volume slider
            HorizontalGradientSliderControl(
                minIconRes = LucideRes.VolumeX,
                maxIconRes = LucideRes.Volume2,
                onIncrement = {
                    connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_UP, ACTION_DOWN)
                    connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_UP, ACTION_UP)
                },
                onDecrement = {
                    connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_DOWN, ACTION_DOWN)
                    connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_DOWN, ACTION_UP)
                },
                systemFraction = volumeSync,
                minIconActive = muteSync,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            // Bottom: media controls
            MediaControlsRow(
                connectionManager = connectionManager,
                vertical = false,
                modifier = Modifier.fillMaxWidth(),
            )

            // Seek controls: 10s back / forward
            SeekControlsRow(
                connectionManager = connectionManager,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        // Portrait: vertical sliders side by side
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Top: gradient stacks side by side
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GradientSliderControl(
                    maxIconRes = LucideRes.Sun,
                    minIconRes = LucideRes.SunDim,
                    onIncrement = {
                        connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_UP, ACTION_DOWN)
                        connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_UP, ACTION_UP)
                    },
                    onDecrement = {
                        connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_DOWN, ACTION_DOWN)
                        connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_DOWN, ACTION_UP)
                    },
                    systemFraction = brightnessSync,
                    modifier = Modifier.weight(1f),
                )

                GradientSliderControl(
                    maxIconRes = LucideRes.Volume2,
                    minIconRes = LucideRes.VolumeX,
                    onIncrement = {
                        connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_UP, ACTION_DOWN)
                        connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_UP, ACTION_UP)
                    },
                    onDecrement = {
                        connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_DOWN, ACTION_DOWN)
                        connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_DOWN, ACTION_UP)
                    },
                    systemFraction = volumeSync,
                    minIconActive = muteSync,
                    modifier = Modifier.weight(1f),
                )
            }

            // Bottom: media controls
            MediaControlsRow(
                connectionManager = connectionManager,
                vertical = false,
                modifier = Modifier.fillMaxWidth(),
            )

            // Seek controls: 10s back / forward
            SeekControlsRow(
                connectionManager = connectionManager,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
// ─────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────

/**
 * Media controls — can render horizontally or vertically.
 */
@Composable
private fun MediaControlsRow(
    connectionManager: ConnectionManager,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    if (vertical) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(HudSurface)
                .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MediaButton(
                iconRes = LucideRes.SkipBack,
            ) {
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_PREV, ACTION_DOWN)
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_PREV, ACTION_UP)
            }

            MediaButton(
                iconRes = LucideRes.Play,
                size = 34,
            ) {
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_PLAY_PAUSE, ACTION_DOWN)
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_PLAY_PAUSE, ACTION_UP)
            }

            MediaButton(
                iconRes = LucideRes.SkipForward,
            ) {
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_NEXT, ACTION_DOWN)
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_NEXT, ACTION_UP)
            }
        }
    } else {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(HudSurface)
                .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaButton(
                iconRes = LucideRes.SkipBack,
            ) {
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_PREV, ACTION_DOWN)
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_PREV, ACTION_UP)
            }

            MediaButton(
                iconRes = LucideRes.Play,
                size = 34,
            ) {
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_PLAY_PAUSE, ACTION_DOWN)
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_PLAY_PAUSE, ACTION_UP)
            }

            MediaButton(
                iconRes = LucideRes.SkipForward,
            ) {
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_NEXT, ACTION_DOWN)
                connectionManager.sendKeyEvent(KeyCodes.KEY_MEDIA_NEXT, ACTION_UP)
            }
        }
    }
}

/**
 * Seek controls — sends left/right arrow key events, which most media apps
 * (browsers, QuickTime, VLC, streaming apps) interpret as a 10s skip.
 */
@Composable
private fun SeekControlsRow(
    connectionManager: ConnectionManager,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HudSurface)
            .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaButton(
            iconRes = LucideRes.ChevronsLeft,
            repeatOnHold = true,
        ) {
            connectionManager.sendKeyEvent(KeyCodes.KEY_ARROW_LEFT, ACTION_DOWN)
            connectionManager.sendKeyEvent(KeyCodes.KEY_ARROW_LEFT, ACTION_UP)
        }

        MediaButton(
            iconRes = LucideRes.ChevronsRight,
            repeatOnHold = true,
        ) {
            connectionManager.sendKeyEvent(KeyCodes.KEY_ARROW_RIGHT, ACTION_DOWN)
            connectionManager.sendKeyEvent(KeyCodes.KEY_ARROW_RIGHT, ACTION_UP)
        }
    }
}

// Hold-to-repeat timing (mirrors standard OS key-repeat: short initial delay, then fast repeat)
private const val REPEAT_INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 120L

@Composable
private fun MediaButton(
    @DrawableRes iconRes: Int,
    size: Int = 30,
    repeatOnHold: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    if (repeatOnHold) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                onClick()
                delay(REPEAT_INITIAL_DELAY_MS)
                while (isPressed) {
                    onClick()
                    delay(REPEAT_INTERVAL_MS)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(HudSurfaceElevated)
            .border(0.5.dp, HudSurfaceBorder, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                // Repeating buttons fire from the hold loop above; a plain click would double-fire.
                onClick = { if (!repeatOnHold) onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        HudIcon(
            iconRes = iconRes,
            tint = HudCyanDim,
            modifier = Modifier.size(size.dp),
        )
    }
}

// Key action constants (mirrors KeyProcessor)
private const val ACTION_DOWN = 0
private const val ACTION_UP = 1

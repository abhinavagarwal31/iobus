package com.iobus.client.ui.control

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iobus.client.network.ConnectionManager
import com.iobus.client.protocol.KeyCodes
import com.iobus.client.protocol.SystemActionId
import com.iobus.client.ui.theme.*

/**
 * Portrait Controls panel — replaces the old Home/mode-picker screen's center content.
 *
 * Layout (top → bottom):
 *  1. Brightness + Volume gradient stacks side by side
 *  2. Media controls row (Previous · Play/Pause · Next)
 *  3. Lock button (direct, no passcode)
 *  4. Power button (opens passcode dialog)
 *
 * All hardware controls send key events via [connectionManager].
 * System actions (Lock, Power) use [SystemActionId] bytes.
 */
@Composable
fun ControlsPanel(
    connectionManager: ConnectionManager,
    onPowerDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── Gradient stacks: Brightness + Volume ──────

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GradientSliderControl(
                label = "BRIGHTNESS",
                iconRes = LucideRes.Sun,
                onIncrement = {
                    connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_UP, ACTION_DOWN)
                    connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_UP, ACTION_UP)
                },
                onDecrement = {
                    connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_DOWN, ACTION_DOWN)
                    connectionManager.sendKeyEvent(KeyCodes.KEY_BRIGHTNESS_DOWN, ACTION_UP)
                },
                modifier = Modifier.weight(1f),
            )

            GradientSliderControl(
                label = "VOLUME",
                iconRes = LucideRes.Volume2,
                onIncrement = {
                    connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_UP, ACTION_DOWN)
                    connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_UP, ACTION_UP)
                },
                onDecrement = {
                    connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_DOWN, ACTION_DOWN)
                    connectionManager.sendKeyEvent(KeyCodes.KEY_VOLUME_DOWN, ACTION_UP)
                },
                modifier = Modifier.weight(1f),
            )
        }

        // ── Media controls row ────────────────────────

        Row(
            modifier = Modifier
                .fillMaxWidth()
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

        // ── System actions: Lock + Power ──────────────

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Lock — direct action, no passcode
            ControlSystemButton(
                label = "LOCK",
                iconRes = LucideRes.Lock,
                iconColor = HudCyanDim,
                modifier = Modifier.weight(1f),
            ) {
                connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN)
            }

            // Power — opens passcode dialog
            ControlSystemButton(
                label = "POWER",
                iconRes = LucideRes.Power,
                iconColor = HudCyanDim,
                modifier = Modifier.weight(1f),
            ) {
                onPowerDialog()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────

@Composable
private fun MediaButton(
    @DrawableRes iconRes: Int,
    size: Int = 30,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(HudSurfaceElevated)
            .border(0.5.dp, HudSurfaceBorder, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        HudIcon(
            iconRes = iconRes,
            tint = HudCyanDim,
            modifier = Modifier.size(size.dp),
        )
    }
}

@Composable
private fun ControlSystemButton(
    label: String,
    @DrawableRes iconRes: Int,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HudSurface)
            .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HudIcon(
            iconRes = iconRes,
            tint = iconColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = HudTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
    }
}

// Key action constants (mirrors KeyProcessor)
private const val ACTION_DOWN = 0
private const val ACTION_UP = 1

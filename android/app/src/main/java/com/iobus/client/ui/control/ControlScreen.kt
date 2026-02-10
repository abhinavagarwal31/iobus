package com.iobus.client.ui.control

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iobus.client.input.KeyProcessor
import com.iobus.client.input.TouchProcessor
import com.iobus.client.network.ConnectionManager
import com.iobus.client.network.ConnectionState
import com.iobus.client.protocol.SystemActionId
import com.iobus.client.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Main control screen — dynamically switches between input modes.
 *
 * Modes:
 *  - NONE: portrait, shows mode picker (home screen)
 *  - KEYBOARD: landscape, full keyboard
 *  - TRACKPAD: landscape, full trackpad
 *  - COMBINED: landscape, split trackpad (left) + keyboard (right)
 *
 * Orientation is managed programmatically — no activity restart.
 * Connection persists across all mode switches.
 */
@Composable
fun ControlScreen(
    connectionManager: ConnectionManager,
    onDisconnected: () -> Unit,
) {
    val state by connectionManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity

    var inputMode by remember { mutableStateOf(InputMode.NONE) }

    // Navigate back if disconnected
    LaunchedEffect(state) {
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
            onDisconnected()
        }
    }

    // Manage orientation based on mode
    LaunchedEffect(inputMode) {
        activity?.requestedOrientation = if (inputMode.isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Reset to portrait when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val touchProcessor = remember { TouchProcessor(connectionManager) }
    val keyProcessor = remember { KeyProcessor(connectionManager) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBlack),
    ) {
        when (inputMode) {
            InputMode.NONE -> {
                // Portrait home screen — mode picker
                HomePanel(
                    host = connectionManager.host,
                    onModeSelected = { inputMode = it },
                    onDisconnect = { scope.launch { connectionManager.disconnect() } },
                    onLockScreen = { connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN) },
                    onSleep = { connectionManager.sendSystemAction(SystemActionId.SLEEP) },
                    onPowerDialog = { connectionManager.sendSystemAction(SystemActionId.POWER_DIALOG) },
                )
            }

            InputMode.KEYBOARD -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    HudStatusBar(
                        host = connectionManager.host,
                        currentMode = inputMode,
                        onModeSelected = { inputMode = it },
                        onDisconnect = { scope.launch { connectionManager.disconnect() } },
                        onLockScreen = { connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN) },
                        onSleep = { connectionManager.sendSystemAction(SystemActionId.SLEEP) },
                        onPowerDialog = { connectionManager.sendSystemAction(SystemActionId.POWER_DIALOG) },
                    )
                    KeyboardPanel(
                        keyProcessor = keyProcessor,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            InputMode.TRACKPAD -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    HudStatusBar(
                        host = connectionManager.host,
                        currentMode = inputMode,
                        onModeSelected = { inputMode = it },
                        onDisconnect = { scope.launch { connectionManager.disconnect() } },
                        onLockScreen = { connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN) },
                        onSleep = { connectionManager.sendSystemAction(SystemActionId.SLEEP) },
                        onPowerDialog = { connectionManager.sendSystemAction(SystemActionId.POWER_DIALOG) },
                    )
                    TrackpadSurface(
                        touchProcessor = touchProcessor,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                    )
                }
            }

            InputMode.COMBINED -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    HudStatusBar(
                        host = connectionManager.host,
                        currentMode = inputMode,
                        onModeSelected = { inputMode = it },
                        onDisconnect = { scope.launch { connectionManager.disconnect() } },
                        onLockScreen = { connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN) },
                        onSleep = { connectionManager.sendSystemAction(SystemActionId.SLEEP) },
                        onPowerDialog = { connectionManager.sendSystemAction(SystemActionId.POWER_DIALOG) },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TrackpadSurface(
                            touchProcessor = touchProcessor,
                            modifier = Modifier
                                .weight(0.42f)
                                .fillMaxHeight(),
                        )
                        KeyboardPanel(
                            keyProcessor = keyProcessor,
                            compact = true,
                            modifier = Modifier
                                .weight(0.58f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// HUD Status Bar — landscape modes
// ─────────────────────────────────────────────────────────

@Composable
private fun HudStatusBar(
    host: String,
    currentMode: InputMode,
    onModeSelected: (InputMode) -> Unit,
    onDisconnect: () -> Unit,
    onLockScreen: () -> Unit,
    onSleep: () -> Unit,
    onPowerDialog: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(HudSurface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: connection status + host
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Green pulse dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(HudGreen),
            )
            Text(
                text = host,
                color = HudTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )

            Spacer(Modifier.width(8.dp))

            // Mode switcher pills
            val modes = listOf(InputMode.KEYBOARD, InputMode.TRACKPAD, InputMode.COMBINED, InputMode.NONE)
            for (mode in modes) {
                val isActive = mode == currentMode
                val label = if (mode == InputMode.NONE) "✕" else mode.icon
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isActive) HudCyanSubtle else HudModeSurface)
                        .border(
                            0.5.dp,
                            if (isActive) HudCyanDim else HudModeInactive,
                            RoundedCornerShape(4.dp),
                        )
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (isActive) HudCyan else HudTextDisabled,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Right: system controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HudIconButton("🔒", "Lock", HudTextSecondary) { onLockScreen() }
            HudIconButton("⏻", "Sleep", HudTextSecondary) { onSleep() }
            HudIconButton("⚡", "Power", HudAmberDim) { onPowerDialog() }

            Spacer(Modifier.width(4.dp))

            // Disconnect
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(HudRedDim.copy(alpha = 0.2f))
                    .border(0.5.dp, HudRedDim.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable { onDisconnect() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "DC",
                    color = HudRed,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun HudIconButton(
    icon: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = icon,
            color = color,
            fontSize = 12.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────
// Portrait Home Panel — mode picker + system controls
// ─────────────────────────────────────────────────────────

@Composable
private fun HomePanel(
    host: String,
    onModeSelected: (InputMode) -> Unit,
    onDisconnect: () -> Unit,
    onLockScreen: () -> Unit,
    onSleep: () -> Unit,
    onPowerDialog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top: branding + connection
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = "IOBUS",
                color = HudCyan,
                fontSize = 36.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = 8.sp,
            )
            Text(
                text = "CONTROL INTERFACE",
                color = HudTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp,
            )

            Spacer(Modifier.height(8.dp))

            // Connection badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(HudGreen),
                )
                Text(
                    text = "CONNECTED TO $host",
                    color = HudGreenDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                )
            }
        }

        // Center: mode selection cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SELECT MODE",
                color = HudTextDisabled,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )

            ModeCard(
                icon = "⌨",
                title = "KEYBOARD",
                subtitle = "Full MacBook keyboard layout",
                onClick = { onModeSelected(InputMode.KEYBOARD) },
            )
            ModeCard(
                icon = "◎",
                title = "TRACKPAD",
                subtitle = "Precision cursor control",
                onClick = { onModeSelected(InputMode.TRACKPAD) },
            )
            ModeCard(
                icon = "⊞",
                title = "COMBINED",
                subtitle = "Trackpad + keyboard side by side",
                onClick = { onModeSelected(InputMode.COMBINED) },
            )
        }

        // Bottom: system controls + disconnect
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // System action row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SystemActionButton("🔒", "Lock") { onLockScreen() }
                SystemActionButton("⏻", "Sleep") { onSleep() }
                SystemActionButton("⚡", "Power") { onPowerDialog() }
            }

            // Disconnect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HudRedDim.copy(alpha = 0.15f))
                    .border(0.5.dp, HudRedDim.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onDisconnect() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "DISCONNECT",
                    color = HudRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ModeCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HudSurface)
            .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = icon,
            fontSize = 24.sp,
        )
        Column {
            Text(
                text = title,
                color = HudTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )
            Text(
                text = subtitle,
                color = HudTextDisabled,
                fontSize = 10.sp,
                fontWeight = FontWeight.Light,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "→",
            color = HudCyanDim,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun SystemActionButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudSurfaceElevated)
            .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = icon, fontSize = 20.sp)
        Text(
            text = label,
            color = HudTextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )
    }
}

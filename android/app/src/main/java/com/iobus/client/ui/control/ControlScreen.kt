package com.iobus.client.ui.control

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iobus.client.IOBusApplication
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
 * Architecture:
 *  - HOME: portrait, navigation-only landing (header, connection, mode selector)
 *  - CONTROLS: portrait, full-screen control center (brightness, volume, media, lock, power)
 *  - KEYBOARD: landscape, full keyboard
 *  - TRACKPAD: landscape, full trackpad
 *  - COMBINED: landscape, split trackpad (left) + keyboard (right)
 *
 * Home screen shows no content — only mode selector navigation.
 * Control Center is a proper enclosed mode, not rendered on home.
 * Tapping the active mode pill returns to HOME.
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

    var inputMode by remember { mutableStateOf(InputMode.HOME) }
    var showPowerDialog by remember { mutableStateOf(false) }
    val passcodeStore = IOBusApplication.passcodeStore

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
        Crossfade(
            targetState = inputMode,
            animationSpec = tween(durationMillis = 180),
            label = "mode-transition",
        ) { mode ->
            when (mode) {
                InputMode.HOME -> {
                    // Portrait home — navigation only, no content
                    HomeScreen(
                        connectionManager = connectionManager,
                        onModeSelected = { inputMode = it },
                        onDisconnect = { scope.launch { connectionManager.disconnect() } },
                    )
                }

                InputMode.CONTROLS -> {
                    // Portrait control center — full-screen enclosed mode
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding(),
                    ) {
                        PortraitStatusBar(
                            host = connectionManager.host,
                            currentMode = inputMode,
                            onModeSelected = { m ->
                                inputMode = if (m == inputMode) InputMode.HOME else m
                            },
                            onHome = { inputMode = InputMode.HOME },
                            onDisconnect = { scope.launch { connectionManager.disconnect() } },
                            onLockScreen = { connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN) },
                            onPowerDialog = { showPowerDialog = true },
                        )
                        ControlsPanel(
                            connectionManager = connectionManager,
                            onPowerDialog = { showPowerDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 8.dp),
                        )
                    }
                }

                InputMode.KEYBOARD -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        HudStatusBar(
                            host = connectionManager.host,
                            currentMode = inputMode,
                            onModeSelected = { m ->
                                inputMode = if (m == inputMode) InputMode.HOME else m
                            },
                            onHome = { inputMode = InputMode.HOME },
                            onDisconnect = { scope.launch { connectionManager.disconnect() } },
                            onLockScreen = { connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN) },
                            onPowerDialog = { showPowerDialog = true },
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
                            onModeSelected = { m ->
                                inputMode = if (m == inputMode) InputMode.HOME else m
                            },
                            onHome = { inputMode = InputMode.HOME },
                            onDisconnect = { scope.launch { connectionManager.disconnect() } },
                            onLockScreen = { connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN) },
                            onPowerDialog = { showPowerDialog = true },
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
                            onModeSelected = { m ->
                                inputMode = if (m == inputMode) InputMode.HOME else m
                            },
                            onHome = { inputMode = InputMode.HOME },
                            onDisconnect = { scope.launch { connectionManager.disconnect() } },
                            onLockScreen = { connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN) },
                            onPowerDialog = { showPowerDialog = true },
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

        // Power dialog overlay — passcode → options
        if (showPowerDialog) {
            ShutdownConfirmDialog(
                passcodeStore = passcodeStore,
                onShutdown = {
                    connectionManager.sendSystemAction(SystemActionId.SHUTDOWN)
                    showPowerDialog = false
                },
                onRestart = {
                    connectionManager.sendSystemAction(SystemActionId.RESTART)
                    showPowerDialog = false
                },
                onSleep = {
                    connectionManager.sendSystemAction(SystemActionId.SLEEP)
                    showPowerDialog = false
                },
                onDismiss = { showPowerDialog = false },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// Portrait Home Screen — navigation only
// ─────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(
    connectionManager: ConnectionManager,
    onModeSelected: (InputMode) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Top: compact header ──
        Spacer(Modifier.height(36.dp))

        Text(
            text = "IOBUS",
            color = HudCyan,
            fontSize = 28.sp,
            fontWeight = FontWeight.Thin,
            letterSpacing = 6.sp,
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = "CONTROL INTERFACE",
            color = HudTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 3.sp,
        )

        Spacer(Modifier.height(6.dp))

        // Connection badge — tight to header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(HudGreen),
            )
            Text(
                text = "CONNECTED TO ${connectionManager.host}",
                color = HudGreenDim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
            )
        }

        // ── Visual anchor: thin cyan line ──
        Spacer(Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .height(1.5.dp),
        ) {
            drawLine(
                color = HudCyanDim.copy(alpha = 0.35f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1.5f,
            )
        }

        // ── Center: Mode selector (primary focus) ──
        Spacer(Modifier.weight(0.4f))

        // Subtle ambient cyan glow behind mode selector
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                HudCyan.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.width * 0.45f,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ModeSelectorRow(
                currentMode = null,
                onModeSelected = onModeSelected,
            )
        }

        Spacer(Modifier.weight(1f))

        // ── Bottom: Disconnect ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(HudRedSoft.copy(alpha = 0.08f))
                .border(0.5.dp, HudRedSoft.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                .clickable { onDisconnect() }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "DISCONNECT",
                color = HudRedSoft.copy(alpha = 0.72f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

// ─────────────────────────────────────────────────────────
// Mode Selector Row — reusable enclosed container
// ─────────────────────────────────────────────────────────

@Composable
private fun ModeSelectorRow(
    currentMode: InputMode?,
    onModeSelected: (InputMode) -> Unit,
) {
    val modes = listOf(InputMode.KEYBOARD, InputMode.TRACKPAD, InputMode.COMBINED, InputMode.CONTROLS)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HudSurface.copy(alpha = 0.85f))
            .border(0.5.dp, HudSurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            // Inner shadow for depth
            .drawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.25f,
                    ),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (mode in modes) {
            val isActive = mode == currentMode
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            isPressed -> HudCyan.copy(alpha = 0.18f)
                            isActive -> HudCyan.copy(alpha = 0.10f)
                            else -> HudSurfaceElevated.copy(alpha = 0.4f)
                        }
                    )
                    .then(
                        if (isActive || isPressed) Modifier.border(
                            0.5.dp,
                            HudCyanDim.copy(alpha = if (isPressed) 0.55f else 0.35f),
                            RoundedCornerShape(10.dp),
                        ) else Modifier
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { onModeSelected(mode) }
                    .then(
                        if (isPressed) Modifier.drawBehind {
                            drawCircle(
                                color = HudCyan.copy(alpha = 0.10f),
                                radius = size.maxDimension * 0.6f,
                            )
                        } else Modifier
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                HudIcon(
                    iconRes = LucideRes.modeIcon(mode),
                    tint = when {
                        isPressed -> HudCyan
                        isActive -> HudCyan
                        else -> HudCyanDim
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// Portrait Status Bar — Control Center mode (standardized)
// ─────────────────────────────────────────────────────────

@Composable
private fun PortraitStatusBar(
    host: String,
    currentMode: InputMode,
    onModeSelected: (InputMode) -> Unit,
    onHome: () -> Unit,
    onDisconnect: () -> Unit,
    onLockScreen: () -> Unit,
    onPowerDialog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HudTopBarSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Top row: Home | Label | Lock + Power + DC
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Home button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(HudSurfaceElevated.copy(alpha = 0.5f))
                    .clickable { onHome() }
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                HudIcon(
                    iconRes = LucideRes.Home,
                    tint = HudCyanDim,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Center: Screen label + connection dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = currentMode.label.uppercase(),
                    color = HudTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp,
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(HudGreen),
                )
            }

            // Right: Lock + Power + Disconnect
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HudIconButton(iconRes = LucideRes.Lock, tint = HudTextSecondary) { onLockScreen() }
                HudIconButton(iconRes = LucideRes.Power, tint = HudCyanDim) { onPowerDialog() }

                Spacer(Modifier.width(2.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(HudRedSoft.copy(alpha = 0.12f))
                        .border(0.5.dp, HudRedSoft.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .clickable { onDisconnect() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "DC",
                        color = HudRedSoft.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }

        // Bottom row: Mode selector pills
        ModeSelectorRow(
            currentMode = currentMode,
            onModeSelected = onModeSelected,
        )
    }
}

// ─────────────────────────────────────────────────────────
// HUD Status Bar — landscape modes (standardized)
// ─────────────────────────────────────────────────────────

@Composable
private fun HudStatusBar(
    host: String,
    currentMode: InputMode,
    onModeSelected: (InputMode) -> Unit,
    onHome: () -> Unit,
    onDisconnect: () -> Unit,
    onLockScreen: () -> Unit,
    onPowerDialog: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(HudTopBarSurface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: Home button + label + connection dot + mode pills
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Home icon
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onHome() }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                HudIcon(
                    iconRes = LucideRes.Home,
                    tint = HudCyanDim,
                    modifier = Modifier.size(14.dp),
                )
            }

            // Screen label
            Text(
                text = currentMode.label.uppercase(),
                color = HudTextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
            )

            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(HudGreen),
            )

            Spacer(Modifier.width(4.dp))

            // Mode switcher pills — Lucide icons
            val modes = listOf(InputMode.KEYBOARD, InputMode.TRACKPAD, InputMode.COMBINED, InputMode.CONTROLS)
            for (mode in modes) {
                val isActive = mode == currentMode
                val iconColor = if (isActive) HudCyan else HudTextDisabled
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isActive) HudCyan.copy(alpha = 0.12f) else HudModeSurface)
                        .border(
                            if (isActive) 1.dp else 0.5.dp,
                            if (isActive) HudCyanDim else HudModeInactive,
                            RoundedCornerShape(4.dp),
                        )
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HudIcon(
                        iconRes = LucideRes.modeIcon(mode),
                        tint = iconColor,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        // Right: system controls (Lock + Power + DC)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HudIconButton(iconRes = LucideRes.Lock, tint = HudTextSecondary) { onLockScreen() }
            HudIconButton(iconRes = LucideRes.Power, tint = HudCyanDim) { onPowerDialog() }

            Spacer(Modifier.width(4.dp))

            // Disconnect — softer red
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(HudRedSoft.copy(alpha = 0.15f))
                    .border(0.5.dp, HudRedSoft.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .clickable { onDisconnect() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "DC",
                    color = HudRedSoft,
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
    @DrawableRes iconRes: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        HudIcon(
            iconRes = iconRes,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
    }
}

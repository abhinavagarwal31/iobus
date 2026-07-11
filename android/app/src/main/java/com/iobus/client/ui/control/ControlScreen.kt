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
import androidx.compose.ui.platform.LocalConfiguration
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
 *  - HOME: portrait, navigation-only landing (header, connection, mode selector, lock/power row)
 *  - CONTROLS: sensor orientation (adapts to portrait/landscape), full-screen control center
 *  - KEYBOARD: landscape, full keyboard
 *  - TRACKPAD: sensor orientation (adapts to portrait/landscape), full trackpad —
 *    portrait enables one-handed use
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
    onSettings: () -> Unit,
) {
    val state by connectionManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity
    val configuration = LocalConfiguration.current

    var inputMode by remember { mutableStateOf(InputMode.HOME) }
    var showPowerDialog by remember { mutableStateOf(false) }
    val passcodeStore = IOBusApplication.passcodeStore

    // Navigate back if disconnected
    LaunchedEffect(state) {
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
            onDisconnected()
        }
    }

    // Manage orientation based on mode (re-apply on configuration changes)
    LaunchedEffect(inputMode, configuration) {
        activity?.requestedOrientation = when {
            inputMode == InputMode.CONTROLS || inputMode == InputMode.TRACKPAD ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            inputMode.isLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
                        onLockScreen = { connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN) },
                        onPowerDialog = { showPowerDialog = true },
                        onSettings = onSettings,
                    )
                }

                InputMode.CONTROLS -> {
                    // Portrait control center — fullscreen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding(),
                    ) {
                        ControlsPanel(
                            connectionManager = connectionManager,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 8.dp),
                        )
                    }
                }

                InputMode.KEYBOARD -> {
                    // Fullscreen keyboard
                    KeyboardPanel(
                        keyProcessor = keyProcessor,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                InputMode.TRACKPAD -> {
                    // Fullscreen trackpad
                    TrackpadSurface(
                        touchProcessor = touchProcessor,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                    )
                }

                InputMode.COMBINED -> {
                    // Fullscreen combined mode
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

        // Floating menu overlay — shown on all non-HOME modes
        if (inputMode != InputMode.HOME) {
            FloatingMenuButton(
                currentMode = inputMode,
                onModeSelected = { m ->
                    inputMode = if (m == inputMode) InputMode.HOME else m
                },
                onHome = { inputMode = InputMode.HOME },
                onLockScreen = { connectionManager.sendSystemAction(SystemActionId.LOCK_SCREEN) },
                onPowerDialog = { showPowerDialog = true },
                onDisconnect = { scope.launch { connectionManager.disconnect() } },
                onSettings = null,
                modifier = Modifier.fillMaxSize(),
            )
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
    onLockScreen: () -> Unit,
    onPowerDialog: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Top bar: settings gear (pinned top-right) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(HudSurfaceElevated.copy(alpha = 0.5f))
                    .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(6.dp))
                    .clickable { onSettings() }
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                HudIcon(
                    iconRes = LucideRes.Settings,
                    tint = HudCyanDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

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

        // Mac status display — lock and activity
        val systemState by connectionManager.systemState.collectAsState()
        systemState?.let { state ->
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HudSurfaceElevated.copy(alpha = 0.42f))
                    .border(0.5.dp, HudSurfaceBorder.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val lockLabel = if (state.isLocked) "LOCKED" else "UNLOCKED"
                    val lockColor = if (state.isLocked) HudAmber else HudGreen
                    StatusPill(
                        title = "SECURITY",
                        label = lockLabel,
                        iconRes = LucideRes.Lock,
                        color = lockColor,
                        modifier = Modifier.weight(1f),
                    )

                    val activityLabel = state.activityStatus.uppercase(java.util.Locale.ROOT)
                    val activityColor = when (state.activityStatus) {
                        "active" -> HudCyan
                        "idle" -> HudCyanDim
                        else -> HudTextSecondary
                    }
                    val activityIcon = when (state.activityStatus) {
                        "active" -> LucideRes.Zap
                        "idle" -> LucideRes.Touchpad
                        else -> LucideRes.Moon
                    }
                    StatusPill(
                        title = "ACTIVITY",
                        label = activityLabel,
                        iconRes = activityIcon,
                        color = activityColor,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val batteryLabel = "${state.batteryPercent}%"
                    val batteryColor = when {
                        state.isCharging -> HudGreen
                        state.batteryPercent <= 20 -> HudRed
                        else -> HudCyan
                    }
                    val batteryIcon = when {
                        state.isCharging -> LucideRes.BatteryCharging
                        state.batteryPercent <= 20 -> LucideRes.BatteryLow
                        else -> LucideRes.Battery
                    }
                    StatusPill(
                        title = "POWER",
                        label = batteryLabel,
                        iconRes = batteryIcon,
                        color = batteryColor,
                        modifier = Modifier.fillMaxWidth(0.5f),
                        subtitle = if (state.isCharging) "CHARGING" else null,
                    )
                }
            }
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

        // ── System: Lock & Power ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(HudSurface)
                .border(0.5.dp, HudSurfaceBorder, RoundedCornerShape(10.dp))
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeSystemButton(
                label = "LOCK",
                iconRes = LucideRes.Lock,
                modifier = Modifier.weight(1f),
                onClick = onLockScreen,
            )
            HomeSystemButton(
                label = "POWER",
                iconRes = LucideRes.Power,
                modifier = Modifier.weight(1f),
                onClick = onPowerDialog,
            )
        }

        Spacer(Modifier.height(10.dp))

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
// Home system action button — Lock / Power on Home screen
// ─────────────────────────────────────────────────────────

@Composable
private fun HomeSystemButton(
    label: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isPressed) HudCyan.copy(alpha = 0.10f) else HudSurfaceElevated.copy(alpha = 0.5f)
            )
            .border(
                0.5.dp,
                if (isPressed) HudCyanDim.copy(alpha = 0.45f) else HudSurfaceBorder,
                RoundedCornerShape(10.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HudIcon(
            iconRes = iconRes,
            tint = if (isPressed) HudCyan else HudCyanDim,
            modifier = Modifier.size(22.dp),
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

// ─────────────────────────────────────────────────────────
// Status Pill — displays Mac lock and activity status
// ─────────────────────────────────────────────────────────

@Composable
private fun StatusPill(
    title: String,
    label: String,
    @DrawableRes iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = HudTextSecondary.copy(alpha = 0.72f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = color.copy(alpha = 0.85f),
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HudIcon(
                iconRes = iconRes,
                tint = color.copy(alpha = 0.92f),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                color = color.copy(alpha = 0.9f),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                maxLines = 1,
            )
        }
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


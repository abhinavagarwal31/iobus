package com.iobus.client.ui.control

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iobus.client.IOBusApplication
import com.iobus.client.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Floating menu button — HUD-style expandable vertical menu.
 *
 * When collapsed: Shows a Zap icon button positioned safely in corner.
 * When expanded: Displays scrollable vertical menu expanding from button position.
 *
 * Menu items:
 * - Mode selection (Keyboard, Trackpad, Combined, Controls)
 * - Home
 * - Lock
 * - Power
 * - Disconnect
 * - Settings
 *
 * Features:
 * - Expands from corner button position
 * - Scrollable menu list
 * - Snap animations with spring physics
 * - Haptic feedback on all interactions
 * - No overlap with keyboard or other controls
 */
@Composable
fun RadialMenuButton(
    currentMode: InputMode,
    onModeSelected: (InputMode) -> Unit,
    onHome: () -> Unit,
    onLockScreen: () -> Unit,
    onPowerDialog: () -> Unit,
    onDisconnect: () -> Unit,
    onSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Scale animation for menu expansion
    val menuScale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "menu_scale",
    )

    // Alpha animation for menu
    val menuAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "menu_alpha",
    )

    Box(modifier = modifier) {
        // Background dimming overlay when expanded
        if (isExpanded && menuAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * menuAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // Close menu when clicking on background
                        IOBusApplication.hapticManager.keyTap()
                        isExpanded = false
                    },
            )
        }

        // Main floating button - positioned in safe corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
            ) {
                FloatingActionButton(
                    isExpanded = isExpanded,
                    onClick = {
                        IOBusApplication.hapticManager.keyTap()
                        scope.launch {
                            isExpanded = !isExpanded
                        }
                    },
                )

                // Expanded menu - appears below button
                if (isExpanded && menuAlpha > 0.01f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExpandableMenu(
                        currentMode = currentMode,
                        onModeSelected = { mode ->
                            IOBusApplication.hapticManager.click()
                            onModeSelected(mode)
                            isExpanded = false
                        },
                        onHome = {
                            IOBusApplication.hapticManager.click()
                            onHome()
                            isExpanded = false
                        },
                        onLockScreen = {
                            IOBusApplication.hapticManager.click()
                            onLockScreen()
                            isExpanded = false
                        },
                        onPowerDialog = {
                            IOBusApplication.hapticManager.click()
                            onPowerDialog()
                            isExpanded = false
                        },
                        onDisconnect = {
                            IOBusApplication.hapticManager.click()
                            onDisconnect()
                            isExpanded = false
                        },
                        onSettings = onSettings?.let { settingsAction ->
                            {
                                IOBusApplication.hapticManager.click()
                                settingsAction()
                                isExpanded = false
                            }
                        },
                        scale = menuScale,
                        alpha = menuAlpha,
                    )
                }
            }
        }
    }
}

/**
 * Main circular floating action button.
 */
@Composable
private fun FloatingActionButton(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "button_scale",
    )

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "button_rotation",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(HudKeyFnSurface)
            .drawBehind {
                // Outer glow
                drawCircle(
                    color = HudCyan.copy(alpha = if (isExpanded) 0.5f else 0.3f),
                    radius = size.width / 2 + 6.dp.toPx(),
                )
                // Border
                drawCircle(
                    color = HudCyan.copy(alpha = if (isExpanded) 1f else 0.6f),
                    radius = size.width / 2,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // X icon when expanded, Zap when collapsed
        HudIcon(
            iconRes = if (isExpanded) LucideRes.ChevronRight else LucideRes.Zap,
            tint = if (isExpanded) Color.White else HudCyan,
            modifier = Modifier
                .size(24.dp)
                .padding(2.dp),
        )
    }
}

/**
 * Expandable vertical menu - scrollable list of menu items.
 */
@Composable
private fun ExpandableMenu(
    currentMode: InputMode,
    onModeSelected: (InputMode) -> Unit,
    onHome: () -> Unit,
    onLockScreen: () -> Unit,
    onPowerDialog: () -> Unit,
    onDisconnect: () -> Unit,
    onSettings: (() -> Unit)?,
    scale: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    // Define menu items
    val menuItems = buildList {
        // Mode selection items
        add(MenuItem.Mode(InputMode.KEYBOARD, currentMode == InputMode.KEYBOARD))
        add(MenuItem.Mode(InputMode.TRACKPAD, currentMode == InputMode.TRACKPAD))
        add(MenuItem.Mode(InputMode.COMBINED, currentMode == InputMode.COMBINED))
        add(MenuItem.Mode(InputMode.CONTROLS, currentMode == InputMode.CONTROLS))
        
        // Divider
        add(MenuItem.Divider)
        
        // Action items
        add(MenuItem.Action("Home", LucideRes.Home, isDestructive = false) { onHome() })
        add(MenuItem.Action("Lock", LucideRes.Lock, isDestructive = false) { onLockScreen() })
        add(MenuItem.Action("Power", LucideRes.Power, isDestructive = false) { onPowerDialog() })
        
        // Settings (optional)
        onSettings?.let {
            add(MenuItem.Action("Settings", LucideRes.Settings, isDestructive = false, it))
        }
        
        // Divider before destructive action
        add(MenuItem.Divider)
        add(MenuItem.Action("Disconnect", LucideRes.ChevronRight, isDestructive = true) { onDisconnect() })
    }

    Box(
        modifier = modifier
            .width(180.dp)
            .heightIn(max = 400.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(HudSurface.copy(alpha = 0.95f * alpha))
            .border(1.dp, HudCyan.copy(alpha = 0.3f * alpha), RoundedCornerShape(12.dp))
            .drawBehind {
                // Inner glow
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            HudCyan.copy(alpha = 0.1f * alpha),
                            Color.Transparent,
                        ),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                )
            },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(menuItems) { item ->
                when (item) {
                    is MenuItem.Mode -> {
                        MenuModeItem(
                            mode = item.mode,
                            isActive = item.isActive,
                            onClick = { onModeSelected(item.mode) },
                        )
                    }
                    is MenuItem.Action -> {
                        MenuActionItem(
                            label = item.label,
                            iconRes = item.iconRes,
                            isDestructive = item.isDestructive,
                            onClick = item.onClick,
                        )
                    }
                    is MenuItem.Divider -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .height(1.dp)
                                .background(HudCyan.copy(alpha = 0.15f))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mode selector menu item.
 */
@Composable
private fun MenuModeItem(
    mode: InputMode,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isActive -> HudCyan.copy(alpha = 0.15f)
                    isPressed -> HudKeyPressed.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isActive) 1.dp else 0.5.dp,
                color = when {
                    isActive -> HudCyan.copy(alpha = 0.6f)
                    isPressed -> HudCyan.copy(alpha = 0.4f)
                    else -> HudCyan.copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isActive) HudCyan.copy(alpha = 0.2f) else HudKeyFnSurface.copy(alpha = 0.5f))
                .drawBehind {
                    drawCircle(
                        color = if (isActive) HudCyan.copy(alpha = 0.6f) else HudCyan.copy(alpha = 0.25f),
                        radius = size.width / 2,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            HudIcon(
                iconRes = LucideRes.modeIcon(mode),
                tint = if (isActive) HudCyan else HudCyanDim,
                modifier = Modifier.size(18.dp),
            )
        }

        // Label
        Text(
            text = mode.label.uppercase(),
            color = if (isActive) HudCyan else HudTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Active indicator
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(HudCyan)
            )
        }
    }
}

/**
 * Action menu item.
 */
@Composable
private fun MenuActionItem(
    label: String,
    @DrawableRes iconRes: Int,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val color = if (isDestructive) HudRedSoft else HudCyanDim

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isDestructive && isPressed -> HudRedSoft.copy(alpha = 0.15f)
                    isPressed -> HudKeyPressed.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = 0.5.dp,
                color = if (isDestructive) {
                    HudRedSoft.copy(alpha = if (isPressed) 0.4f else 0.2f)
                } else {
                    HudCyan.copy(alpha = if (isPressed) 0.3f else 0.1f)
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Icon
        HudIcon(
            iconRes = iconRes,
            tint = if (isPressed) Color.White else color,
            modifier = Modifier.size(18.dp),
        )

        // Label
        Text(
            text = label.uppercase(),
            color = if (isPressed) Color.White else color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )
    }
}

/**
 * Menu item sealed class.
 */
private sealed class MenuItem {
    data class Mode(val mode: InputMode, val isActive: Boolean) : MenuItem()
    data class Action(
        val label: String,
        @DrawableRes val iconRes: Int,
        val isDestructive: Boolean = false,
        val onClick: () -> Unit,
    ) : MenuItem()
    object Divider : MenuItem()
}

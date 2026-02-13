package com.iobus.client.ui.control

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iobus.client.input.KeyProcessor
import com.iobus.client.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Full on-screen keyboard — Iron Man HUD style, fills all available space.
 *
 * Key heights are computed from available space using weight-based layout:
 *  - Function row: 0.55x weight
 *  - Standard rows: 1.0x weight
 *  - Total rows expand to fill the entire parent height
 *
 * Neon glow effects:
 *  - Thin cyan border outline on all keys (always visible)
 *  - Brighter glow on press with outer bloom + inner radial gradient
 *  - Active modifiers: steady cyan border
 */
@Composable
fun KeyboardPanel(
    keyProcessor: KeyProcessor,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var shiftActive by remember { mutableStateOf(false) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var cmdActive by remember { mutableStateOf(false) }
    var fnActive by remember { mutableStateOf(false) }

    // Fun-key flash: drives a brief whole-keyboard overlay glow
    val funFlashAlpha = remember { Animatable(0f) }
    // JARVIS deferred-feature toast
    val jarvisToastAlpha = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // Row gap — tight to maximize key real estate
    val gap = if (compact) 1.dp else 1.5.dp

    // Weight factors: fn row is shorter, standard rows are equal
    val fnWeight = if (compact) 0.45f else 0.55f
    val stdWeight = 1.0f
    val totalRows = KeyboardLayout.allRows.size
    // Total weight: 1 fn row + (totalRows-1) standard rows
    val totalWeight = fnWeight + stdWeight * (totalRows - 1)

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudKeyboardBg)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            // Fun key flash overlay — covers entire keyboard
            .drawWithContent {
                drawContent()
                val alpha = funFlashAlpha.value
                if (alpha > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                HudCyan.copy(alpha = alpha * 0.35f),
                                HudCyan.copy(alpha = alpha * 0.08f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2, size.height * 0.05f),
                            radius = size.width * 1.2f,
                        ),
                    )
                }
            },
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        KeyboardLayout.allRows.forEachIndexed { rowIndex, row ->
            val isFnRow = rowIndex == 0
            val isSpaceRow = rowIndex == KeyboardLayout.allRows.lastIndex
            val rowWeight = if (isFnRow) fnWeight else stdWeight

            if (isSpaceRow) {
                SpaceBarRow(
                    keys = row,
                    keyProcessor = keyProcessor,
                    shiftActive = shiftActive,
                    ctrlActive = ctrlActive,
                    altActive = altActive,
                    cmdActive = cmdActive,
                    fnActive = fnActive,
                    gap = gap,
                    onModifierToggle = { flag, active ->
                        when (flag) {
                            KeyProcessor.MOD_SHIFT -> shiftActive = active
                            KeyProcessor.MOD_CTRL -> ctrlActive = active
                            KeyProcessor.MOD_ALT -> altActive = active
                            KeyProcessor.MOD_CMD -> cmdActive = active
                        }
                    },
                    onFnToggle = { fnActive = !fnActive },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(rowWeight / totalWeight),
                )
            } else {
                KeyboardRow(
                    keys = row,
                    keyProcessor = keyProcessor,
                    shiftActive = shiftActive,
                    ctrlActive = ctrlActive,
                    altActive = altActive,
                    cmdActive = cmdActive,
                    fnActive = fnActive,
                    gap = gap,
                    onModifierToggle = { flag, active ->
                        when (flag) {
                            KeyProcessor.MOD_SHIFT -> shiftActive = active
                            KeyProcessor.MOD_CTRL -> ctrlActive = active
                            KeyProcessor.MOD_ALT -> altActive = active
                            KeyProcessor.MOD_CMD -> cmdActive = active
                        }
                    },
                    onFunKeyPressed = {
                        coroutineScope.launch {
                            funFlashAlpha.snapTo(1f)
                            funFlashAlpha.animateTo(0f, tween(600))
                        }
                    },
                    onDeferredKeyPressed = {
                        coroutineScope.launch {
                            jarvisToastAlpha.snapTo(1f)
                            jarvisToastAlpha.animateTo(0f, tween(1500))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(rowWeight / totalWeight),
                )
            }
        }
    }

    // ── Deferred-feature toast overlay ──
    val toastAlpha = jarvisToastAlpha.value
    if (toastAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .drawBehind {
                        // Glow background
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    HudCyan.copy(alpha = toastAlpha * 0.12f),
                                    HudCyan.copy(alpha = toastAlpha * 0.12f),
                                    Color.Transparent,
                                ),
                            ),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                        drawRoundRect(
                            color = HudCyan.copy(alpha = toastAlpha * 0.4f),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(width = 0.5.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Not available in v1",
                    color = HudCyan.copy(alpha = toastAlpha * 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
    } // end wrapping Box
}

@Composable
private fun KeyboardRow(
    keys: List<KeyDef>,
    keyProcessor: KeyProcessor,
    shiftActive: Boolean,
    ctrlActive: Boolean,
    altActive: Boolean,
    cmdActive: Boolean,
    fnActive: Boolean,
    gap: androidx.compose.ui.unit.Dp,
    onModifierToggle: (Int, Boolean) -> Unit,
    onFunKeyPressed: () -> Unit = {},
    onDeferredKeyPressed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        val totalWeight = keys.sumOf { it.width.toDouble() }.toFloat()
        for (key in keys) {
            if (key.type == KeyType.FUN_KEY) {
                FunKeyCap(
                    onPressed = onFunKeyPressed,
                    modifier = Modifier
                        .weight(key.width / totalWeight)
                        .fillMaxHeight(),
                )
            } else {
                HudKeyCap(
                    keyDef = key,
                    keyProcessor = keyProcessor,
                    shiftActive = shiftActive,
                    fnActive = fnActive,
                    isModifierActive = when (key.modifierFlag) {
                        KeyProcessor.MOD_SHIFT -> shiftActive
                        KeyProcessor.MOD_CTRL -> ctrlActive
                        KeyProcessor.MOD_ALT -> altActive
                        KeyProcessor.MOD_CMD -> cmdActive
                        else -> false
                    },
                    onModifierToggle = onModifierToggle,
                    onDeferredKeyPressed = onDeferredKeyPressed,
                    modifier = Modifier
                        .weight(key.width / totalWeight)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Space bar row with inverted-T arrow cluster.
 * Arrow ↑ and ↓ are stacked vertically, each taking half the row height.
 */
@Composable
private fun SpaceBarRow(
    keys: List<KeyDef>,
    keyProcessor: KeyProcessor,
    shiftActive: Boolean,
    ctrlActive: Boolean,
    altActive: Boolean,
    cmdActive: Boolean,
    fnActive: Boolean,
    gap: androidx.compose.ui.unit.Dp,
    onModifierToggle: (Int, Boolean) -> Unit,
    onFnToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom,
    ) {
        val totalWeight = keys.sumOf { it.width.toDouble() }.toFloat()

        var i = 0
        while (i < keys.size) {
            val key = keys[i]

            if (key.type == KeyType.HALF_HEIGHT && i + 1 < keys.size &&
                keys[i + 1].type == KeyType.HALF_HEIGHT
            ) {
                val upKey = key
                val downKey = keys[i + 1]

                Column(
                    modifier = Modifier
                        .weight(upKey.width / totalWeight)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    HudKeyCap(
                        keyDef = upKey,
                        keyProcessor = keyProcessor,
                        shiftActive = shiftActive,
                        fnActive = fnActive,
                        isModifierActive = false,
                        onModifierToggle = onModifierToggle,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    HudKeyCap(
                        keyDef = downKey,
                        keyProcessor = keyProcessor,
                        shiftActive = shiftActive,
                        fnActive = fnActive,
                        isModifierActive = false,
                        onModifierToggle = onModifierToggle,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                i += 2
            } else {
                val isFnKey = key.label == "fn"
                HudKeyCap(
                    keyDef = key,
                    keyProcessor = keyProcessor,
                    shiftActive = shiftActive,
                    fnActive = fnActive,
                    isModifierActive = when (key.modifierFlag) {
                        KeyProcessor.MOD_SHIFT -> shiftActive
                        KeyProcessor.MOD_CTRL -> ctrlActive
                        KeyProcessor.MOD_ALT -> altActive
                        KeyProcessor.MOD_CMD -> cmdActive
                        else -> false
                    },
                    onModifierToggle = if (isFnKey) { _, _ -> onFnToggle() } else onModifierToggle,
                    overrideFnToggle = isFnKey,
                    isFnActive = fnActive,
                    modifier = Modifier
                        .weight(key.width / totalWeight)
                        .fillMaxHeight(),
                )
                i++
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// HUD Key Cap — Iron Man neon glow style
// ─────────────────────────────────────────────────────────

/**
 * Single key — Iron Man HUD aesthetic.
 *
 * Layers (bottom to top):
 *  1. Outer neon glow bloom (only on press) — drawn outside bounds via drawBehind
 *  2. Matte dark fill (#0E0E18 base)
 *  3. Thin neon cyan outline (0.75dp, always visible at low alpha; full cyan on press)
 *  4. Inner radial glow on press — soft cyan→transparent gradient from center
 *  5. Text label — crisp, high contrast
 *
 * All color transitions animated at 100ms for snappy feel.
 */
@Composable
private fun HudKeyCap(
    keyDef: KeyDef,
    keyProcessor: KeyProcessor,
    shiftActive: Boolean,
    fnActive: Boolean,
    isModifierActive: Boolean,
    onModifierToggle: (Int, Boolean) -> Unit,
    onDeferredKeyPressed: () -> Unit = {},
    modifier: Modifier = Modifier,
    overrideFnToggle: Boolean = false,
    isFnActive: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    // ── Target colors ──
    val targetBg = when {
        isPressed -> HudKeyPressed
        overrideFnToggle && isFnActive -> HudKeyModifierActive
        keyDef.type == KeyType.MODIFIER && isModifierActive -> HudKeyModifierActive
        keyDef.type == KeyType.FUNCTION -> HudKeyFnSurface
        else -> HudKeySurface
    }

    val targetBorder = when {
        isPressed -> HudCyan
        overrideFnToggle && isFnActive -> HudCyan.copy(alpha = 0.6f)
        keyDef.type == KeyType.MODIFIER && isModifierActive -> HudCyan.copy(alpha = 0.6f)
        keyDef.type == KeyType.ACCENT -> HudCyanDim.copy(alpha = 0.5f)
        else -> HudCyanDim.copy(alpha = 0.18f) // always-visible faint neon outline
    }

    val targetText = when {
        isPressed -> Color.White
        keyDef.type == KeyType.ACCENT -> HudCyan
        overrideFnToggle && isFnActive -> HudCyan
        keyDef.type == KeyType.MODIFIER && isModifierActive -> HudCyan
        keyDef.type == KeyType.FUNCTION -> HudTextFnKey
        else -> HudTextPrimary
    }

    val animBg by animateColorAsState(targetBg, tween(120), label = "bg")
    val animBorder by animateColorAsState(targetBorder, tween(140), label = "bdr")
    val animText by animateColorAsState(targetText, tween(100), label = "txt")

    // Animated glow intensity for press effect (120–160ms per spec)
    val bloomAlpha by animateFloatAsState(
        if (isPressed) 0.25f else 0f,
        tween(140), label = "bloom",
    )
    val innerGlowAlpha by animateFloatAsState(
        if (isPressed) 0.10f else 0f,
        tween(140), label = "iglow",
    )

    // Display label
    // Default: media icon is primary (matches MacBook behavior)
    // fn active: show F-key name (fn switches to raw F1-F12)
    val displayLabel = when {
        fnActive && keyDef.type == KeyType.FUNCTION && keyDef.secondaryLabel != null ->
            keyDef.label   // "F1", "F2", etc. — fn sends actual F-key
        shiftActive && keyDef.shiftLabel != null -> keyDef.shiftLabel
        else -> keyDef.label
    }

    // Effective keycode — matches MacBook default behaviour:
    //   Default (no fn): media action (brightness, volume, play, etc.)
    //   fn active: raw F1-F12
    // Note: F3–F6 are deferred v2 features; they show a toast in media mode.
    val effectiveKeyCode = if (keyDef.type == KeyType.FUNCTION && keyDef.secondaryLabel != null) {
        if (fnActive) keyDef.keyCode  // fn → actual F-key
        else KeyboardLayout.fnMediaMap[keyDef.keyCode] ?: keyDef.keyCode  // default → media
    } else {
        keyDef.keyCode
    }

    // Check if this is a deferred key (F3–F6 in media mode, caps lock always)
    val isDeferred = keyDef.keyCode in KeyboardLayout.deferredKeys
            && (keyDef.type != KeyType.FUNCTION || !fnActive)

    val fontSize = when {
        keyDef.type == KeyType.FUNCTION && keyDef.secondaryLabel != null && fnActive -> 7.sp // dual label mode (fn shows F-key + dimmed media)
        keyDef.type == KeyType.FUNCTION -> 9.sp
        keyDef.type == KeyType.HALF_HEIGHT -> 11.sp
        displayLabel.length <= 1 -> 14.sp
        displayLabel.length <= 3 -> 12.sp
        else -> 9.sp
    }

    val cornerRadius = 4.dp
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val cr = cornerRadius.toPx()

                // Layer 1: Outer glow bloom on press (animated fade)
                if (bloomAlpha > 0f) {
                    val bloom = 3.dp.toPx()
                    drawRoundRect(
                        color = HudCyan.copy(alpha = bloomAlpha),
                        cornerRadius = CornerRadius(cr + bloom),
                        topLeft = Offset(-bloom, -bloom),
                        size = Size(size.width + bloom * 2, size.height + bloom * 2),
                    )
                }

                // Layer 2: Fill
                drawRoundRect(
                    color = animBg,
                    cornerRadius = CornerRadius(cr),
                )

                // Layer 3: Inner radial glow (animated fade)
                if (innerGlowAlpha > 0f) {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                HudCyan.copy(alpha = innerGlowAlpha),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width * 0.7f,
                        ),
                        cornerRadius = CornerRadius(cr),
                    )
                } else if ((keyDef.type == KeyType.MODIFIER && isModifierActive) ||
                    (overrideFnToggle && isFnActive)
                ) {
                    // Subtle inner glow for active modifiers
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                HudCyan.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width * 0.6f,
                        ),
                        cornerRadius = CornerRadius(cr),
                    )
                }

                // Layer 4: Neon outline border
                drawRoundRect(
                    color = animBorder,
                    cornerRadius = CornerRadius(cr),
                    style = Stroke(width = if (isPressed) 1.5.dp.toPx() else 0.75.dp.toPx()),
                )
            }
            .pointerInput(keyDef, fnActive) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        if (overrideFnToggle) {
                            onModifierToggle(0, false)
                        } else if (keyDef.modifierFlag != 0) {
                            val newState = keyProcessor.toggleModifier(keyDef.modifierFlag)
                            onModifierToggle(keyDef.modifierFlag, newState)
                        } else if (isDeferred) {
                            onDeferredKeyPressed()
                        } else {
                            keyProcessor.pressKey(effectiveKeyCode)
                            if (shiftActive) {
                                onModifierToggle(KeyProcessor.MOD_SHIFT, false)
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (keyDef.type == KeyType.FUNCTION && keyDef.secondaryLabel != null) {
            // Function key — show Lucide icon centered, tiny F-number in bottom-right corner
            Box(modifier = Modifier.fillMaxSize()) {
                // Lucide icon (centered)
                val fnIconRes = LucideRes.fnKeyIcons[keyDef.label]
                if (fnIconRes != null) {
                    HudIcon(
                        iconRes = fnIconRes,
                        tint = animText,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp),
                    )
                }
                // Tiny F-number in bottom-right corner
                Text(
                    text = keyDef.label,
                    color = animText.copy(alpha = if (fnActive) 1.0f else 0.3f),
                    fontSize = 5.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 1.dp),
                )
            }
        } else {
            Text(
                text = displayLabel,
                color = animText,
                fontSize = fontSize,
                fontWeight = if (keyDef.type == KeyType.REGULAR) FontWeight.Normal else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// Fun Key Cap — visual-only custom key with HUD spark icon
// ─────────────────────────────────────────────────────────

@Composable
private fun FunKeyCap(
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val iconColor by animateColorAsState(
        if (isPressed) Color.White else HudCyan,
        tween(80), label = "fun_icon",
    )
    val bgColor by animateColorAsState(
        if (isPressed) HudKeyPressed else HudKeyFnSurface,
        tween(100), label = "fun_bg",
    )
    val borderColor by animateColorAsState(
        if (isPressed) HudCyan else HudCyan.copy(alpha = 0.3f),
        tween(80), label = "fun_bdr",
    )

    val cornerRadius = 4.dp
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val cr = cornerRadius.toPx()
                if (isPressed) {
                    val bloom = 4.dp.toPx()
                    drawRoundRect(
                        color = HudCyan.copy(alpha = 0.35f),
                        cornerRadius = CornerRadius(cr + bloom),
                        topLeft = Offset(-bloom, -bloom),
                        size = Size(size.width + bloom * 2, size.height + bloom * 2),
                    )
                }
                drawRoundRect(color = bgColor, cornerRadius = CornerRadius(cr))
                if (isPressed) {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(HudCyan.copy(alpha = 0.2f), Color.Transparent),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width * 0.8f,
                        ),
                        cornerRadius = CornerRadius(cr),
                    )
                }
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(cr),
                    style = Stroke(width = if (isPressed) 1.5.dp.toPx() else 0.75.dp.toPx()),
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onPressed() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        HudIcon(
            iconRes = LucideRes.Zap,
            tint = iconColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
        )
    }
}

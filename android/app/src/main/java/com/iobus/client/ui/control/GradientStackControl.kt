package com.iobus.client.ui.control

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.iobus.client.ui.theme.*

/**
 * Continuous vertical gradient slider control.
 *
 * A smooth, fluid energy column that fills from bottom to top.
 * No segmentation, no discrete steps — fully continuous visual.
 *
 * Internally quantized to [steps] boundaries for firing key events,
 * but the visual always tracks the exact touch position smoothly.
 *
 * @param maxIconRes Icon for the top (max) quick-set button (e.g. Sun for brightness, Volume2 for volume).
 * @param minIconRes Icon for the bottom (min) quick-set button (e.g. SunDim for brightness, VolumeX for volume).
 * @param onIncrement Called when value crosses an upward step boundary.
 * @param onDecrement Called when value crosses a downward step boundary.
 * @param steps Internal quantization for key events (default 16). Visual is unaffected.
 * @param systemFraction When non-null, the slider animates to this value while the user is not dragging.
 *   Used for brightness/volume sync pushed from the server.
 * @param minIconActive When true, the min button renders in an active/highlighted state.
 *   Used to indicate mute is engaged on the volume slider.
 */
@Composable
fun GradientSliderControl(
    @DrawableRes maxIconRes: Int,
    @DrawableRes minIconRes: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 16,
    systemFraction: Float? = null,
    minIconActive: Boolean = false,
) {
    // Raw fraction 0f..1f — starts at 0 until server sync arrives
    var rawFraction by remember { mutableFloatStateOf(0f) }
    // Quantized step for firing discrete key events
    var quantizedStep by remember { mutableIntStateOf(0) }
    // True while finger is on the slider
    var isAdjusting by remember { mutableStateOf(false) }

    // Apply server-pushed value when the user is not actively adjusting
    LaunchedEffect(systemFraction) {
        if (systemFraction != null && !isAdjusting) {
            val clamped = systemFraction.coerceIn(0f, 1f)
            rawFraction = clamped
            quantizedStep = (clamped * steps).toInt().coerceIn(0, steps)
        }
    }

    // Smooth animated fraction (fast during drag, micro ease on release)
    val animatedFraction by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = tween(durationMillis = if (isAdjusting) 16 else 100),
        label = "sliderFill",
    )

    // Glow intensity during interaction
    val glowAlpha by animateFloatAsState(
        targetValue = if (isAdjusting) 1f else 0f,
        animationSpec = tween(durationMillis = if (isAdjusting) 60 else 280),
        label = "adjustGlow",
    )

    // Fire increment/decrement when crossing quantized boundaries
    fun updateFraction(newFraction: Float) {
        val clamped = newFraction.coerceIn(0f, 1f)
        rawFraction = clamped
        val newStep = (clamped * steps).toInt().coerceIn(0, steps)
        if (newStep > quantizedStep) {
            repeat(newStep - quantizedStep) { onIncrement() }
        } else if (newStep < quantizedStep) {
            repeat(quantizedStep - newStep) { onDecrement() }
        }
        quantizedStep = newStep
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Max button — instantly sets level to 100%
        SliderEndButton(iconRes = maxIconRes) { updateFraction(1f) }

        Spacer(Modifier.height(2.dp))

        // Continuous slider track
        Box(
            modifier = Modifier
                .width(46.dp)
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(HudSliderTrack)
                .border(0.5.dp, HudSliderBorder, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        isAdjusting = true
                        // Top = 1.0, Bottom = 0.0
                        val fraction = 1f - (offset.y / size.height.toFloat())
                        updateFraction(fraction)
                        isAdjusting = false
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isAdjusting = true
                            val fraction = 1f - (offset.y / size.height.toFloat())
                            updateFraction(fraction)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val fraction = 1f - (change.position.y / size.height.toFloat())
                            updateFraction(fraction)
                        },
                        onDragEnd = { isAdjusting = false },
                        onDragCancel = { isAdjusting = false },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cr = 14.dp.toPx()
                val fillH = h * animatedFraction
                val fillTop = h - fillH

                // ── Track depth: subtle inner shadow at top ──
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = h * 0.12f,
                    ),
                    cornerRadius = CornerRadius(cr),
                )

                // ── Active fill ──
                if (animatedFraction > 0.002f) {
                    val clip = Path().apply {
                        addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(cr)))
                    }
                    clipPath(clip) {
                        // Main gradient: cyan (top) → mid blue → electric blue (bottom)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    HudSliderGradientTop,
                                    HudSliderGradientMid,
                                    HudSliderGradientBot,
                                ),
                                startY = 0f,
                                endY = h,
                            ),
                            topLeft = Offset(0f, fillTop),
                            size = Size(w, fillH),
                        )

                        // Inner highlight — subtle left-edge depth
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.045f),
                                    Color.Transparent,
                                ),
                                startX = 0f,
                                endX = w * 0.4f,
                            ),
                            topLeft = Offset(0f, fillTop),
                            size = Size(w, fillH),
                        )

                        // Fill edge glow — soft luminous line at current level
                        val edgeGlowH = 4.dp.toPx().coerceAtMost(fillH)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    HudCyan.copy(alpha = 0.22f),
                                    Color.Transparent,
                                ),
                                startY = fillTop,
                                endY = fillTop + edgeGlowH,
                            ),
                            topLeft = Offset(0f, fillTop),
                            size = Size(w, edgeGlowH),
                        )
                    }
                }

                // ── Interaction glow pulse ──
                if (glowAlpha > 0f) {
                    val clip = Path().apply {
                        addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(cr)))
                    }
                    clipPath(clip) {
                        // Radial glow centered at fill level edge
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    HudCyan.copy(alpha = glowAlpha * 0.10f),
                                    Color.Transparent,
                                ),
                                center = Offset(w / 2f, fillTop),
                                radius = w * 1.2f,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        // Min button — instantly sets level to 0% (glows when muted)
        SliderEndButton(iconRes = minIconRes, isActive = minIconActive) { updateFraction(0f) }
    }
}

// ─────────────────────────────────────────────────────────
// Slider end-cap buttons (Max / Min)
// ─────────────────────────────────────────────────────────

/**
 * Circular icon button placed at the top (max) or bottom (min) of a slider.
 *
 * 48dp circle, dark glass surface matching media buttons.
 * Press triggers scale-down to 0.96f (60 ms) + soft cyan border glow
 * that fades out over 130 ms. No bounce.
 */
@Composable
private fun SliderEndButton(
    @DrawableRes iconRes: Int,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Press scale: 0.96f down, springs back — no bounce
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = if (isPressed) 60 else 100),
        label = "endButtonScale",
    )

    // Glow alpha: instant on press, fades out in 130 ms. Also on when isActive.
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed || isActive) 1f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 20 else 130),
        label = "endButtonGlow",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(HudSurfaceElevated)
            .border(
                0.5.dp,
                if (glowAlpha > 0.01f) HudCyan.copy(alpha = glowAlpha * 0.5f) else HudSurfaceBorder,
                CircleShape,
            )
            .background(HudCyan.copy(alpha = glowAlpha * 0.10f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        HudIcon(
            iconRes = iconRes,
            tint = if (glowAlpha > 0.1f) HudCyan else HudCyanDim,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────
// Horizontal Gradient Slider (Landscape Mode)
// ─────────────────────────────────────────────────────────

/**
 * Continuous horizontal gradient slider control for landscape mode.
 *
 * A smooth, fluid energy bar that fills from left to right.
 * No segmentation, no discrete steps — fully continuous visual.
 *
 * Internally quantized to [steps] boundaries for firing key events,
 * but the visual always tracks the exact touch position smoothly.
 *
 * @param minIconRes Icon for the left (min) quick-set button.
 * @param maxIconRes Icon for the right (max) quick-set button.
 * @param onIncrement Called when value crosses an upward step boundary.
 * @param onDecrement Called when value crosses a downward step boundary.
 * @param steps Internal quantization for key events (default 16). Visual is unaffected.
 * @param systemFraction When non-null, the slider animates to this value while the user is not dragging.
 * @param minIconActive When true, the min button renders in an active/highlighted state.
 */
@Composable
fun HorizontalGradientSliderControl(
    @DrawableRes minIconRes: Int,
    @DrawableRes maxIconRes: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 16,
    systemFraction: Float? = null,
    minIconActive: Boolean = false,
) {
    var rawFraction by remember { mutableFloatStateOf(0f) }
    var quantizedStep by remember { mutableIntStateOf(0) }
    var isAdjusting by remember { mutableStateOf(false) }

    LaunchedEffect(systemFraction) {
        if (systemFraction != null && !isAdjusting) {
            val clamped = systemFraction.coerceIn(0f, 1f)
            rawFraction = clamped
            quantizedStep = (clamped * steps).toInt().coerceIn(0, steps)
        }
    }

    val animatedFraction by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = tween(durationMillis = if (isAdjusting) 16 else 100),
        label = "sliderFill",
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isAdjusting) 1f else 0f,
        animationSpec = tween(durationMillis = if (isAdjusting) 60 else 280),
        label = "adjustGlow",
    )

    fun updateFraction(newFraction: Float) {
        val clamped = newFraction.coerceIn(0f, 1f)
        rawFraction = clamped
        val newStep = (clamped * steps).toInt().coerceIn(0, steps)
        if (newStep > quantizedStep) {
            repeat(newStep - quantizedStep) { onIncrement() }
        } else if (newStep < quantizedStep) {
            repeat(quantizedStep - newStep) { onDecrement() }
        }
        quantizedStep = newStep
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Min button — instantly sets level to 0%
        SliderEndButton(iconRes = minIconRes, isActive = minIconActive) { updateFraction(0f) }

        Spacer(Modifier.width(2.dp))

        // Continuous slider track (horizontal)
        Box(
            modifier = Modifier
                .height(46.dp)
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(HudSliderTrack)
                .border(0.5.dp, HudSliderBorder, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        isAdjusting = true
                        // Left = 0.0, Right = 1.0
                        val fraction = offset.x / size.width.toFloat()
                        updateFraction(fraction)
                        isAdjusting = false
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isAdjusting = true
                            val fraction = offset.x / size.width.toFloat()
                            updateFraction(fraction)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val fraction = change.position.x / size.width.toFloat()
                            updateFraction(fraction)
                        },
                        onDragEnd = { isAdjusting = false },
                        onDragCancel = { isAdjusting = false },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cr = 14.dp.toPx()
                val fillW = w * animatedFraction

                // Track depth: subtle inner shadow at left edge
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = w * 0.12f,
                    ),
                    cornerRadius = CornerRadius(cr),
                )

                // Active fill
                if (animatedFraction > 0.002f) {
                    val clip = Path().apply {
                        addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(cr)))
                    }
                    clipPath(clip) {
                        // Main gradient: electric blue (left) → mid blue → cyan (right)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    HudSliderGradientBot,
                                    HudSliderGradientMid,
                                    HudSliderGradientTop,
                                ),
                                startX = 0f,
                                endX = w,
                            ),
                            topLeft = Offset(0f, 0f),
                            size = Size(fillW, h),
                        )

                        // Inner highlight — subtle top-edge depth
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.045f),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = h * 0.4f,
                            ),
                            topLeft = Offset(0f, 0f),
                            size = Size(fillW, h),
                        )

                        // Fill edge glow — soft luminous line at current level
                        val edgeGlowW = 4.dp.toPx().coerceAtMost(fillW)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    HudCyan.copy(alpha = 0.22f),
                                ),
                                startX = fillW - edgeGlowW,
                                endX = fillW,
                            ),
                            topLeft = Offset(fillW - edgeGlowW, 0f),
                            size = Size(edgeGlowW, h),
                        )
                    }
                }

                // Interaction glow pulse
                if (glowAlpha > 0f) {
                    val clip = Path().apply {
                        addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(cr)))
                    }
                    clipPath(clip) {
                        // Radial glow centered at fill level edge
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    HudCyan.copy(alpha = glowAlpha * 0.10f),
                                    Color.Transparent,
                                ),
                                center = Offset(fillW, h / 2f),
                                radius = h * 1.2f,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(2.dp))

        // Max button — instantly sets level to 100%
        SliderEndButton(iconRes = maxIconRes) { updateFraction(1f) }
    }
}

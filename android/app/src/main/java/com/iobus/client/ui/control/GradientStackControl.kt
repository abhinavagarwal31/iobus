package com.iobus.client.ui.control

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * @param label Display label below the slider (e.g. "BRIGHTNESS").
 * @param iconRes Lucide VectorDrawable resource ID drawn above the slider.
 * @param onIncrement Called when value crosses an upward step boundary.
 * @param onDecrement Called when value crosses a downward step boundary.
 * @param steps Internal quantization for key events (default 16). Visual is unaffected.
 */
@Composable
fun GradientSliderControl(
    label: String,
    @DrawableRes iconRes: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 16,
) {
    // Raw fraction 0f..1f — continuous position
    var rawFraction by remember { mutableFloatStateOf(0.5f) }
    // Quantized step for firing discrete key events
    var quantizedStep by remember { mutableIntStateOf(steps / 2) }
    // True while finger is on the slider
    var isAdjusting by remember { mutableStateOf(false) }

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
        // Icon — thin-line, optically centered
        HudIcon(
            iconRes = iconRes,
            tint = HudCyanDim,
            modifier = Modifier.size(20.dp),
        )

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

        // Label — reduced letter spacing
        Text(
            text = label,
            color = HudTextDisabled,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )
    }
}

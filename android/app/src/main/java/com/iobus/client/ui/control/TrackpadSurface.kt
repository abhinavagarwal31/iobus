package com.iobus.client.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iobus.client.input.TouchProcessor
import com.iobus.client.ui.theme.*

/**
 * Full-area trackpad surface — HUD aesthetic.
 *
 * Features:
 * - Deep dark surface with subtle border glow
 * - Faint dot grid overlay for depth
 * - "TRACKPAD" watermark at very low opacity
 * - Gesture recognition: move, tap, right-tap, scroll, drag
 */
@Composable
fun TrackpadSurface(
    touchProcessor: TouchProcessor,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(HudTrackpadGradientTop, HudTrackpadGradientBot),
                )
            )
            .border(0.5.dp, HudTrackpadBorder, shape)
            .drawBehind {
                // Faint grid lines for HUD depth effect
                val gridSpacing = 40.dp.toPx()
                val gridColor = HudTrackpadGrid
                val effect = PathEffect.dashPathEffect(floatArrayOf(2f, 6f), 0f)

                var x = gridSpacing
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 0.5f, pathEffect = effect)
                    x += gridSpacing
                }
                var y = gridSpacing
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 0.5f, pathEffect = effect)
                    y += gridSpacing
                }
            }
            .pointerInput(touchProcessor) {
                detectTrackpadGestures(touchProcessor)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "TRACKPAD",
            color = HudTextMicro,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 6.sp,
        )
    }
}

/**
 * Core gesture detection loop.
 *
 * Uses elapsed time for long-press detection (no coroutine launch needed
 * inside AwaitPointerEventScope). Long press threshold: 400ms.
 */
private suspend fun PointerInputScope.detectTrackpadGestures(tp: TouchProcessor) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        firstDown.consume()

        var pointerCount = 1
        var lastPosition = firstDown.position
        var moved = false
        var longPressed = false
        val movedThreshold = 10f // px
        val longPressThresholdMs = 400L
        val downTime = System.currentTimeMillis()

        // Track gesture
        while (true) {
            val event = awaitPointerEvent()
            val pointers = event.changes.filter { it.pressed }
            if (pointers.isEmpty()) break

            pointerCount = maxOf(pointerCount, pointers.size)
            val current = pointers.first().position

            val dx = current.x - lastPosition.x
            val dy = current.y - lastPosition.y

            if (!moved && (dx * dx + dy * dy) > movedThreshold * movedThreshold) {
                moved = true
            }

            // Check long-press transition
            if (!longPressed && !moved &&
                (System.currentTimeMillis() - downTime) > longPressThresholdMs
            ) {
                longPressed = true
                tp.onDragStart()
            }

            if (moved) {
                when {
                    pointers.size >= 2 -> {
                        // Two-finger → scroll
                        tp.onScroll(-dx, -dy)
                    }
                    else -> {
                        // Single finger → move or drag
                        tp.onMove(dx, dy)
                    }
                }
            }

            lastPosition = current
            pointers.forEach { it.consume() }
        }

        // Release — determine tap vs drag end
        if (longPressed) {
            tp.onDragEnd()
        } else if (!moved) {
            // Tap
            when {
                pointerCount >= 2 -> tp.onSecondaryTap()
                else -> tp.onTap()
            }
        }
    }
}

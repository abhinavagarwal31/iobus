package com.iobus.client.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Shared HUD drawing utilities used across all screens.
 */

/**
 * Draws four L-shaped corner brackets — the signature element of a heads-up display.
 *
 * @param color       Bracket colour (typically HudCyan with low alpha).
 * @param strokeWidth Stroke width in pixels.
 * @param bracketSize Length of each bracket arm in pixels.
 */
fun DrawScope.drawHudCornerBrackets(
    color: Color,
    strokeWidth: Float,
    bracketSize: Float,
) {
    val s = strokeWidth
    // Top-left
    drawLine(color, Offset(0f, bracketSize), Offset(0f, 0f), s)
    drawLine(color, Offset(0f, 0f), Offset(bracketSize, 0f), s)
    // Top-right
    drawLine(color, Offset(size.width - bracketSize, 0f), Offset(size.width, 0f), s)
    drawLine(color, Offset(size.width, 0f), Offset(size.width, bracketSize), s)
    // Bottom-left
    drawLine(color, Offset(0f, size.height - bracketSize), Offset(0f, size.height), s)
    drawLine(color, Offset(0f, size.height), Offset(bracketSize, size.height), s)
    // Bottom-right
    drawLine(color, Offset(size.width - bracketSize, size.height), Offset(size.width, size.height), s)
    drawLine(color, Offset(size.width, size.height - bracketSize), Offset(size.width, size.height), s)
}

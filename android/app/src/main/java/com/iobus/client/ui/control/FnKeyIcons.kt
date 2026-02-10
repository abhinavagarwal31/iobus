package com.iobus.client.ui.control

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom Canvas-drawn icons for function keys — thin-line HUD style.
 *
 * Each function draws its icon centered within the given bounds.
 * All icons use thin strokes (1–1.5px) for a clean, futuristic look.
 */
object FnKeyIcons {

    /** Map of function key label → icon drawing function. */
    val iconMap: Map<String, DrawScope.(color: Color, bounds: Size) -> Unit> = mapOf(
        "F1" to { color, bounds -> drawBrightnessDown(color, bounds) },
        "F2" to { color, bounds -> drawBrightnessUp(color, bounds) },
        "F3" to { color, bounds -> drawMissionControl(color, bounds) },
        "F4" to { color, bounds -> drawSpotlight(color, bounds) },
        "F5" to { color, bounds -> drawDictation(color, bounds) },
        "F6" to { color, bounds -> drawDoNotDisturb(color, bounds) },
        "F7" to { color, bounds -> drawMediaPrev(color, bounds) },
        "F8" to { color, bounds -> drawPlayPause(color, bounds) },
        "F9" to { color, bounds -> drawMediaNext(color, bounds) },
        "F10" to { color, bounds -> drawMute(color, bounds) },
        "F11" to { color, bounds -> drawVolumeDown(color, bounds) },
        "F12" to { color, bounds -> drawVolumeUp(color, bounds) },
    )

    // ── Brightness ──

    private fun DrawScope.drawBrightnessDown(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.55f
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        val stroke = Stroke(width = 1.2f, cap = StrokeCap.Round)

        // Sun circle
        val r = s * 0.22f
        drawCircle(color, radius = r, center = Offset(cx, cy), style = stroke)

        // Rays (8 lines)
        val innerR = s * 0.32f
        val outerR = s * 0.48f
        for (i in 0 until 8) {
            val angle = (i * 45.0) * PI / 180.0
            drawLine(
                color,
                start = Offset(cx + (innerR * cos(angle)).toFloat(), cy + (innerR * sin(angle)).toFloat()),
                end = Offset(cx + (outerR * cos(angle)).toFloat(), cy + (outerR * sin(angle)).toFloat()),
                strokeWidth = 1.2f,
                cap = StrokeCap.Round,
            )
        }

        // Down-arrow under the sun (smaller = dim)
        val arrowY = cy + s * 0.38f
        val arrowW = s * 0.15f
        drawLine(color, Offset(cx - arrowW, arrowY), Offset(cx, arrowY + arrowW * 0.7f), 1.2f, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + arrowW, arrowY), Offset(cx, arrowY + arrowW * 0.7f), 1.2f, cap = StrokeCap.Round)
    }

    private fun DrawScope.drawBrightnessUp(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.55f
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        val stroke = Stroke(width = 1.2f, cap = StrokeCap.Round)

        val r = s * 0.25f
        drawCircle(color, radius = r, center = Offset(cx, cy), style = stroke)

        val innerR = s * 0.35f
        val outerR = s * 0.50f
        for (i in 0 until 8) {
            val angle = (i * 45.0) * PI / 180.0
            drawLine(
                color,
                start = Offset(cx + (innerR * cos(angle)).toFloat(), cy + (innerR * sin(angle)).toFloat()),
                end = Offset(cx + (outerR * cos(angle)).toFloat(), cy + (outerR * sin(angle)).toFloat()),
                strokeWidth = 1.4f,
                cap = StrokeCap.Round,
            )
        }
    }

    // ── Mission Control (three overlapping rectangles) ──

    private fun DrawScope.drawMissionControl(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.55f
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        val stroke = Stroke(width = 1.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Three stacked rectangles
        val rw = s * 0.55f
        val rh = s * 0.28f
        val gap = s * 0.14f

        // Top (small, centered)
        drawRect(color, topLeft = Offset(cx - rw * 0.4f, cy - rh - gap), size = Size(rw * 0.8f, rh * 0.7f), style = stroke)
        // Bottom left
        drawRect(color, topLeft = Offset(cx - rw - s * 0.02f, cy + gap * 0.3f), size = Size(rw * 0.9f, rh), style = stroke)
        // Bottom right
        drawRect(color, topLeft = Offset(cx + s * 0.08f, cy + gap * 0.3f), size = Size(rw * 0.9f, rh), style = stroke)
    }

    // ── Spotlight (magnifying glass) ──

    private fun DrawScope.drawSpotlight(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.50f
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        val stroke = Stroke(width = 1.2f, cap = StrokeCap.Round)

        val r = s * 0.3f
        val centerOffset = Offset(cx - s * 0.06f, cy - s * 0.06f)
        drawCircle(color, radius = r, center = centerOffset, style = stroke)

        // Handle
        val handleAngle = 45.0 * PI / 180.0
        val hStart = Offset(
            centerOffset.x + (r * cos(handleAngle)).toFloat(),
            centerOffset.y + (r * sin(handleAngle)).toFloat(),
        )
        val hEnd = Offset(
            centerOffset.x + (r * 1.7f * cos(handleAngle)).toFloat(),
            centerOffset.y + (r * 1.7f * sin(handleAngle)).toFloat(),
        )
        drawLine(color, hStart, hEnd, 1.6f, cap = StrokeCap.Round)
    }

    // ── Dictation (microphone) ──

    private fun DrawScope.drawDictation(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.50f
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        val stroke = Stroke(width = 1.2f, cap = StrokeCap.Round)

        // Mic body (rounded rect approximated by lines + arc)
        val mw = s * 0.22f
        val mh = s * 0.35f
        val top = cy - s * 0.28f

        // Left side
        drawLine(color, Offset(cx - mw, top + mw), Offset(cx - mw, top + mh), 1.2f, cap = StrokeCap.Round)
        // Right side
        drawLine(color, Offset(cx + mw, top + mw), Offset(cx + mw, top + mh), 1.2f, cap = StrokeCap.Round)
        // Top arc
        drawArc(color, startAngle = 180f, sweepAngle = 180f,
            useCenter = false, topLeft = Offset(cx - mw, top),
            size = Size(mw * 2, mw * 2), style = stroke)
        // Bottom line
        drawLine(color, Offset(cx - mw, top + mh), Offset(cx + mw, top + mh), 1.2f, cap = StrokeCap.Round)

        // Cup arc below mic
        val cupTop = top + mh + s * 0.04f
        val cupW = s * 0.32f
        drawArc(color, startAngle = 0f, sweepAngle = 180f,
            useCenter = false, topLeft = Offset(cx - cupW, cupTop - cupW * 0.5f),
            size = Size(cupW * 2, cupW * 1.2f), style = stroke)

        // Stem + base
        val stemBot = cy + s * 0.42f
        drawLine(color, Offset(cx, cupTop + cupW * 0.1f), Offset(cx, stemBot), 1.2f, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - s * 0.15f, stemBot), Offset(cx + s * 0.15f, stemBot), 1.2f, cap = StrokeCap.Round)
    }

    // ── Do Not Disturb (moon) ──

    private fun DrawScope.drawDoNotDisturb(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.50f
        val cx = bounds.width / 2
        val cy = bounds.height / 2

        // Crescent moon using two overlapping arcs
        val r = s * 0.38f
        val path = Path().apply {
            // Outer arc (full moon)
            val sweep = 300f
            val startA = -150f
            addArc(
                oval = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = startA,
                sweepAngleDegrees = sweep,
            )
            // Inner bite (smaller circle offset to right)
            val biteR = r * 0.75f
            val biteOff = r * 0.4f
            addArc(
                oval = androidx.compose.ui.geometry.Rect(cx + biteOff - biteR, cy - biteR, cx + biteOff + biteR, cy + biteR),
                startAngleDegrees = startA + sweep,
                sweepAngleDegrees = -sweep,
            )
            close()
        }
        drawPath(path, color, style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // ── Media controls ──

    private fun DrawScope.drawMediaPrev(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.45f
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        val stroke = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Bar on left
        drawLine(color, Offset(cx - s * 0.4f, cy - s * 0.35f), Offset(cx - s * 0.4f, cy + s * 0.35f), 1.4f, cap = StrokeCap.Round)

        // Two left-pointing triangles
        val triW = s * 0.35f
        val triH = s * 0.35f
        for (offset in listOf(-0.05f, 0.3f)) {
            val bx = cx + s * offset
            val path = Path().apply {
                moveTo(bx, cy - triH)
                lineTo(bx - triW, cy)
                lineTo(bx, cy + triH)
                close()
            }
            drawPath(path, color, style = stroke)
        }
    }

    private fun DrawScope.drawPlayPause(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.45f
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        val stroke = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Play triangle (left side)
        val triOff = s * 0.12f
        val path = Path().apply {
            moveTo(cx - s * 0.35f, cy - s * 0.35f)
            lineTo(cx + triOff, cy)
            lineTo(cx - s * 0.35f, cy + s * 0.35f)
            close()
        }
        drawPath(path, color, style = stroke)

        // Pause bars (right side)
        val barW = s * 0.08f
        val barH = s * 0.35f
        drawLine(color, Offset(cx + s * 0.22f, cy - barH), Offset(cx + s * 0.22f, cy + barH), 1.4f, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + s * 0.38f, cy - barH), Offset(cx + s * 0.38f, cy + barH), 1.4f, cap = StrokeCap.Round)
    }

    private fun DrawScope.drawMediaNext(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.45f
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        val stroke = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Two right-pointing triangles
        val triW = s * 0.35f
        val triH = s * 0.35f
        for (offset in listOf(-0.3f, 0.05f)) {
            val bx = cx + s * offset
            val path = Path().apply {
                moveTo(bx, cy - triH)
                lineTo(bx + triW, cy)
                lineTo(bx, cy + triH)
                close()
            }
            drawPath(path, color, style = stroke)
        }

        // Bar on right
        drawLine(color, Offset(cx + s * 0.4f, cy - s * 0.35f), Offset(cx + s * 0.4f, cy + s * 0.35f), 1.4f, cap = StrokeCap.Round)
    }

    // ── Volume / Mute ──

    private fun DrawScope.drawSpeakerBase(color: Color, cx: Float, cy: Float, s: Float) {
        val stroke = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Speaker body: small rectangle + cone
        val path = Path().apply {
            // Cone pointing right
            moveTo(cx - s * 0.35f, cy - s * 0.15f)
            lineTo(cx - s * 0.12f, cy - s * 0.3f)
            lineTo(cx - s * 0.12f, cy + s * 0.3f)
            lineTo(cx - s * 0.35f, cy + s * 0.15f)
            close()
        }
        drawPath(path, color, style = stroke)
    }

    private fun DrawScope.drawMute(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.50f
        val cx = bounds.width / 2
        val cy = bounds.height / 2

        drawSpeakerBase(color, cx, cy, s)

        // X mark
        val xOff = s * 0.18f
        val xS = s * 0.18f
        drawLine(color, Offset(cx + xOff - xS, cy - xS), Offset(cx + xOff + xS, cy + xS), 1.4f, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + xOff + xS, cy - xS), Offset(cx + xOff - xS, cy + xS), 1.4f, cap = StrokeCap.Round)
    }

    private fun DrawScope.drawVolumeDown(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.50f
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        val stroke = Stroke(width = 1.0f, cap = StrokeCap.Round)

        drawSpeakerBase(color, cx, cy, s)

        // One sound wave arc
        drawArc(color, startAngle = -35f, sweepAngle = 70f,
            useCenter = false, topLeft = Offset(cx + s * 0.02f, cy - s * 0.25f),
            size = Size(s * 0.3f, s * 0.5f), style = stroke)
    }

    private fun DrawScope.drawVolumeUp(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.50f
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        val stroke = Stroke(width = 1.0f, cap = StrokeCap.Round)

        drawSpeakerBase(color, cx, cy, s)

        // Three sound wave arcs
        for (i in 0..2) {
            val arcOff = s * (0.02f + i * 0.14f)
            val arcSize = s * (0.3f + i * 0.12f)
            drawArc(color, startAngle = -35f, sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(cx + arcOff, cy - arcSize / 2),
                size = Size(arcSize, arcSize),
                style = stroke)
        }
    }

    // ── Fun Key icon (energy pulse / HUD spark) ──

    fun DrawScope.drawFunKeyIcon(color: Color, bounds: Size) {
        val s = min(bounds.width, bounds.height) * 0.55f
        val cx = bounds.width / 2
        val cy = bounds.height / 2

        // Stylized lightning bolt / energy pulse
        val bolt = Path().apply {
            moveTo(cx + s * 0.05f, cy - s * 0.45f)
            lineTo(cx - s * 0.18f, cy - s * 0.02f)
            lineTo(cx + s * 0.02f, cy + s * 0.02f)
            lineTo(cx - s * 0.05f, cy + s * 0.45f)
            lineTo(cx + s * 0.18f, cy + s * 0.02f)
            lineTo(cx - s * 0.02f, cy - s * 0.02f)
            close()
        }
        drawPath(bolt, color, style = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Outer glow ring (partial arc)
        val ringR = s * 0.48f
        drawArc(
            color = color.copy(alpha = 0.4f),
            startAngle = -60f, sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(cx - ringR, cy - ringR),
            size = Size(ringR * 2, ringR * 2),
            style = Stroke(width = 0.8f, cap = StrokeCap.Round),
        )
        drawArc(
            color = color.copy(alpha = 0.4f),
            startAngle = 120f, sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(cx - ringR, cy - ringR),
            size = Size(ringR * 2, ringR * 2),
            style = Stroke(width = 0.8f, cap = StrokeCap.Round),
        )
    }
}
